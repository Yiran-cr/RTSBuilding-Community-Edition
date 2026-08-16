# RTS Building: Community Edition — 架构优化方案

> 编制日期：2026-08-16
> 编制依据：《架构评审报告》（docs/architecture-review.md）P0-P4 问题清单
> 适用版本：main 分支（Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21）
> 方案代号：RTS-ARCH-2026.08

---

## 0. 方案概述

### 0.1 设计理念

围绕四个关键词：**可验证、可演进、单向化、薄平台**。

- **可验证**：一切协议、序列化、集成绑定都必须有自动化护栏，杜绝"靠人肉约束"。
- **可演进**：删除枚举值/类不再引发连锁灾难（协议显式 id、SPI 扩展点）。
- **单向化**：依赖方向严格 `api ← common ← main ← addon`，主模零编译引用附加。
- **薄平台**：common 只承载纯逻辑与数据模型，UI 文案、资源加载、加载器 API 一律不进纯逻辑层。

### 0.2 与现状的关系

不推翻现有"单 JAR 多 mod + 桥钩子（AtomicReference）+ 静态注册表"的好底子——这三样被评审认定为教科书级设计，予以保留。本方案只针对四个结构性缺陷：

1. 协议层无自动化保障（ActionType ordinal 编解码）
2. addon 集成四种手法不一、失败全静默
3. common 模块边界泄漏（UI 文案 / 跨模块资源）
4. 测试覆盖与 CI 基建缺失（6 类测试 vs 6.3 万行，.github 无 workflow）

### 0.3 阶段总览

| 阶段 | 主题 | 预估 | 收益 |
|---|---|---|---|
| 一 | 止血（协议/缺陷/残留） | 1-2 天 | 消除 P0/P1 风险 |
| 二 | Addon 集成统一抽象 | 3-5 天 | 集成长期可维护 |
| 三 | common 边界净化 | 2 天 | 恢复纯逻辑层纯度 |
| 四 | 服务端分层治理 | 3-5 天（持续） | 可测性 |
| 五 | 测试与 CI 基建 | 3 天（可与二并行） | 全仓质量底线 |
| 六 | 打包与文档自动化 | 1-2 天 | 消除双处手改 |

---

## 1. 阶段一 · 止血（1-2 天）

### 1.1 ActionType 显式值化（P1-1 修复）

**现状**：`rtsbuilding-common/.../core/network/ActionType.java` 按 ordinal 编解码，靠 4 个 `@Deprecated` 占位符 + 注释"仅在末尾追加"约束。一次乱序重命名即新旧端协议错位。

**方案**：枚举加显式 `int id`，**保持 `id == 现有 ordinal`（零协议破坏）**，编解码改用 id。

```java
public enum ActionType {
    SET_MODE(0), TOGGLE_CAMERA(1), SET_AUTO_STORE(2), SET_BD_NETWORK(3),
    // ... 全部现有值逐一标注与当前 ordinal 相同的 id ...
    REMOVE_RECENT_ENTRY(40), SET_FUNNEL_RADIUS(41);

    private final int id;

    ActionType(int id) { this.id = id; }

    public int id() { return id; }

    /** 未知 id 返回 null（与现 decode 行为一致，防恶意包 NPE） */
    public static ActionType fromId(int id) {
        return switch (id) {
            case 0 -> SET_MODE;
            // ... 全量映射 ...
            default -> null;
        };
    }
}
```

**改动点**：
- `ActionType.java`：重写为显式 id + `fromId()` switch 映射
- `C2SAction`（main `network/message/`）：编解码改 `actionType.id()` / `ActionType.fromId(rawInt)`
- 新增 `rtsbuilding-common/src/test/java/.../ActionTypeProtocolTest.java`：断言全部 id 唯一、与历史 ordinal 一致、`fromId` 覆盖全值且未知值返 null

**为什么不用 String 注册**：改动面大、协议体积增大，显式 int 即可满足"删除不怕移位"的核心诉求，且迁移零破坏。

### 1.2 RS 集成修复（P0-1）

**现状**：`rtsaddon-refinedstorage/.../RtsRefinedStorageAddon.java:296` 的 `hasPermission` 硬编码 `return false`，导致 insert/extract 永远被拒绝；`mhHasPermission` 已反射绑定却未接线。

**方案**：
- 首选：从 network 对象解析 `SecurityManager` 实例，真正调用 `mhHasPermission` 并接通 INSERT/EXTRACT 两个分支。
- 次选（若暂缓）：在 296 行处加注释声明"只读展示，写操作待权限接线"，并让 UI 对 RS 源明确降级提示，杜绝静默失败。

### 1.3 残留清理（P2-1）

- `rtsbuilding-main/build.gradle`：删除 JMH/SQLite 依赖（第 108-111 行）与 `test{exclude '**/server/benchmark/**'}` 等指向已删除目录的配置
- 空目录 `rtsbuilding-common/.../server/workflow/event/` 删除
- 双份 `RowLayout`（`plugin/binding/RowLayout.java` 与 `plugin/workflow/RowLayout.java`）合并
- `RtsbuildingMod.java:38`、`RtsBindingServiceImpl.java:19` 的 `javax.imageio.spi.ServiceRegistry` 误导性 import 删除
- AE2 反射死代码：`clGridHost`/`clGridNode` 去重、`mhKeyGetDisplayStack` 二次绑定清理

---

## 2. 阶段二 · Addon 集成统一抽象（3-5 天，重构重点）

### 2.1 统一 SPI：`RtsIntegration`

**现状**：AE2/RS/SB 用 MethodHandles 反射、BD 直接编译引用，四种集成四种手法，失败全静默降级，宿主升级无感知。

**方案**：api 模块新增统一集成接口：

```java
// rtsbuilding-api/src/main/java/.../api/compat/RtsIntegration.java
public interface RtsIntegration {
    /** 集成标识（日志 / 诊断用），如 "ae2"、"refinedstorage" */
    String integrationId();

    /** 宿主 mod 是否已加载 */
    boolean available();

    /** 反射绑定自检：返回诊断串，成功返回 null/空串 */
    String selfCheck();

    /** 统一注册入口：向 RtsCompatRegistry 注册 provider / 图标解析器等 */
    void register(RtsCompatRegistry registry);
}
```

**接线方式**（保持静态注册表，不加 ServiceLoader）：

- `RtsCompatRegistry` 增加 `registerIntegration(RtsIntegration)` 与 `integrations()` 访问器
- 每个 rtsaddon 的 `@Mod` 构造时调用 `RtsCompatRegistry.registerIntegration(new RtsXxxIntegration())`
- 主 mod 初始化完成后统一调用 `integration.selfCheck()`，失败打 `LOGGER.warn` 而非静默

### 2.2 反射层自检（`selfCheck`）

每个反射 addon 把反射绑定收敛到单一 `RtsXxxReflection` 类（AE2 已有 `Ae2Reflection`，对齐风格），并实现：

```java
@Override
public String selfCheck() {
    var r = new Ae2Reflection();
    List<String> fails = new ArrayList<>();
    if (r.clGrid == null) fails.add("clGrid");
    if (r.mhGetAvailableStacks == null) fails.add("mhGetAvailableStacks");
    // ... 逐个关键句柄检查 ...
    return fails.isEmpty() ? null : "缺失: " + String.join(", ", fails);
}
```

### 2.3 集成健康状态可视化

利用 `selfCheck()` 结果，在 BuilderScreen 的集成/兼容面板展示每个 addon 状态（已接入/未加载/绑定失败），玩家无需看日志即可判断宿主集成是否生效。

### 2.4 BD 集成策略

BD 保留编译依赖（API 稳定、为唯一有网络回退语义的集成），但做两件事：
- 宿主访问全部收敛到单一 `BdAdapter` 类，接口化导出，便于未来换反射
- 在 `rtsaddon-beyonddimensions` 模块 README/注释中明示"强耦合集成是有意选择，宿主大版本升级需人工适配"

### 2.5 addon gametest

每个 addon 新增 gametest：
- 宿主加载时 `available()` 为真、`selfCheck()` 为 null
- 绑定成功后对空网络执行一次 insert/extract 往返成功
- 挂到 `gameTestServer` run（`rtsbuilding-main/build.gradle` 已有此 run 配置）

---

## 3. 阶段三 · common 边界净化（2 天）

### 3.1 UI 文案移出纯逻辑层

**现状**：`RtsWorkflowStatus.typeLabel()`（`server/workflow/model/RtsWorkflowStatus.java:73`）与 `RtsWorkflowProgressProcessor` 在 common 内直接 `Component.translatable(...)`，而 `RtsHistoryConstants` 注释又强调"服务端不应加载 UI 类"——自相矛盾。

**方案**：
- common 的 `typeLabel()` 改为返回 **lang key 字符串**（如 `"workflow.type.area_mine"`）
- client 层统一 `Component.translatable(key)`；服务端日志需要文案时用 `key` 或 `.getString()` 兜底
- `RtsWorkflowProgressProcessor` 的进度条宽度计算留在 common（纯数学），文案拼接移到 client

### 3.2 跨模块资源解耦

**现状**：`RtsPinyinSearch`（common）硬编码 `/assets/rtsbuilding/pinyin/data.txt`（`util/RtsPinyinSearch.java:13`），资源实体在 main——common 单独发版即静默降级。

**方案**：`RtsPinyinSearch` 改为构造函数注入 `Function<String, InputStream>`（资源打开器）或 `Path` 定位器，main 在装配时传入 `Minecraft.getInstance().getResourceManager()` 对应实现；无字典时明确标记"拼音搜索不可用"而非返回空 Map 静默。

### 3.3 UiProperty 二选一

- 接入：把 `UiStateService.applyAll/collectAll` 的散落调用改写为 `UiProperty.of(...)` 声明式描述符（GLOBAL/SESSION 双作用域清晰化）
- 或删除：若经评估该抽象无实际需求（现零调用点），连同 `UiSnapshot` 的冗余字段一并清理
- 推荐前者（保留可扩展性），但需在接线完成后补齐单元测试

---

## 4. 阶段四 · 服务端分层治理（3-5 天，持续）

### 4.1 `RtsService` 生命周期化

**现状**：`RtsServer` 用 `ServiceLoader.load(RtsService.class)` 发现 10 个 ServiceImpl，依赖顺序靠注释说明（page 先于 session）。

**方案**：
```java
public interface RtsService {
    /** 无参构造（保持 ServiceLoader 契约），构造内只做字段初始化 */
    default void init(RtsServer server) {}
    default void shutdown() {}
}
```
- 依赖声明改为显式：接口增加 `List<Class<? extends RtsService>> dependencies()`，`RtsServer` 按拓扑排序装配
- 服务端停止事件统一调用 `shutdown()`（现散落在各 GameEvents）

### 4.2 工作流引擎状态机化

**现状**：`RtsWorkflowEngine`（579 行）命令式调度，状态流转分散在多个 entry/pipe 中。

**方案**：
- 为 workflow token/entry 定义显式状态机（IDLE → SCHEDULED → RUNNING → PAUSED → COMPLETED/FAILED/CANCELLED）
- 状态转换表收敛到单一 `WorkflowStateMachine`（纯逻辑、可单测），引擎只负责 tick 驱动与资源配额
- 服务端重启恢复路径（`PersistenceService`）复用同一状态机做恢复校验

### 4.3 上帝类拆分

按"数据层 / 输入层 / 渲染层"三明治拆，一次拆一个，每拆必配测试：

| 类（行数） | 拆分方向 |
|---|---|
| `InteractionPanel`(1071) | 按容器类别拆子面板（现有已拆 PageTabBar/TargetProbe，继续拆 Toolbar 区与列表区） |
| `BuildShape`(797) | 每形状独立策略类（`LineShape/LWallShape/...`）+ 共享几何基础，枚举保留为注册/路由 |
| `GridRenderer`(770) | 数据快照渲染与交互反馈分离（纯渲染方法可抽成 `StaticGridPainter`） |
| `BuildInteractionHandler`(758) | 按输入通道（鼠标/键盘/滚轮/快捷）拆分 handler 组合 |

---

## 5. 阶段五 · 测试与 CI 基建（3 天，可与阶段二并行）

### 5.1 测试金字塔补齐

| 层级 | 补什么 | 放哪 |
|---|---|---|
| 单元 | ActionType 协议、C2SAction roundtrip、蓝图 reader roundtrip（NBT/Sponge/Litematic/BG）、UiSnapshot 序列化 | common（新增 src/test）或 main/test |
| 集成 | 工作流引擎（内存 mock Level）、RtsServer 服务拓扑装配、UiStateService 声明式应用 | main/test |
| gametest | addon 绑定自检、mixin 生效、远程放置/挖掘链路、能量网格节点生命周期 | main/src/gameTest（新增源集） |

### 5.2 GitHub Actions 落盘

`.github/workflows/verify.yml`：

```yaml
name: verify
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - name: Gradle build (compile + test)
        run: ./gradlew build --no-daemon --no-configuration-cache
      - name: Docs build
        working-directory: docs/app
        run: npm ci && npm run build
```

（按构建耗时可拆分 `test` 与 `assemble` 两个 job；JDK 21 需 `cache` 开启以复用 Gradle 依赖）

### 5.3 lang 同步校验

CI 脚本（或独立 Gradle task）比对 `zh_cn.json` / `en_us.json` 的 key 集合，漂移即构建失败：

```powershell
# verify-lang.ps1：提取两文件 JSON 顶层 key 集合做差集
```

---

## 6. 阶段六 · 打包与文档自动化（1-2 天）

### 6.1 mod 清单单一来源

**现状**：`neoforge.mods.toml` 模板手写 6 个 `[[mods]]`，`build.gradle` 的 `mods{}`/`jar{from}` 手列 7 个 sourceSet——两处易失同步。

**方案**：
- `gradle.properties` 定义 `builtin_mods = rtsbuilding-technologized,rtsaddon-ae2,...` 单一来源
- `build.gradle` 读取该列表生成 `mods{}` sourceSet 引用与 `jar{from}` 输出
- `neoforge.mods.toml` 模板改为由 Gradle task 从列表生成（或保留模板但加校验 task：清单一致性断言）

### 6.2 产物一致性校验

`verifyAddonPackaging` task：构建后解包主 JAR，断言 6 个 modId 均存在对应入口类、META-INF/services 完整。

---

## 7. 风险与取舍

| 取舍 | 说明 |
|---|---|
| ActionType 用显式 int 而非 String | 改动面小、零迁移破坏；String 协议体积大且引入映射字典，收益不足以覆盖成本 |
| 集成统一用静态注册表而非 ServiceLoader | 与现有机制一致、无需改打包；跨 mod 服务发现本方案无诉求 |
| BD 保留编译依赖 | 唯一有网络回退语义的集成，API 稳定；统一反射的成本 > 收益，以文档化例外处理 |
| `Component.translatable` 在 common 的移除是行为变更 | 需同步改服务端日志调用点，避免回归（配阶段三测试） |
| 上帝类拆分可能引入回归 | 用"每拆必配测试 + 一次只拆一个"约束对冲 |

## 8. 实施顺序建议

```
阶段一（2 天）→ 阶段二（3-5 天）→ 阶段五（并行 3 天）
                    ↓
阶段三（2 天）→ 阶段四（持续）→ 阶段六（1-2 天）
```

里程碑：
- **M1**（阶段一完成）：协议有护栏、RS 缺陷消除、残留清零 —— 可发布 hotfix
- **M2**（阶段二+五完成）：集成统一、CI 全绿、addon 自检可视化 —— 可发布 minor
- **M3**（阶段三/四/六完成）：common 纯净、服务可测、打包自动化 —— 可进入 RC

---

## 附录 · 涉及文件清单

| 文件 | 阶段 |
|---|---|
| `rtsbuilding-common/.../core/network/ActionType.java` | 一 |
| `rtsbuilding-main/.../network/message/C2SAction.java` | 一 |
| `rtsaddon-refinedstorage/.../RtsRefinedStorageAddon.java` | 一 |
| `rtsbuilding-main/build.gradle` | 一 / 六 |
| `rtsbuilding-api/.../api/compat/RtsIntegration.java`（新增） | 二 |
| `rtsbuilding-api/.../api/compat/RtsCompatRegistry.java` | 二 |
| `rtsaddon-*/.../Rts*Addon.java`（4 个） | 二 |
| `rtsbuilding-common/.../server/workflow/model/RtsWorkflowStatus.java` | 三 |
| `rtsbuilding-common/.../util/RtsPinyinSearch.java` | 三 |
| `rtsbuilding-main/.../server/RtsServer.java`、`RtsService.java` | 四 |
| `rtsbuilding-main/.../server/workflow/core/RtsWorkflowEngine.java` | 四 |
| `rtsbuilding-main/.../client/presentation/panel/interaction/InteractionPanel.java` 等 | 四 |
| `.github/workflows/verify.yml`（新增） | 五 |
| `rtsbuilding-main/src/main/templates/META-INF/neoforge.mods.toml` | 六 |

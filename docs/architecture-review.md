# RTS Building: Community Edition — 架构评审报告

> 评审日期：2026-08-16
> 评审范围：仓库 `main` 分支（Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21）
> 评审方式：静态源码走读（全仓 7 个 Gradle 模块、约 6.5 万行 Java）、构建配置核对、文档体系核对
> 报告版本：v1.0（事实核对于 2026-08-16，所有路径引用均已验证）

---

## 0. 执行摘要

RTS Building 是一个"像 RTS 一样从俯视视角建造"的 Minecraft 模组，目前处于 beta 阶段（`mod_version=1.1.4`）。项目在**模块解耦**、**现代 Java 运用**、**防御式编码**三个方向表现出超出多数模组工程的水准，尤其是"一个 JAR 装 6 个 mod"的打包范式和"主模零编译引用附加"的桥接设计，可作模组工程范本。

同时存在三类结构性问题：

1. **协议层缺少自动化保障**：`ActionType` 依赖 ordinal 编解码，仅靠 `@Deprecated` 占位符 + 注释约束，无对应序数稳定性测试，一次乱序重命名就是联机/存档灾难。
2. **宿主 mod 集成参差**：RefinedStorage 集成实际处于"只读"状态（`hasPermission` 恒 `false`），四个 addon 四种手法，反射无 gametest 验证。
3. **测试覆盖与体量严重失衡**：6.3 万行仅 6 个 JUnit 测试类，且全部集中在纯数学/序列化，核心链路（网络、工作流、能量、mixin、addon 桥）零覆盖。

**核心结论**：架构是"设计出来的"，质量是"管理出来的"。本项目的前半句是优等生，后半句明显掉队。

---

## 1. 项目总览

| 维度 | 内容 |
|---|---|
| 定位 | 俯视视角 RTS 式建造模组（规划 / 放置 / 挖掘 / 材料管理） |
| 版本 | 1.1.4（beta），LGPL-3.0-only |
| 技术栈 | NeoForge 21.1.219 / Parchment 2024.11.17 / Java 21 / Gradle（ModDevGradle 2.0.140） |
| 模块数 | 7 个 Gradle 子项目（common / api / main / technologized / addon×4） |
| 代码量 | main ≈ 63,106 行（client 33,044 / server 25,357 / 其余 ~4.7K）；common 26 类；api 28 类；technologized 21 类 |
| 内置 mod 数 | 6 个 modId 共居一 JAR（主 mod + technologized + AE2/RS/BD/SB 四个 addon） |
| 测试 | 6 个 JUnit 5 测试类 + 3 个 `build.gradle` 残留依赖（JMH/SQLite） |
| 文档体系 | `docs/reports/*.json`（链路检查报告，Vue SPA 渲染）+ `docs/change-log/*.md`（每日总结） |
| 分支 | main / NeoForge-RTSBuildin-v2.0 / forge-1.20.1 / forge-1.19.2 / forge-1.12.2 / forge-1.7.10 / fabric-1.21.1 / neoforge-26.1 |

---

## 2. 模块架构

### 2.1 依赖方向（全仓最值得称道的设计）

```
rtsbuilding-api（纯接口，无实现）
      ▲ api() 依赖
rtsbuilding-common（共享游戏逻辑，依赖 Minecraft 但不依赖 NeoForge 加载器 API）
      ▲ compileOnly / sourceSet 合并
rtsbuilding-main（NeoForge 平台层：注册 / 服务端 / 客户端 / 网络 / mixin）
      ▲ compileOnly
rtsbuilding-technologized（能量附加：compileOnly api+common+main）
rtsaddon-ae2 / refinedstorage / beyonddimensions / sophisticatedbackpacks（宿主集成，compileOnly api）
```

关键约束（均有构建配置背书）：

- `rtsbuilding-main/build.gradle` 对 common/api 仅 `compileOnly`（运行时靠 `mods{sourceSet}` 合并 + `jar{from}` 打包），**main 绝不编译引用任何 addon 模块**。
- common 依赖 api（`api project(':rtsbuilding-api')`），方向单向，无循环。
- technologized 反向依赖 main 的桥接类（`RtsBuildEnergy`/`RtsTerminalEnergy`/`RtsAPIImpl`），即"主模挖洞、附加填洞"。
- 包名分区技巧规避循环：`BuilderMode` 放在 api 模块却保留 `common.build` 包名（注释明确说明原因）。

### 2.2 各模块职责与体量

| 模块 | 包/职责 | 关键类（节选） |
|---|---|---|
| rtsbuilding-api | 10 个子 API（placement/mining/interaction/fluid/blueprint/binding/transfer/session/storage/energy）+ `RtsCompatRegistry`（4 类 provider）+ `ProtectionRegistry` + 仿 Mekanism 能量 API（`Action`/`AutomationType`/`IEnergyContainer`） | `RtsAPI`、`RtsCompatRegistry`、`ProtectionRegistry` |
| rtsbuilding-common | 蓝图格式解析（原版 NBT/Sponge/Litematica/BuildingGadgets）、蓝图模型（record）、蓝图旋转、软替换规则、能量容器默认实现、Ultimine BFS 收集器、工作流数据模型、拼音搜索、UI 状态持久化抽象 | `RtsBlueprint`、`BlueprintReaders`/`BlueprintWriters`、`RtsUltimineCollector`、`ActionType`、`RtsWorkflowStatus` |
| rtsbuilding-main | 注册层（方块/物品/实体/创造页）、客户端全屏 UI 体系、形状画笔、世界渲染 pass 管线、mixin（11 个）、服务端 20+ 服务、工作流引擎、网络（C2S 统一通道 + 多域 S2C）、宿主兼容 | `RtsbuildingMod`、`RtsServer`、`BuilderScreen`、`BuildShape`、`RtsWorkflowEngine`、`RtsCameraManager`、`ServerActionHandler` |
| rtsbuilding-technologized | 玩家级能量网格（按 owner 聚合，非空间网格）、储能单元/热能发电机、终端耗电、放置计费、config 可整体关闭 | `RtsEnergyNetworkManager`、`RtsEnergyCostService`、`RtsTerminalEnergyImpl` |
| rtsaddon-ae2 | MethodHandles 反射绑定 appeng API，注册存储/流体/图标 3 个 provider | `RtsAe2Addon`、`Ae2NetworkItemHandler` |
| rtsaddon-refinedstorage | 反射绑定 RS2 API，注册存储 provider（**当前只读，见问题 P0-1**） | `RtsRefinedStorageAddon` |
| rtsaddon-beyonddimensions | **直接编译引用**宿主 mod（唯一非反射），注册存储/流体 provider | `RtsBeyondDimensionsAddon` |
| rtsaddon-sophisticatedbackpacks | 反射绑定背包 API，注册背包 provider，全部 `Optional` 降级 | `RtsSophisticatedBackpacksAddon` |

---

## 3. 关键架构机制

### 3.1 "单 JAR 多 mod"打包范式

`rtsbuilding-main/build.gradle:189` 的 `jar` 任务用 `from project(':xxx').sourceSets.main.output` 把 7 个子项目的 class 全部并入主 JAR；`neoforge.mods.toml` 模板声明 6 个 `[[mods]]`（rtsbuilding、rtsbuilding_technologized、rtsbuilding_addon_ae2/refinedstorage/beyonddimensions/sophisticatedbackpacks），各自带独立 `@Mod` 注解由 FML 在同 JAR 内实例化。

依赖声明用 `[[dependencies]]` 控制顺序：addon 对主 mod `required`（保证主 mod 先构造）、对宿主 mod `optional`。这解决了 NeoForge 多项目 mod 的加载顺序问题，无需额外产物管理。

**评审**：这是本项目最漂亮的工程决策之一。代价是 `neoforge.mods.toml` 与 `build.gradle` 中的 mod 清单必须人工保持同步（新增 addon 时两处都要改）。

### 3.2 主模/附加解耦的三板斧

1. **`AtomicReference` 桥钩子**：`RtsBuildEnergy`（放置计费）、`RtsTerminalEnergy`（终端耗电）定义在主 mod 的 `common` 包，由 technologized 在 `commonSetup` 注入；主模调用点 `get()` 判空，无附加时零开销 no-op。
2. **API 单例注入**：`RtsAPIImpl.setEnergyApi()`（`volatile` 字段）由 technologized 注入。
3. **静态注册表**：`RtsCompatRegistry`（api 模块）——addon 构造时 `register()`，主模 8+ 个消费点遍历取用。

整个体系没有 `ServiceLoader`/反射做跨 mod 服务发现（ServiceLoader 仅用于 main 内部 `RtsService` 发现 10 个 ServiceImpl）。

### 3.3 网络协议

- **上行**：单一 `C2SAction(actionType, CompoundTag)` 通道，`ActionType` 按 ordinal 编解码（见问题 P1-1），约 35 个分支由 `ServerActionHandler` 大 switch 分发，全部 `enqueueWork`。含模式门禁（`isBuildMode`/`isFunnelAllowedMode`）与 `safeFace()` 越界回退。
- **下行**：按域拆分的 S2C payload（blueprint/builder/camera/feedback/resume/storage），全部 record + StreamCodec。
- **高频专用**：相机姿态走独立 `C2SCameraPosePayload`（纯字段编解码，不走 NBT），服务端据此做权威范围校验。
- **防专用服 ClassNotFound**：`ClientPayloadDispatcher` 用 `IS_CLIENT` 静态守卫 + Java 21 模式匹配。

### 3.4 服务端运行时

- `RtsServer` 用 `ServiceLoader.load(RtsService.class)` 发现 10 个 `*ServiceImpl`（META-INF/services 列出），构造注意 page 先于 session 的顺序依赖。
- 工作流引擎 `RtsWorkflowEngine`：玩家×维度槽位管理、Token 消费者 API、`dirtyPlayers` 合并发包（`flushDirty` 按 tick 批量）、服务重启蓝图恢复。
- 全局 tick 由 `ServerTickOrchestrator` 统一调度（存储缓存刷新、页面推送、挖掘状态机、放置恢复、tickable 管道、背包签名轮询）。
- 放置/挖掘走管道模式（`ActivePipeline`/`PipelinePipe`/`TickablePipe`，sealed `PipelineResult`）。

### 3.5 客户端运行时

- 内核-模块架构：`RtsClientKernel` + 7 个 `FeatureModule`（camera/storage/building/mining/workflow/remote/pathfinding），32 槽 `StateEvent` 重放缓冲。
- 输入：`InputPipeline` + `InputLayer` + `RtsClientInputGate`，RTS 模式下 mixin 完全接管键盘/鼠标（`KeyboardInputMixin`/`MouseInputMixin` 阻断 F3/F11/侧键）。
- 渲染：`RenderPipeline` + 16 个 `RenderPass`（Box/Line/Linked/Boundary/Ultimine/Place/Break…），SDF 着色器 9 个。
- 形状画笔：`LineBrushSelector` 通用状态机（不感知具体形状）+ `BuildShape` 枚举内联几何/阶段/调整/提示（797 行，见问题 P3-1）。

### 3.6 mixin 清单（11 个）

| Mixin | 目标 | 用途 |
|---|---|---|
| `KeyboardInputMixin` | KeyboardHandler | RTS 模式接管键盘 |
| `MouseInputMixin` | MouseHandler | 阻断侧键穿透 |
| `MinecraftSetScreenMixin`/`MinecraftTickMixin` | Minecraft | 屏幕切换/每 tick 挂接 |
| `ScreenRenderBgMixin` | Screen | RTS UI 背景 |
| `ClientLevelMixin` | ClientLevel | 破坏粒子增强 |
| `ClientPacketListenerMixin` | ClientPacketListener | 等级事件/方块更新拦截 |
| `LocalPlayerMixin`/`LocalPlayerStepAiMixin` | LocalPlayer | 相机控制/aiStep/容器关闭 |
| `ChestMenuMixin` | ChestMenu | 远程容器 stillValid |
| `ModdedRemoteStillValidMixin` | Iron Furnaces/Generator Galore/Sophisticated Storage | `@Pseudo` + 类名匹配兼容第三方容器 |

---

## 4. 数据流走查（以"开启 RTS 模式"为例）

```
客户端按下终端 → RtsClientPacketGateway.sendToggleCamera → C2SAction(TOGGLE_CAMERA)
  → ServerActionHandler.handle (enqueueWork)
  → RtsTerminalEnergy.Provider 判空扣费（无 technologized 则跳过）
  → 记录终端 UUID、点亮 lit、创建 RtsCameraEntity
  → 回 S2CRtsCameraStatePayload
客户端 handleCameraState → CameraModule 应用状态
  → kernel.dispatch(RtsToggled) → 打开 BuilderScreen + 设置区域锚点
```

典型模式：**客户端 UI 状态服务端权威校验 → S2C 回包驱动客户端模块**，放置/挖掘/交互均遵循此范式。防御点成体系（越界 decode 返 null、快照节流、handler 生命周期释放）。

---

## 5. 问题清单（按严重度分级）

### P0 — 功能性缺陷

**P0-1：RefinedStorage 集成实际为"只读"，写操作静默失败**
`rtsaddon-refinedstorage/.../RtsRefinedStorageAddon.java`：

- `hasPermission()` 内层调用 `ref.hasPermission(network, actionFlag)`，而 `RsReflection.hasPermission(Object, int)`（**第 296-298 行）硬编码 `return false;`**。
- 后果：`insertItem`（第 130 行 `if (!hasPermission(1)) return stack;`）与 `extractItem`（第 145 行 `if (!hasPermission(2)) return ItemStack.EMPTY;`）**永远被拒绝**。
- 讽刺的是 `mhHasPermission` 方法句柄已在第 236 行反射绑定好，却从未被接线调用。
- 全代码无任何注释说明这是"有意停用"——大概率是 SecurityManager 实例获取未完成的半成品。
- **修复建议**：从网络对象解析 SecurityManager 实例并真正调用 `mhHasPermission`；或若暂缓，至少加注释声明"只读展示，写操作待权限接线"，并让 `getReportedCount` 之外的功能明确降级提示。

### P1 — 架构级风险

**P1-1：协议稳定性靠人肉约束，无自动化护栏**
`rtsbuilding-common/core/network/ActionType.java`：按 ordinal 编解码，4 个 `@Deprecated` 占位符 + "仅在末尾追加"注释。`BlueprintFormatTest` 已做序数稳定性测试，但**同样脆弱的 `ActionType` 没有任何对应测试**。一次乱序重命名 = 新旧端混连协议错位（注释自己举的例子：旧端序号 25 被解析为 PATHFIND）。
**修复建议**：仿 `BlueprintFormatTest` 写 `ActionTypeOrderTest`（断言各 ordinal 值不变），纳入 CI。

**P1-2：反射集成的长期维护债无验证手段**
AE2/RS/SB 三处 MethodHandles 硬编码类名+方法签名（如 `com.refinedstorage2.api.*`、appeng `IGridNode` 等），宿主 mod 大版本升级即静默失效（返回 null/空列表），且**无任何 gametest 验证反射绑定是否仍有效**。BD 又直接 `import` 宿主（`maven.modrinth:beyonddimensions`），宿主改 API 直接编译炸——四种集成四种策略，缺统一抽象。

**P1-3：common 模块边界泄漏（UI 进纯逻辑层）**
- `RtsWorkflowStatus.typeLabel()`（common，`RtsWorkflowStatus.java:73`）与 `RtsWorkflowProgressProcessor` 直接 `Component.translatable(...)`。而 `RtsHistoryConstants` 的注释反而强调"服务端不应加载 UI 类"——自相矛盾。
- `RtsPinyinSearch`（common）读取 `/assets/rtsbuilding/pinyin/data.txt`（`RtsPinyinSearch.java:13`），该资源在 **main 模块**——依赖 sourceSet 合并的运行时约定，common 单独发版即静默降级（loadDictionary 失败返回空 Map）。

### P2 — 工程结构问题

**P2-1：死代码/残留**
- `UiProperty`（common）：`applyToSnapshot`/`collectAll` 被 `UiStateService` 调用，但 `UiProperty.of(...)` **全仓零调用点**——属性描述符机制"定义完成、待接线"。
- `server/workflow/event/` 空目录（共 2 个空子目录）。
- `rtsbuilding-main/build.gradle:108-111`：JMH/SQLite 依赖 + `test{exclude '**/server/benchmark/**'}` 指向**已删除的目录**（benchmark/history 不存在）。
- 双份 `RowLayout`：`plugin/binding/RowLayout.java`（34 行类）与 `plugin/workflow/RowLayout.java`（12 行 record）并存（AGENTS.md 已列为待迁移项）。
- `javax.imageio.spi.ServiceRegistry` 的 import 仅被 javadoc `{@link}` 引用（`RtsbuildingMod.java:38`、`RtsBindingServiceImpl.java:19`），误导性残留。
- AE2 反射轻微死代码：`clGridHost` 与 `clGridNode` 指向同一类、`mhKeyGetDisplayStack` 二次绑定覆盖。

**P2-2：上帝类**
`InteractionPanel`（1071 行）、`GridRenderer`（770 行）、`BuildInteractionHandler`（758 行）、`BuildShape`（797 行）、`RtsCameraManager`（598 行）。虽已拆出部分协作者，体量仍偏大，与全仓 64 个 record 的精致形成反差。

**P2-3：测试覆盖与体量失衡**
6 个测试类全部为纯逻辑：形状几何、环形缓冲、轮廓提取、格式识别、PipelineResult、计数工具。**网络编解码、工作流引擎、能量网格、addon 桥、mixin 全部零覆盖**。`test` 任务 exclude 指向不存在目录，说明曾有基准/历史测试后被删除，删代码未删配置。

### P3 — 代码质量/待迁移

- **P3-1**：`BuildShape.hint()` 交互提示硬编码中文（含 `LineBrushSelector` 的 `replace("建造","破坏")` 文案替换）；`BindingRenderer`/`RowLayout` 绑定按钮文字、`RenderingSection` 颜色标签均未迁移 lang（AGENTS.md 自知，列为后续项）。
- **P3-2**：`@EventBusSubscriber` 默认 GAME 总线却监听 `RegisterClientExtensionsEvent` 等 MOD 生命周期事件（technologized 与 main 的 `RtsClientBootstrap` 同模式），未显式 `bus = Bus.MOD`，依赖 NeoForge 对事件类型的特殊处理，属隐性约定。
- **P3-3**：中英注释混用（model 包英文 / io、transform 包中文），与"统一简体中文注释"约定有出入。
- **P3-4**：`BuildingGadgetsTemplateReader` 的魔法字节掩码（`B1/B2/B3_BYTE_MASK`、`legacyPos/legacyStateId`）无独立注释说明编码布局，依赖 BG 格式领域知识。

### P4 — 工程治理

- **P4-1**：lang 双文件同步——实测 `zh_cn.json` 与 `en_us.json` **均为 218 行**（已同步，此点纠正早前误判），但 key 数量同步仍需人工保证，无自动化校验。
- **P4-2**：仓库持有 8 个远程分支（含 forge-1.7.10 / 1.12.2 / fabric-1.21.1 / neoforge-26.1），多版本同步是持续隐性成本；`Platform.java` 注释标明是跨加载器移植点，但 fabric 分支长期滞后。
- **P4-3**：`.github/` 目录存在但未发现 workflow 文件（本次 glob 未命中任何文件），CI 未落盘。

---

## 6. 优点清单（防止"只批不改"）

1. **模块分层与依赖方向**：api→common→main→addon 单向依赖，编译期强隔离 + 运行时单 classloader，是 NeoForge 多模块 mod 的务实范式。
2. **"一 JAR 多 mod"**：`jar{from}` + 单 TOML 6 modId + `[[dependencies]]` 控序，打包与加载问题一次解决。
3. **解耦三板斧**（AtomicReference 桥 / API 单例注入 / 静态注册表）：主模零编译引用附加，附加可整体缺席，符合"核心可用、附加可选"。
4. **防御式编码成体系**：NBT 解压炸弹防护（`NbtAccounter.create(128MB)`）、C2S 越界 decode 返 null、`safeFace()` 回退、反射失败静默降级、快照节流、`releaseItemHandler` 生命周期契约。
5. **性能意识贯穿**：`inputStamp()` 形状几何缓存、`dirtyPlayers` 合并发包、GhostRingBuffer 容量管理、登录/启动预热（会话序列化、页面构建、BuilderScreen 类加载）。
6. **现代 Java 充分**：64 处 record、sealed 接口（`PipelineResult`）、模式匹配 switch、Java 21 特性。
7. **注释质量高**：含"关键设计决策"章节（如 `RtsWorkflowEngine` Token 消费者 API、`LineBrushSelector` 缓存缓存理由、`BuilderMode` 放 api 的理由），对维护者友好。
8. **仿 Mekanism 能量 API 语义完整**：`Action.SIMULATE/EXECUTE` + `AutomationType` + NBT 序列化全在接口 default 方法，实现只需给最小集合。

---

## 7. 改进路线图（按性价比排序）

| 优先级 | 动作 | 工作量 | 收益 |
|---|---|---|---|
| 1 | 写 `ActionTypeOrderTest` 断言 ordinal 稳定性 | 1-2h | 防止协议错位灾难 |
| 2 | 修 RS `hasPermission` 或注释声明只读意图 | 1h | 消除静默失败 |
| 3 | 清理 `build.gradle` JMH/SQLite 残留 + 空目录 | 0.5h | 消除误导 |
| 4 | 删 `UiProperty` 或接线 | 1h | 去死代码 |
| 5 | 为反射 addon 加 gametest 冒烟（绑定 + 一次读写） | 1-2d | 宿主升级可感知 |
| 6 | 统一四个 addon 的集成抽象（SPI 化） | 3-5d | 长期维护成本陡降 |
| 7 | common 的 UI 文案/资源外移（`typeLabel` 移到 client 或传参） | 0.5d | 恢复纯逻辑层纯度 |
| 8 | 上帝类拆分（先 `InteractionPanel` → 子面板组件化） | 持续 | 可测试性 |
| 9 | lang key 自动化同步校验（CI 脚本比对两文件 key 集合） | 2h | 防漂移 |
| 10 | 网络编解码 / 工作流引擎补核心单测 | 3-5d | 覆盖最关键风险区 |

---

## 8. 总结

这份架构的**分层思路与解耦手法可以当作模组工程的教科书案例**——特别是"单 JAR 多 mod + 单向依赖 + 桥钩子注入"的组合，解决了 MC modding 中最常见的加载顺序和循环依赖泥潭。但它在**交付质量**上掉队：协议层缺自动化保障、一个附加集成处于半成品状态、6 万行只有 6 个测试、死代码与残留配置较多。

一句话：**架构是"设计出来的"，质量是"管理出来的"——这个项目的前半句是优等生，后半句明显掉队。** 好在问题全部可枚举、可修复，且 P0/P1 的修复成本都很低（两天内可清完风险项）。

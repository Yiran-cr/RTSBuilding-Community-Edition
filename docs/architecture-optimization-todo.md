# 架构优化 · 任务跟踪记忆（临时）

> 来源：`docs/architecture-optimization.md`（方案代号 RTS-ARCH-2026.08）
> 用途：当前主要任务为**修复架构缺陷**。每解决一步，在此更新状态并标注日期确认。
> 维护规则：`[x] 已完成` / `[ ] 待办` / `[~] 进行中`，完成后追加 `（完成于 YYYY-MM-DD）`。

## 阶段一 · 止血（1-2 天）— P0/P1 风险

- [x] **1.1 ActionType 显式值化**：枚举加 `int id`（保持 id==现有 ordinal），C2SAction 改按 id 编解码，新增 `ActionTypeProtocolTest`（完成于 2026-08-16，7 测试全通过）
- [x] **1.1b 补充同类协议枚举显式 id（复查补漏）**：核实阶段一遗漏——`BuilderMode`（api，网络 SET_MODE）、`RtsStorageSort`（main，网络+存档 NBT）、`RtsWorkflowType`（common，工作流进度）、`RtsWorkflowPriority`（rank 编解码端不对称）均改用显式 id/fromRank 对称编解码，新增 `ProtocolEnumTest`（12 测试全通过）。修正 `SessionSerializer`/`RtsPageCore`/`RtsPagePayloadFactory`/`ServerActionHandler`/`StorageState`/`WorkflowModule` 等调用点。并补清 2 处遗漏的 `ServiceRegistry` javadoc 残留（`RtsBlueprintServiceImpl`/`BlueprintTickPipe`）（完成于 2026-08-16，全仓 `build` 通过）
- [x] **1.2 RS 集成重写**：发现原 addon 绑定 `com.refinedstorage2.api.*` 旧包名在 RS 2.0.9（MC 1.21.1）中已不存在（集成实际失效）；对照下载的 2.0.9 jar 重写为 `com.refinedmods.refinedstorage.api` 组件式架构（NetworkNodeContainerProvider→NetworkNode→Network→StorageNetworkComponent，insert/extract 用 Actor.EMPTY 无权限门控），hasPermission 只读问题随新 API 消失；15 个绑定类经 javap 逐一对齐（完成于 2026-08-16）
- [x] **1.3 残留清理**：删 build.gradle JMH/SQLite 依赖与 test exclude（指向已删除目录）、删空目录 `server/workflow/event/`、删 `ServiceRegistry` 误导 import（2 处，javadoc 改纯文本）、清 AE2 反射死代码（clGridHost 重复绑定、mhKeyGetDisplayStack 首次绑定被覆盖）。双份 RowLayout 经评估为不同用途同名类（record 布局记录 vs 可变计算器），非重复实现，保留不合并（完成于 2026-08-16）。复查补漏 2 处 ServiceRegistry javadoc 残留见 1.1b

## 阶段二 · Addon 集成统一抽象（3-5 天，重构重点）

- [x] **2.1 `RtsIntegration` SPI**：api 新增 `RtsIntegration` 接口（integrationId/available/selfCheck/register）+ `RtsCompatRegistry.registerIntegration()/getIntegrations()`（完成于 2026-08-16）
- [x] **2.2 反射自检 `selfCheck()`**：AE2/RS/SB 三个反射 addon 实现 selfCheck（检查关键句柄 null），BD 编译期绑定返回 null；4 个 addon 主类均改 `implements RtsIntegration` 并走统一注册入口；`RtsServer.init()` 增加 `checkIntegrations()` 统一健康检查，失败打 WARN（完成于 2026-08-16，复查修正：反射失败时也注册 integration 使诊断可见）
- [x] **2.3 集成健康状态可视化（复查修正）**：`IntegrationSection` 状态列改动态宽度（按集成名+gap，防超长名重叠）、删未用常量/import、无宿主时显示提示并迁移 lang key；AE2/RS/SB 反射失败时**仍注册 integration**（带 `loadError` 诊断），使"宿主存在但绑定失败"在设置面板可见而非静默消失；lang 双文件 key 集合核验一致（完成于 2026-08-16）
- [x] **2.4 BD 集成收敛**：新增 `BdAdapter` 作为 BD 宿主 API 唯一访问入口（DimensionsNet/UnifiedStorage 入口、isNetMember/displayName/fluidHandler/bucket 操作），主类与 `BdDirectItemHandler` 改用 adapter，清理 4 个宿主 import；类注释与优化方案文档均明示强耦合例外（完成于 2026-08-16）
- [x] **2.5 addon gametest（调整）**：评估 dev/CI 无宿主 mod（AE2/RS/SB/BD 未 localRuntime），真实 insert/extract 往返仅能在装有宿主的实机验证，gametest 无执行环境。落地为：`RtsCompatRegistryTest`（SPI 注册/遍历/selfCheck 契约，3 测试过）+ 运行时 `RtsServer.checkIntegrations` selfCheck + IntegrationSection 可视化 + 阶段一 1.2 的 javap 逐类核对。宿主往返留实机验证（完成于 2026-08-16）

## 阶段三 · common 边界净化（2 天）

- [x] **3.1 UI 文案移出**：`RtsWorkflowStatus.typeLabel()` 改为 `typeLabelKey()` 返回 lang key（common 零依赖 `Component`，仅 javadoc 提及）；`RtsWorkflowProgressProcessor` 拆纯逻辑（typeLabelKey/formatProgressText/computeFillWidth），`formatLabel` 的 UI 拼接移入 client `WorkflowRenderer.buildLabel`（完成于 2026-08-16）
- [x] **3.2 跨模块资源解耦（复查修正）**：`RtsPinyinSearch` 增加 `setDictionarySource(Supplier<InputStream>)` 注入 + `needsReload` 懒重载；main 在 `onServerStarting` 用 `ResourceManager.getResource().open()` 注入真实资源源。**复查发现并修复降级缺陷**：新来源加载为空时原实现清空字典 → 改为防御性保留原字典（避免拼音搜索静默失效）；测试语义同步修正。新增 `RtsPinyinSearchTest`（3 测试过）（完成于 2026-08-16）
- [x] **3.3 UiProperty 删除**：核实整个 UI 状态持久化子系统（UiStateRepository/UiSnapshot/UiProperty/UiStateService/JsonFileRepository）**零调用点**（全仓 grep 证实），按方案"删除"分支删除 5 文件 + 空 persist 目录（完成于 2026-08-16）

## 阶段四 · 服务端分层治理（3-5 天，持续）

- [x] **4.1 `RtsService` 生命周期化**：接口加 `init(RtsServer)`/`shutdown()`/`dependencies()` default 方法；新增 `ServiceTopoSorter`（Kahn 入度法，依赖缺失/成环安全降级）；`RtsServer` 构造后按拓扑 init、新增 `shutdown()`（逆序）；`onServerStopped` 接入；`RtsSessionServiceImpl` 声明依赖 page 示范。**复查修正**：初版 DFS 算法有 resolving 状态 bug（依赖服务被误跳）→ 重写为 Kahn。新增 `ServiceTopoSorterTest`（5 测试过）（完成于 2026-08-16）
- [x] **4.2 工作流引擎状态机化**：新增 `WorkflowState` 枚举（IDLE/RUNNING/PAUSED/SUSPENDED/COMPLETED/FAILED）+ `WorkflowStateMachine`（common 纯逻辑：canTransition 白名单/fromFlags 推导/toHoldType 映射）；`RtsWorkflowEntry` 加 `state()`/`transition()`（校验合法转换并保持布尔字段同步，NBT 存档兼容）；token/engine 的 setPaused/setSuspended 全部改走 transition（grep 确认无残留）。新增 `WorkflowStateMachineTest`（6 测试过）（完成于 2026-08-16）
- [x] **4.3 上帝类拆分（BuildShape）**：抽 21 个纯几何方法到 `ShapeGeometry`（独立静态工具，上限常量沿用 BuildShape.MAX_*），6 个 compute 委托；BuildShape 797→378 行（-52%）；删除失效 import、修 BOM/闭合。BuildShapeFillModeTest 12 测试全过确认行为不变（完成于 2026-08-16）。其余上帝类（InteractionPanel/GridRenderer/BuildInteractionHandler）标为后续持续项

## 阶段五 · 测试与 CI 基建（3 天）

- [x] **5.1 测试金字塔补齐（调整）**：新增 `RtsWorkflowStatusTest`（8 测试过：fromRaw 派生计算/进度钳制/完成判定/holdType 语义/typeLabelKey/null 防御）。网络 roundtrip 因依赖完整 Minecraft 运行时（RegistryFriendlyByteBuf），由协议枚举测试（fromId/id roundtrip）+ 实机验证覆盖（与 2.5 同策略）（完成于 2026-08-16）
- [x] **5.2 GitHub Actions 落盘**：新增 `.github/workflows/verify.yml`（JDK21 + `./gradlew build --no-daemon --no-configuration-cache` + docs SPA `npm ci && npm run build` + lang 校验，main 与 v2.0 分支 + PR 触发，60min 超时）（完成于 2026-08-16）
- [x] **5.3 lang 同步校验**：新增 `scripts/verify-lang.ps1`（比对 zh_cn/en_us 顶层 key 集合，JSON 非法/缺失/不一致均非零退出），本机验证通过与失败两路径（完成于 2026-08-16）

## 阶段六 · 打包与文档自动化（1-2 天）

- [x] **6.1 mod 清单单一来源**：gradle.properties 新增 `builtin_mods`（5 个内置 addon 子项目列表）；`rtsbuilding-main/build.gradle` 读取该列表生成 `mods{}` sourceSet 引用与 `jar{}` 合并（定义前置避免配置时序问题）；硬编码双处手改消除（完成于 2026-08-16）
- [x] **6.2 产物一致性校验**：新增 `addonManifest`（子项目→modId→@Mod 入口类映射）+ `verifyAddonPackaging` task（解包主 JAR，校验每个内置 addon 入口类已合入 + neoforge.mods.toml 声明 modId + builtin_mods 未登记检测），挂到 `check`；通过/失败两路径本机验证（失败路径正确报"入口类未合入"）（完成于 2026-08-16）

## 全项目扫描（收尾）

- [x] **扫描确认**（2026-08-16）：① ordinal 协议残留 0；② common 模块引用 main 专属类 0（边界干净）；③ 记忆清单 20 项全 `[x]`；④ 全仓 build 通过、112 测试 0 失败；⑤ 删 6 个空目录（blueprint/plugin/progression 模块、common/block、common/block.entity、server/service/crafting）；⑥ IntegrationSection 硬编码「宿主未加载」迁移 lang key（新增 `ui.rtsbuilding.integration.unavailable`）。
- [ ] **独立待办（非六阶段范围）**：~~`RenderingSection`（设置面板颜色标签+tooltip）~~、~~`BindingRenderer`/`RowLayout`（解绑/双向/仅提取/开启位置/关闭显示）~~、~~`BuildShape.hint` 交互提示~~、~~`LineBrushSelector` 文案替换~~ —— **lang 迁移专项已完成**（见下方）

## Lang 迁移专项（2026-08-16 收尾）

- [x] **RenderingSection**：23 个 key（颜色标签 11 + tooltip 11 + 组名 1），新增静态 `tStatic()`（避免字段初始化顺序 NPE）；`ColorGroup`/`ColorSlot` 名称与 11 处 tooltip 全部迁移（hardcode 清零）
- [x] **BindingRenderer/RowLayout**：5 个 key（解绑/双向/仅提取/开启位置/关闭显示），新增 `tr()` helper；ButtonBar 宽度计算同步用翻译文本
- [x] **BuildShape.hint/LineBrushSelector**：10 个 hint key（line/wall/face/solid/cylinder/sphere，含 %d 参数），lang 值用 `{action}`/`{button}` 占位符；`currentHint` 由 replace("建造","破坏") 改为 token 替换（建造/破坏、右键/左键由 lang 决定）
- [x] **验证**：全仓 UI 硬编码复扫清零（仅剩异常消息/日志/注释，符合 AGENTS.md 约定）；lang 229=229 一致；全仓 build 通过、112 测试 0 失败

## UI 前置模组提取（2026-08-16 专项）

- [x] **新增 `rtsbuilding-ui` 独立前置模组**（modId `rtsbuilding_ui`，基包 `com.rtsbuilding.rtsui`）：57 Java 类（render/animate/theme/state/window/component/color）+ 27 shader + 11 纹理（命名空间 `rtsbuilding_ui`）+ `RtsUiMod` 入口（9 shader 自注册）。
- [x] **解耦 3 点**：① `BuilderScreen`→`RtsPanelHost` 接口；② `FeatureAdjusterState` 网络调用→`setFunnelRadiusSync` 回调注入；③ `TopBarLayoutHelper.ButtonGroup` 实现 `ButtonGroupLayout`。
- [x] **主模块**：删 53 旧 UI 文件，58 文件 import 全量更新，BuilderScreen 实现 RtsPanelHost，5 面板 screen 字段改 RtsPanelHost，toml 声明 rtsbuilding_ui + 主 mod required 依赖（ordering AFTER）。
- [x] **验证**：全仓 build 通过（36 tasks）、jar 含 UI 类/入口/shaders/纹理/toml、112 测试 0 失败、lang 229=229、verifyAddonPackaging 通过。

---

# RTS Building: 社区版 — 记忆规范文件（架构索引 + 开发规范）

Minecraft RTS 风格俯视建造模组：手持 `rts_terminal` 进入俯视模式，远程建造/挖掘/连锁挖掘/蓝图/物品管理/容器绑定。NeoForge 1.21.1 / Forge 1.20.1（分支 `forge-1.20.1`）。Java 21。当前版本 `mod_version=1.1.4`。

本文件是所有后续会话必须遵守的**记忆规范**：前半部分是「架构索引」（每个模块/包/核心类是什么、干嘛的），后半部分是「工作规范」（构建、扩展点、编码约定、链路检查、每日总结）。新增代码前先查本文件，确认是否已有同类逻辑可复用。

---

## 一、语言约定

- 与用户交流、回复、思考均使用简体中文。
- 代码中书写注释时使用简体中文。

## 二、构建 / 运行

- 构建（Windows）：`.\gradlew.bat build --no-daemon --no-configuration-cache`
- Linux/macOS：`./gradlew build --no-daemon --no-configuration-cache`
- 编译 + 测试（链路检查修复后必跑）：`.\gradlew.bat :rtsbuilding-main:compileJava --no-daemon --no-configuration-cache` 与 `:rtsbuilding-main:test`
- `gradle.properties` 中默认启用 Gradle daemon / 并行 / 配置缓存（命令行用 `--no-daemon --no-configuration-cache` 规避）。
- 版本/仓库坐标：见 `gradle.properties`（`mod_version`、`uifw_version`、`builtin_mods` 等）。ModDevGradle `2.0.140`、NeoForge `21.1.219`、Parchment `2024.11.17`、Minecraft `1.21.1`。

## 三、模块结构总览

9 个 Gradle 子项目（`settings.gradle`）。依赖方向（编译期）：

```
rtsbuilding-api        ← 依赖底层（仅 NeoForge 环境 + JetBrains 注解），可 Maven 发布
rtsbuilding-common ──api──▶ rtsbuilding-api          （共享玩法逻辑）
rtsbuilding-ui      ◀── 完全独立，不引用任何 RTS 代码（modId=uifw，独立版本号）
rtsbuilding-main    ──compileOnly──▶ api / common / ui （主模组：NeoForge 平台层 + 打包宿主）
rtsbuilding-planetrise ──compileOnly──▶ api / common / main  （内置能量插件）
rtsaddon-ae2 / refinedstorage / beyonddimensions / sophisticatedbackpacks
                    ──compileOnly──▶ api（BD 额外编译期耦合宿主）  （内置宿主集成插件）
```

**关键约束**：主模组**禁止**编译期引用内置插件模块（planetrise / rtsaddon-*）。插件通过 `rtsbuilding-api` 的接口 + `common`/`main` 中的静态桥（`AtomicReference` 注入）与主模组通信。最终所有内置插件 + ui + common + api 的产物全部**合并进主模组 JAR**（单 JAR 多 mod）。

### 3.1 rtsbuilding-api — 公共 API 层（包 `com.rtsbuilding.rtsbuilding.api`）

给第三方 mod / 内置插件用的稳定接口层，只含接口与静态注册表，无实现、无玩法。

| 包 | 内容与职责 |
|---|---|
| `api` | 全局门面 `RtsAPI.get()`（`@ApiStatus.Internal setImplementation` 由 main 注入）+ 9 个子 API：`storage`（存储查询）、`blueprint`（蓝图材料）、`placement`（远程放置）、`interaction`（远程交互）、`mining`（挖掘/超挖/区域）、`transfer`（物品转移）、`fluids`（流体）、`bindings`（存储绑定）、`sessions`（会话查询）。另含领地保护 `ProtectionCheck`（@FunctionalInterface）+ `ProtectionRegistry`（静态注册表，远程操作前逐一 DENY/PASS 检查） |
| `api.compat` | 宿主集成 SPI。`RtsCompatRegistry`（静态注册表：integration/storageProvider/fluidProvider/backpackProvider/iconResolver 五类列表）；`RtsIntegration`（addon 统一生命周期：integrationId/available/selfCheck/register，@Experimental）；`RtsStorageNetworkProvider`（把宿主存储网络暴露为 IItemHandler）、`RtsFluidNetworkProvider`、`RtsBackpackProvider`、`RtsIconResolver`；4 个 handler 增强接口：`ReportedCountItemHandler`（上报非堆叠精确计数）、`AnySlotInsertItemHandler`（任意槽插入）、`RefreshableSnapshotHandler`（快照刷新）、`DirectExtractHandler`（按物品直接提取） |
| `api.energy` | Mekanism 风格能量容器 API（FE 单位）：`IEnergyContainer`（单容器，实现方只需 3 个方法其余默认）、`IEnergyHandler`（多容器分面）、`Action`（EXECUTE/SIMULATE）、`AutomationType`（EXTERNAL/INTERNAL/MANUAL）、`IContentsListener` |
| `common.build` | `BuilderMode`（OFF/SELECT_PAN/LINK_STORAGE/FUNNEL/INTERACT/ROTATE/BUILD/BLUEPRINT）。**故意放 api 模块**避免 common↔api 循环依赖 |
| `api.powergrid` | **电网多人系统 API**（供主 mod UI 读取）。`RtsPowerGrid`（静态门面 + `setImplementation` 注入：requestRefresh/currentGrid/members 带 RtsAccessLevel/externalMachines/inviteMember(String 按玩家名)/removeMember/setMemberAccess/transferOwnership/lastActionResult/setExternalMachineType/clearExternalMachineType/locateDevice）；数据 record `PowerGridSnapshot`（owner/ownerName/members/devices/totalGeneration/totalDemand）、`GridMember`（id/name/online/access）、`PowerDevice`、`RtsDeviceRole`(GEN/TOWER/CONSUMER)、`RtsPowerStatus`(POWERED/UNCOVERED/UNDERSUPPLIED/OFFLINE)、`RtsMachineType`(GENERATOR/CONSUMER)、`RtsAccessLevel`(OWNER/ADMIN/USER/BLOCKED+canEdit/canTransfer，BLOCKED=在线未加入「陌生人」，仅作可邀请候选人)、`ExternalMachineConfig` |

### 3.2 rtsbuilding-common — 共享玩法逻辑（包 `com.rtsbuilding.rtsbuilding`）

不含加载器代码，纯逻辑可单测（测试集中在 main/src/test）。

| 包 | 内容与职责 |
|---|---|
| `common` | `RtsUltimineCollector`（Ultimine 连锁挖掘 BFS 泛洪收集器，泛型解耦状态查询/过滤）、`RtsHistoryConstants`（形状/建造历史每玩家上限 1000） |
| `common.blueprint` | 蓝图子系统。`io/`：`BlueprintReaders`（按扩展名路由 4 种格式解析）、`BlueprintWriters`（世界框选捕获/旋转复制/写原版结构 NBT）、`VanillaStructureNbtReader`（.nbt，public 持久化恢复入口）、`SpongeSchemReader`、`LitematicReader`、`BuildingGadgetsTemplateReader`；`model/`：`RtsBlueprint`（record，自动推导材料清单）、`RtsBlueprintBlock`、`BlueprintFormat`、`BlueprintParseException`；`rule/`：`BlueprintReplaceRules`（软替换方块标签+硬编码列表）；`transform/`：`BlueprintTransform`（三轴 90° 旋转/居中偏移） |
| `common.energy` | `BasicEnergyContainer`（`IEnergyContainer` 默认实现，工厂 create/input/output）、`EnergyTransferUtils`（能量转移，源侧先 SIMULATE） |
| `common.entity` | `RtsCameraEntity`（RTS 摄像机占位实体：无物理/不可选中/`snapTo` 跳坐标/插值姿态） |
| `core.network` | `ActionType`（客户端→服务端动作协议枚举，42 个，**显式 id** 编解码，删除用 `@Deprecated` 占位防协议错位） |
| `server.workflow.model` | 工作流纯逻辑模型（零 MC 依赖）：`WorkflowState`、`WorkflowStateMachine`（转换白名单）、`RtsWorkflowType`（显式 id）、`RtsWorkflowPriority`、`RtsWorkflowStatus`（快照 record）、`RtsWorkflowProgressProcessor`（进度纯计算） |
| `util` | `RtsPinyinSearch`（拼音/首字母/字面模糊搜索，可注入字典源）、`RtsCountUtil`（数量饱和运算，防溢出） |

### 3.3 rtsbuilding-ui — uifw 独立 UI 框架（包 `com.rtsbuilding.uifw`，modId `uifw`）

与模组无关、可被任意 mod 依赖的库模组。独立版本号 `uifw_version`；合并进主 JAR（自身 toml 被排除）也可单独分发。**禁止**包含任何 RTS 玩法逻辑（当前源码 0 处引用 rtsbuilding 类）。

| 包 | 内容与职责 |
|---|---|
| 根 | `UiFrameworkMod`（@Mod 入口，注册 10 个 shader） |
| `animate` | `Easing`（11 个缓动函数）、`AnimFloat`（时间驱动动画值，hover/slide/fade 工厂）、`ColorAnimation`（RGB/HSV 插值） |
| `layout` | **纯 Java 布局数学（无 MC 依赖）**：`FlexLayout`（flexbox 行/列 + justify/align/gap/flex 权重）、`GridLayout`（均分网格）、`UiBox`/`UiSize`（尺寸声明 px/percent/auto）、`UiRect`。渲染与命中检测必须复用同一布局计算结果 |
| `render` | `SdfRenderer`（SDF 矢量绘制核心：圆角矩形/描边/胶囊/色轮/箭头/输入框等）、`UiShaders`、`UiPalette`（JSON 主题调色板，全库颜色唯一来源，支持宿主 `assets/<ns>/theme/uifw.json` 覆盖）、`TextRenderer`、`SpriteRenderer`（精灵/九宫格/平铺）、`MipmapTexture`（生成完整 mip 链）、`FilterState`（PIXEL/NORMAL/HQ 过滤状态）、`CrossFadeRenderer`、`BlendScope`、`GuiRenderTypes`、`GuiItemRenderer`（物品图标 + 深度缓冲污染清理）、`model/`（TextureInfo/SpriteRegion/NineSliceRegion） |
| `state` | `TooltipController`（延迟 tooltip）、`HoverSuppression`（浮窗遮挡抑制下层 hover） |
| `theme` | `ThemeManager`（明暗单例 + 变更广播）、`ThemeListener` |
| `window` | 窗口系统：`api/`（`UiPanelHost` 宿主屏幕抽象——uifw 与宿主的唯一解耦点、`UiPanelApi`）、`UiPanel`（可拖拽/缩放/关闭的面板基类）、`FloatingWindowLayer`（叠放/点击穿透）、`WindowFrameRenderer`、`PanelDragHandler`/`PanelResizeHandler`/`PanelDragPerformanceOptimizer`、`AbstractButtonGroup`、`DownOverlayLayer`、`BasePopup`、`CollapsibleSection`/`SettingsSection`/`ScrollBar`/`EdgeResizeHandler` |
| `component` | 组件库：`UiButton`/`CloseButton`/`ToggleSwitch`/`TextInputBox`/`NumericInputBox`/`HexInputComponent`/`ScaleSliderComponent`/`ColorPickerButton`/`CircleColorSwatch`/`ResetButton`/`DeleteButton` + `color/`（`ColorPickerPanel` 调色盘、`ColorWheelComponent` 矢量色轮、`GrayscaleBarComponent`，是 FlexLayout 规范示范实现） |

### 3.4 rtsbuilding-main — 主模组（包 `com.rtsbuilding.rtsbuilding`，NeoForge 平台层）

**整合发布模块**：承载加载器相关代码，最终把全部子模块合并进主 JAR。src 下包职责：

| 包 | 内容与职责 |
|---|---|
| 根 | `RtsbuildingMod`（@Mod 主入口：注册方块/物品/实体/标签、初始化 `RtsServer` + `RtsAPIImpl` + 工作流管道、GameEvents 订阅登录/登出/tick）、`Config`（`rtsbuilding-common.toml`：蓝图/幽灵预览/能量开关与耗能）、`PerformanceConfig`（渲染性能开关） |
| `client.bootstrap` | `RtsClientBootstrap`（渲染器/mipmap/按键/内核装配）、`ClientInputBridge`、`ClientTickHandler`、`ClientRenderHandler`（帧级相机更新 + 渲染管道/无人机光束）、`RtsMipmapTextures`（大尺寸 GUI 贴图 mipmap 注册，配合资源重载） |
| `client.kernel` | 客户端单例内核：`RtsClientKernel`（模块注册/初始化、StateEvent 分发与 32 条重放缓冲、BuilderScreen 开关）、`FeatureModule`（模块接口）、`StateEvent`/`EpochClock` |
| `client.infrastructure.module` | 7 个客户端功能模块：`camera`（CameraModule：自由/环绕相机、姿态上报节流）、`building`、`mining`、`overlay`、`pathfinding`、`remote`（RemoteMenuModule）、`storage`（StorageModule） |
| `client.input` | 分层输入：`InputPipeline`/`InputLayer`/`layer/`（CameraInputLayer 等）、`RtsKeybinds`/`RtsKeyMappings`（持久化到 `config/rts_building/keybinds.json`）、`RtsClientInputGate` |
| `client.network` | `RtsClientPacketGateway`（所有 C2S 发包门面）、`RtsClientNetworkHandlers`（所有 S2C 收包处理） |
| `client.presentation` | UI 表现层。`standalone/BuilderScreen`（**RTS 主界面**：组合全部面板、虚拟坐标系渲染/事件路由、蓝图放置模式状态：准星锚点 + 点击确认发 C2S `PLACE_BLUEPRINT`）、`panel/`（topbar 顶栏、leftbar 左栏建造/破坏/形状、rightbar 右栏存储浏览器、downbar 下面板 + trackball、gear 齿轮设置含 `IntegrationSection` 集成健康、blueprint 面板组：库（行内 使用/预览/打开/删除 按钮）/导入/保存/**结构预览** `BlueprintPreviewPanel`/interaction 容器交互、resume 工作流恢复、powergrid **电网管理面板** `PowerGridManagerPanel`（参照 Flux-Networks 布局：顶部信息+横向 Tab 成员/设备/外部机器+分页网格；成员 Tab 列出组成员+在线未加入的「陌生人」(BLOCKED) 行，点击行开 `PopupMemberEdit` 权限编辑弹窗，对陌生人点「设为用户」即邀请加入——无输入框不输玩家名；K 键在 `BuilderScreen.keyPressed` 切换）、background 世界截帧、handler 交互处理器）、`plugin/`（嵌层插件：绑定/网格/工作流进度）、`layout/`（PanelRegistry/RenderLayer）、`event/`（UI 事件模型） |
| `client.render` | 世界渲染：`RenderPipeline`（各 pass 顶点统一 flush）、`RenderPass`/`pass/`（框选/交互目标/边界墙/连锁挖掘预览/动画/蓝图放置幽灵预览 `BlueprintPlacementPreviewPass`）、`RtsEffectStateTracker`（放置/破坏动画绑定）、`GhostRingBuffer`、`CapturedFrameTexture`/`ViewCaptureService`（世界截帧做 UI 背景）、`DroneBeamRenderer`、`util/` |
| `client.scene` | **结构预览场景渲染**（参考 LDLib2 `WorldSceneRenderer`/`FBOWorldSceneRenderer`/`DummyWorld`，同为 NeoForge 1.21.1）：`RtsDummyLevel`（继承 Level 的纯内存虚拟世界，方块存 Map、光照固定 15、无实体/刻调度）、`RtsSceneRenderer`（VBO 缓存编译 + FBO 相机渲染 + 纹理绘制；球坐标相机 yaw/pitch/radius 支持拖拽旋转滚轮缩放；`RenderTargetScope` 保存/恢复 FBO+viewport+scissor） |
| `client.rtsbuild.shape` | `BuildShape`（建造形状枚举：线/墙/平面/体/圆面/球）+ `ShapeGeometry` 纯几何计算；`LineBrushSelector` 画笔状态机 |
| `client` 其他 | `camera/RtsCameraEntityRenderer`（隐形渲染器）、`domain/`（客户端领域模型）、`entity/`（`rts_drone` 无人机渲染 + 动画）、`application/service/ScreenCoordinator`（容器交互面板协调器）、`blueprint/BlueprintLocalStore`（本地蓝图文件存储 config/rts_building/blueprints）、`compat/`、`state/FeatureAdjusterState`、`util/` |
| `client.model` | **Java 模型 ↔ 碰撞箱互转工具** `JavaModelShapeUtil`：`toCubeListBuilder(RtsJavaModelShape, origin)`（纯几何数据 → 渲染 CubeListBuilder）、`fromModelPart(ModelPart, ignoreParts)`（已烘焙 ModelPart 8 角点矩阵变换 → VoxelShape，client-only）。**所有使用 Java 模型（ModelPart）的方块**：碰撞箱用 `RtsJavaModelShape.buildShape()`（服务端安全），渲染用 `toCubeListBuilder` 同源生成，禁止手写 `Shapes.or(Block.box(...))` |
| `common` | 主模组自有注册与桥：`RtsBlocks`/`RtsItems`/`RtsEntities`/`RtsCreativeTabs`（注册表）、`item/RtsTerminalItem`（终端物品，右键切换 RTS 模式 + 能量条）、`entity/RtsDroneEntity`（服务端权威无人机）、`RtsTerminalEnergy`（**终端能量桥**：静态 `AtomicReference<Provider>`，供能量插件注入）、`geometry/`：`RtsModelShapeParser`（模型 JSON→碰撞箱）、`RtsVoxelShapeUtils`（VoxelShape 组合/旋转/裁剪）、`RtsJavaModelShape`（**Java 模型纯几何盒数据**：方块像素坐标 addBox + `buildShape()` 生成碰撞箱，与渲染模型同源） |
| `compat` | `jei/`（RtsJeiPlugin + 全局 GUI 处理器）、`remote/RtsRemoteMenuCompat`（原版箱子/铁炉/GeneratorGalore/Sophisticated 远程菜单检测）、`RemoteMenuTracker` |
| `mixin` | 16 个 mixin（`rtsbuilding.mixins.json`）：`KeyboardInputMixin`（RTS 下完全接管键盘）、`MouseInputMixin`（阻断侧键）、`MinecraftSetScreenMixin`（容器屏幕嵌入 BuilderScreen 而非替换）、`LocalPlayerMixin`（强制 isControlledCamera）、`ChestMenuMixin`/`ModdedRemoteStillValidMixin`（远程 stillValid 强制通过，@Pseudo）、`ClientPacketListenerMixin`（吞 2001 破坏事件）、`ClientLevelMixin`（抑制粒子）、`ScreenRenderBgMixin`、`RtsGuiOverlayMixin`/`RtsChatComponentMixin`（暴露原版 actionbar/聊天消息，供 RTS 覆盖式 Screen 在下面板之上补渲染）、`RtsChatScreenMixin`（RTS 下打开原版 ChatScreen 关闭时恢复 BuilderScreen，聊天不退出 RTS）、`MinecraftTickMixin`、`LocalPlayerStepAiMixin` 等 |
| `network` | `RtsPayloadRegistrar`（统一 payload 注册入口）、`ClientPayloadDispatcher`（S2C 分发桥，IS_CLIENT 守卫）、`NetworkConstants`、`message/C2SAction`（统一 C2S 动作：ActionType + NBT 参数，未知 id 返回 null 防恶意包）、`message/C2SCameraPosePayload`（高频姿态）、`handler/ServerActionHandler`（服务端统一分发约 40 种动作）、`{camera,storage,builder,feedback,blueprint,resume}/` 各领域 payload |
| `platform` | `Platform`（注册表/配置/能力/发包的加载器统一抽象） |
| `server` | 服务端逻辑（加载器侧）：`RtsServer`（服务注册表核心：ServiceLoader 发现 10 个 `RtsService`，按 dependencies 拓扑排序装配 + 集成健康检查）、`RtsService`（init/shutdown/dependencies）、`api/impl/`（`RtsAPIImpl` 总实现 + 各子 API *Impl）、`camera/RtsCameraManager`（相机会话/锚点/姿态钳位/动作范围 AABB）、`data/`（持久化：DataCluster/DataComponent/NbtCodec/SaveScheduler 每 200 tick 刷盘）、`history/ServerHistoryManager`（撤销系统：服务端权威记录、10 分钟过期、64 格/ tick 预算）、`pipeline/`（**工作流管道系统**：core 的 PipelinePipe/WorkflowPipeline/PipelineRegistry、validation 会话/维度校验、tool 工具借用/归还、mining、placement、blueprint、execution/SyncPipe、workflow）、`service/`（10 个服务实现 + mining/placement/fluids/transfer/page/interaction/bindings/beam 子包）、`storage/`（会话 `RtsStorageSession`/绑定/页面/流体/缓存/解析器/视图）、`workflow/`（工作流引擎 `RtsWorkflowEngine` 单例：每玩家每维度槽位管理器、脏标记每 tick 合并发包、存档恢复）、`tracking/RtsBlockTrackingEvents`、`util/` |

资源：`assets/rtsbuilding/`（lang 中英文各 ~284 key、`theme/uifw.json` 主题覆盖、`pinyin/data.txt` 拼音字典、`textures/gui/` 面板贴图、模型/纹理）、`data/rtsbuilding/tags/block/blueprint_soft_replaceable.json`、`META-INF/services/...RtsService`（ServiceLoader 声明）。`src/main/templates/META-INF/neoforge.mods.toml` 是构建期模板（见第五节）。

### 3.5 rtsbuilding-planetrise — 内置能量插件（modId `rtsbuilding_planetrise`，包 `com.rtsbuilding.rtsbuilding.planetrise`）

"Planet Rise（星球崛起）"：热能动能机产能 + **电网输电塔广播供电** + 储能单元 + 终端用电。电力系统参照 `docs/../power_system_design.md` 设计：只存在两种半径（链路范围 LinkRange、供电范围 PowerRange），三分类建筑（发电机器 / 输电塔 / 用电机器）。旧「戴森球式无线输电（塔范围内吸取+分发）」逻辑已**整体弃用**。可被 `Config.enableTechnologized` 整体禁用。**方块四个：热能发电机 + 风力发电机（发电机器）、无线输电塔（唯一供电建筑）+ 储能单元（用电/存储节点）**。

| 包 | 内容与职责 |
|---|---|
| 根 | `EnergyMod`（@Mod 入口，commonSetup 注入终端能量 Provider）、`EnergyBlocks`/`EnergyBlockEntities`/`EnergyItems`/`EnergyCreativeTabs`/`EnergyCapabilities`（方块/方块实体/物品/创造栏/能力注册）、`TerminalEnergyImpl`（终端用电：`terminal_energy` 数据组件 + 物品 IEnergyStorage + `RtsTerminalEnergy.Provider` 实现，开启 RTS 扣 500 FE，亮绿能量条） |
| `power` | **电网核心逻辑**（参照 power_system_design.md；组=电网、组内全互通）：`PowerRole`（GENERATOR/TOWER 节点角色）、`GridNode`（调度输入快照，坐标用 long 解耦 MC）、`PowerScheduler`（纯算法：组内聚合发电量与需求 → 按需求占比分配、吞吐封顶、转发不衰减；保留「发电经塔中转、供电范围广播」角色约束）、`IPowerGridNode`（方块实体实现的节点接口：role/linkRange/powerRange/throughput/generation/demand/gridOwner/acceptQuota）、`PowerGridManager`（服务端每维度调度器：按归属组分组、每 tick 惰性聚合+写回各塔配额、nodesOf/aggregate 聚合查询，委托全局所有权）、`PowerGridOwnership`（服务端**全局电网所有权**：参照 Flux-Networks changeMembership/NetworkMember/AccessLevel——owner/members 带 RtsAccessLevel(OWNER/ADMIN/USER) 阶梯、changeMembership(actor,target,action,online)→结果码(SUCCESS/NO_ADMIN/NO_OWNER/INVALID)、邀请只对在线玩家、升降级/移除/转移所有权（旧主降 USER、目标在线且为成员、不能转给自己）、组持久化 `config/rts_building/powergrid_groups.json` 重启保留，成员跨维度共享） |
| `network` | 「电网多人」网络包：`PowerGridPackets`（C2S refresh/invite/remove/externalConfig/memberAction + S2C `S2CPowerGridSnapshot` 含成员在线/等级/设备/总发电总耗电/外部配置 + S2C `S2CPowerGridActionResult`）、`PowerGridServerHandler`（invite 按名解析、changeMembership 权限校验、成员操作结果回传、快照构造推送）、`PowerGridClientHandler`（S2C→客户端缓存）、`PowerGridApiImpl`（实现 `RtsPowerGrid`：读缓存+发C2S）、`PowerGridNetworkRegistrar`（注册 payload） |
| `server.powergrid` | `PowerGridExternalConfigStore`（外部机器类型配置持久化 `config/rts_building/powergrid_external.json`，仅 Generator/Consumer，纯传输 Substation 不开放玩家配置） |
| `client.powergrid` | `PowerGridClientCache`（客户端缓存电网快照/外部配置，供 UI 读取） |
| `block` | `EnergyBlock`（基类：RtsModelShapeParser 碰撞箱）、`ThermalGeneratorBlock`（热能发电机：LIT/FACING 状态，岩浆燃烧产能）、`PowerTowerBlock`（**无线输电塔**：电网唯一广播供电建筑，1 主方块 + 上方 4 占位，塔身 5 格高，`MODEL`（`RtsJavaModelShape` 纯几何，与 Blockbench `Wireless_Energy_Transmission_Tower` 塔身同源）→ `SHAPE`）、`EnergyCellBlock`（储能单元：右键查看电量）、`WindGeneratorBlock`（**风力发电机**：1 主方块 + 上方 2 占位，塔身 3 格高，`MODEL`（`RtsJavaModelShape` 纯几何，与 Blockbench `Wind_Turbine` 塔身同源）→ `SHAPE`）、`BoundingBlock`（多占位代理方块：形状/交互/破坏/能力代理主方块，`dynamicShape()`） |
| `block.entity` | **`AbstractEnergyMachineBlockEntity`（所有 RTS 能量机器抽象基类：统一能量缓冲（BasicEnergyContainer）/`createEnergyStorage` 能力工厂/`addEnergy`+`consumeEnergy` 内部充放电/tick 模板（`tickServer`+`tickClient` 含 level、客户端、`Config.isTechnologizedEnabled` 守卫 + 首次占位同步）/energy NBT 持久化 + `saveEnergyAdditional`/`loadEnergyAdditional` 扩展钩子，后续新机器直接继承，子类只需覆写 `onServerTick`/`onClientTick`；另统一处理 `onLoad`/`setRemoved` 的电网注册与注销）**、`ThermalGeneratorBlockEntity`（2 万 FE 缓冲 + 8000mB 岩浆罐，tick 产 60 FE，实现 `IPowerGridNode` 发电机器，`generation()` 上报产电速率）、`PowerTowerBlockEntity`（**无线输电塔/电网节点**：链路范围 + 供电范围，分片扫描供电范围发现用电器统计 `demand()`→ `PowerGridManager` 调度分得配额 → `acceptQuota()` 后 `broadcast()` 把配额注入供电器；实现 `IBoundingBlock`（4 占位，占位格子可暴露缓冲）、`EnergyCellBlockEntity`（储能单元：Config 容量缓冲，双向 IEnergyStorage，无 tick）、`WindGeneratorBlockEntity`（按塔顶高度比例产电 + canSeeSky，实现 `IPowerGridNode` 发电机器）、`BoundingBlockEntity`（占位实体：仅存 mainPos NBT，`getUpdateTag`/`handleUpdateTag` 显式同步）、`IBoundingBlock`（多占位接口：创建/清除/形状/交互转发/能量代理/`syncBoundingBlocks`）、`ContainerEnergyStorage`（IEnergyContainer→IEnergyStorage 适配器） |
| `client.power` | **范围圈视觉反馈**（参照 power_system_design.md 第六节）：`PowerRangeVisualStore`（客户端单例，能量节点 `onLoad`/移除时注册/注销）、`PowerRangeOverlayRenderer`（挂 `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS`，画水平圆环：链路蓝圈 + 供电黄圈）、`EnergyClient` 注册快捷键「显示电力范围圈（按住，默认 P）」 |
| `client` | `EnergyClient`/`BlockRenderProperties`（破坏粒子聚合器，参考 Mekanism）、`model/ModelWindGenerator`（Blockbench `Wind_Turbine` 导出的实体模型几何，纹理 128×128；渲染时 `scale(-1,-1,1)` 翻转实体坐标 + `translate(0,1.5,0)` 地面落位，风叶 `bone` 组绕 y 轴旋转动画）、`model/ModelPowerTower`（Blockbench `Wireless_Energy_Transmission_Tower` 导出的实体模型几何，纹理 128×128；`body` 塔身 + `rotate` 顶部天线组，天线按 Blockbench 动画 3 秒绕 y 轴一圈 + 1.0→0.8→1.0 缩放脉冲，同样 translate+scale 落位）、`render/RenderWindGenerator(Item)`、`render/RenderPowerTower(Item)`（BEWLR：translate(0.5,0,0.5) 跨格绘制整塔） |

### 3.6 rtsaddon-* — 内置宿主集成插件（仓库根目录独立项目）

把外部宿主 mod 的存储能力接入 RTS 存储面板。统一形态：主类 `implements RtsIntegration`，`@Mod` 构造时 `RtsCompatRegistry.registerIntegration(this)`；宿主未加载则只打 INFO；反射失败仍注册 integration（带 loadError，设置面板可见）。**无 resources、无自身 toml**。

| 项目 | modId | 集成宿主 | 说明 |
|---|---|---|---|
| `rtsaddon-ae2` | `rtsbuilding_addon_ae2` | Applied Energistics 2 | ME 网络物品（IItemHandler）+ 流体收集 + 图标解析。`RtsAe2Addon`/`Ae2StorageProvider`/`Ae2FluidProvider`/`Ae2IconResolverProvider`/`Ae2NetworkItemHandler`（IKeyCounter 快照，10 tick 节流）/`Ae2Reflection`（MethodHandles 反射绑定）。宿主 API 运行时反射，编译期零依赖 |
| `rtsaddon-refinedstorage` | `rtsbuilding_addon_refinedstorage` | Refined Storage 2 | RS 2.0 网络物品（组件式 API：getContainerProvider→getNetwork→StorageNetworkComponent）。`RtsRefinedStorageAddon`/`RsStorageProvider`/`RsNetworkItemHandler`/`RsReflection`。仅物品，无流体/图标 |
| `rtsaddon-beyonddimensions` | `rtsbuilding_addon_beyonddimensions` | Beyond Dimensions | 玩家维度主网络（物品+流体，虚拟网络回退 BlockPos.ZERO，`SessionFlags.useBdNetwork` 控制）。**唯一编译期强耦合宿主**（`BdAdapter` 收敛全部宿主引用，`BdStorageProvider`/`BdFluidProvider`/`BdDirectItemHandler` 含 `DirectExtractHandler`） |
| `rtsaddon-sophisticatedbackpacks` | `rtsbuilding_addon_sophisticatedbackpacks` | Sophisticated Backpacks | 背包方块按 UUID 链接；背包方块被破坏/重放按 UUID 迁移引用；手持背包物品也能打开存取。`RtsSophisticatedBackpacksAddon`/`BackpackProvider`/`BackpackReflection` |

## 四、核心架构数据流（理解用）

- **进入 RTS 模式**：手持 `rts_terminal` 右键 → 客户端 `RtsClientPacketGateway.sendToggleCamera` → `C2SAction(TOGGLE_CAMERA)` → `ServerActionHandler`（校验终端能量 `RtsTerminalEnergy`）→ `RtsCameraManager.toggle`（创建相机+无人机实体、建会话）→ S2C 相机回包 → `RtsClientKernel.dispatch` → 打开 `BuilderScreen`。
- **远程建造**：BuilderScreen 捕获鼠标 → 形状计算（`BuildShape`/`LineBrushSelector`）→ `sendPlace/sendLinePlace/sendAreaBoxPlace` → `C2SAction(PLACE_BLOCK/PLACE_BATCH)` → `ServerActionHandler` → `RtsPlacementServiceImpl` → `PipelineRegistry.execute` 工作流（校验→工具借用→放置→同步）→ 放置批处理逐 tick 落位 → 动画/音效回客户端。
- **相机权威边界**：相机移动/旋转是纯客户端计算，客户端 10Hz + 变化检测上报姿态（专用 `C2SCameraPosePayload`），服务端 `RtsCameraManager` 钳位校验后作为权威位置（动作范围 AABB 判定、无人机跟随）。
- **电网供电链路（参照 power_system_design.md）**：发电机器（发电机/风电机）`tickServer` 上报本 tick 产电速率 `generation()` → 输电塔 `tickServer` 分片扫描其<b>供电范围</b>、用 `demand()` 上报范围内用电器需求 → 二者向 `PowerGridManager` 注册；`PowerGridManager.tick` 每 tick 用 `PowerScheduler` 按<b>电网组</b>（组=电网，成员跨维度共享、组内全互通、组间隔离）聚合发电量与需求 → 按需求占比+吞吐封顶给各塔分配<b>配额</b> → `acceptQuota` 写回 → 塔 `broadcast()` 把配额注入供电范围内用电器（任何可注入 `IEnergyStorage` 方块，含储能单元/外部机器）；转发**不衰减**。节点归属：放置者经 `RtsGridOwnerBinding`（common 桥）绑定 `gridOwner`。**电网多人**：`PowerGridOwnership` 全局组（唯一所有者 + 成员邀请/移除，UI 按 K 打开 `PowerGridManagerPanel` 管理），组快照经 S2C `S2CPowerGridSnapshot` 推送到客户端缓存，main UI 经 `RtsPowerGrid`（api）读取。外部模组经方块 `IEnergyStorage` capability ↔ `ContainerEnergyStorage` 适配器互动；塔/发电机自带 `BasicEnergyContainer` 仅作外部接口查看、不参与电网能量搬运。建造操作不耗能。
- **AE2 作为链接存储**：面板对准 ME 节点方块链接 → `RtsLinkedCapabilities.findLinkedItemHandler` 遍历 `RtsCompatRegistry.getStorageProviders()` → `Ae2StorageProvider.createItemHandler`（反射：GridHelper→Grid→StorageService）→ 注册进存储缓存，`RtsPageCore` 构建页面 S2C 推送；下线时 `releaseItemHandler` 释放网络句柄。

## 五、扩展点与打包机制（新增内置插件必读）

- **`builtin_mods` 机制**：`gradle.properties` 的 `builtin_mods=rtsbuilding-planetrise,rtsaddon-*` 是**单一来源清单**。`rtsbuilding-main/build.gradle` 从它派生：① `neoForge.mods{}` 把各插件 sourceSet 并入主 mod（dev 运行时）；② `jar{}` 把各插件 output 合并进主 JAR（uifw 合并时 exclude 自身 `META-INF/neoforge.mods.toml`）；③ `verifyAddonPackaging` 校验每个插件入口类已合入 + toml 已声明（挂在 `check`）。
- **新增内置 addon 四步**：① `settings.gradle` include；② `gradle.properties` 的 `builtin_mods` 追加；③ `rtsbuilding-main/src/main/templates/META-INF/neoforge.mods.toml` 追加 `[[mods]]` 与 `[[dependencies.*]]`（主 mod required、宿主 mod optional 如 `ae2 [15,)`）；④ `rtsbuilding-main/build.gradle` 的 `addonManifest` 登记项目→modId→@Mod 入口类。任何宿主集成先检查是否已有 `rtsaddon-<host>/`。
- **桥接注入模式**：主模组与内置插件通信走 1 种 `AtomicReference` 静态桥/注入——`RtsTerminalEnergy.Provider`（主模组被动查询的供应器）；宿主集成统一走 `RtsCompatRegistry`。
- **协议枚举规则**（`ActionType`/`RtsWorkflowType`/`RtsWorkflowPriority`/`BuilderMode`）：必须**显式 id** 编解码（`fromId` 越界返回 null），删除值用 `@Deprecated` 占位保留 id 防新老端协议错位；改枚举后必须跑 `:rtsbuilding-main:test`（`ProtocolEnumTest` 等护栏）。
- **uifw 打包注意**：toml 只放 `src/main/templates/`，**不要**放 `src/main/resources/`（否则 dev 运行与主 toml 双声明 modId 触发 `dangling_entrypoint`）；主模组依赖 uifw 用 `compileOnly` 而非 `implementation`（否则 `duplicate_mod`）。

## 六、规范约定

- 基础包名 `com.rtsbuilding.rtsbuilding`；模组 ID `rtsbuilding`；内置插件/集成 modId 见上表。
- **逻辑不放加载器相关模块**：共享行为放 `common`，跨模组钩子通过 `rtsbuilding-api` 暴露。
- **UI 布局规范**：新增面板 / 重构 UI 排布时，一律使用 uifw 布局包 `com.rtsbuilding.uifw.layout`（`FlexLayout` 行/列 + justify/align/gap/flex 权重、`GridLayout` 网格、`UiBox`/`UiSize` 尺寸声明），禁止手写散落坐标。行内排布用 `FlexLayout`；规则网格用 `GridLayout`；**渲染与命中检测必须复用同一布局计算**（参考 `ColorPickerPanel` 示范）。现有稳定面板不强改（tooltip/滚动/命中坐标耦合），后续重构时按此规范迁移。
- **面板生成位置统一**：所有浮窗面板（`UiPanel` 子类）的 `computeDefaultPosition()` 一律使用统一基准——尺寸自适应（`w=min(getDefaultWidth(), 屏幕宽-16)`、`h` 类似，留 8px 边距）+ `positionCentered(TOP_H + 6, 8)`（水平居中 + 垂直居中，顶部避开顶栏、底部留 8px 边距）。参考 `GearMenuPanel`/`ResumeWorkflowPanel`/`InteractionPanel`/蓝图系面板实现。**禁止**自定义锚定（如固定右侧/左上），除非有强交互理由并注释说明。
- **大尺寸贴图必须模糊化（mipmap）**：凡源图 ≥256px、实际绘制到 ≤24px（约 20 倍以上缩小）的 GUI 贴图，一律用 mipmap 平滑方案，禁止像素风采样。三要素缺一不可：① `TextureInfo.FilterMode` 用 `HQ`（linear+mipmap=true，绘制由 `TextureStateShard` 强制 `setFilter(true,true)`）；② 启动/资源重载时注册进 `RtsMipmapTextures.registerAll()`（用 `MipmapTexture` 加载生成完整 mip 链）；③ 贴图尺寸必须为 2 的幂。**不要**给这类贴图写 `blur:true` 的 `.mcmeta`（无效且误导，vanilla `SimpleTexture` 永不生成 mipmap）。已迁移：`textures/gui/left/right_button`、`textures/gui/left/button`、`textures/gui/top` 全部图标。
- **多元素模型破坏粒子必须聚合**：凡碰撞箱由多个元素合并（`RtsModelShapeParser` 组合 / `VoxelShape` 含多个 AABB）的方块，破坏/挖掘时必须覆写 `IClientBlockExtensions.addDestroyEffects` 用整体包围盒聚合生成一组粒子（参考 `rtsbuilding-planetrise` 的 `BlockRenderProperties`，仿照 Mekanism：按 0.25 间隔在 shape 包围盒内散布 `TerrainParticle`），**禁止**用原版按碰撞箱每个 AABB 逐段生成粒子的默认行为（会造成粒子爆炸）。挂载方式：在客户端扩展注册事件（`RegisterClientExtensionsEvent`）中 `event.registerBlock(BlockRenderProperties.INSTANCE, <block>)`。已挂载：`thermal_generator`/`power_tower`/`energy_cell`。新增多元素模型方块时必须同步挂载。
- **Blockbench Java 模型方块（`LayerDefinition`）建模规范**：使用 Blockbench 导出的 `modded_entity`（`flip_y=True`，实体坐标 **Y 向下为正**）时，按以下流程接入（参考 `rtsbuilding-planetrise` 的 `ModelWindGenerator`/`WindGeneratorBlock`，对照 Mekanism `ModelWindGenerator`/`RenderWindGenerator`）：
  1. **渲染模型**：把导出的 `createBodyLayer()` 几何照搬为 `LayerDefinition`（`MeshDefinition` + `PartDefinition.addOrReplaceChild`），纹理分辨率（`LayerDefinition.create(mesh, w, h)`）必须与贴图尺寸一致；贴图放到 `assets/<modid>/textures/block/<name>/` 并**确保 PNG 分辨率与 UV 坐标系匹配**（Blockbench 按 128 坐标系导出时，若源 PNG 为 256 需先缩放到 128，否则 UV 采样错位）。
  2. **坐标落位（BEWLR 内）**：实体坐标 → 方块世界坐标必须做 `poseStack.translate(0, 1.5, 0)` **再** `poseStack.scale(-1, -1, 1)`，顺序不可颠倒——`PoseStack` 是右乘矩阵，后调用的变换先作用于顶点，先 `scale` 后 `translate` 会把整塔翻到地下（等价 Mek 的 `translate → mulPose(Axis.ZP.rotationDegrees(180))`）。`scale(-1,-1,1)` 等价绕 Z 轴 180°，必须 X/Y **同时**翻转；仅翻 Y（`scale(1,-1,1)`）是镜像，会致贴图/模型反的。
  3. **旋转部件**：风叶等绕竖直方向旋转的部件直接设 `bone.yRot = angle`（动画角度由方块实体客户端 tick 累积，`getClientAngle(partialTick)` 插值）；旋转部件**不参与碰撞**。
  4. **碰撞箱（服务端安全）**：方块 `getShape`/`getCollisionShape` 在服务端也会被查询（寻路/交互/放置校验），**禁止**在方块静态初始化里调用 client-only 的 `JavaModelShapeUtil.fromModelPart`（依赖 `ModelPart`，dedicated server 崩溃）。碰撞箱必须来自纯数据 `RtsJavaModelShape`（方块像素坐标，模型原点对准方块底部中心 `(8,0,8)`，可跨格）→ `buildShape()` 自动生成 `VoxelShape`，逐盒对应模型塔身各段（底座/收窄/塔杆/装饰/塔顶）保证贴合。`fromModelPart` 仅用于客户端建模/诊断与一致性校验。
  5. **占位高度**：塔形多占位方块（`IBoundingBlock`）的 `BOUNDING_HEIGHT`/`BOUNDING_OFFSETS`/渲染器包围盒（`getRenderBoundingBox`）必须与模型格高一致（如 3 格高 → 主方块 + 上方 2 占位）。

## 七、语言文件（lang）约定

所有**用户可见 UI 文案**必须通过 lang 语言文件管理，禁止硬编码中文：

- Lang 文件：`rtsbuilding-main/src/main/resources/assets/rtsbuilding/lang/zh_cn.json`（中文）与 `en_us.json`（英文），两个文件 key 必须同步（可用 `scripts/verify-lang.ps1` 校验）。
- **uifw 模块**的文案走它自己的 lang：`rtsbuilding-ui/src/main/resources/assets/uifw/lang/zh_cn.json` 与 `en_us.json`，key 前缀 `screen.uifw.*` 等。UI 库组件不得引用主 mod 的 `*.rtsbuilding.*` key；宿主 mod 传入的 key 由宿主自己定义。
- Key 前缀约定：`screen.rtsbuilding.*`（面板/界面）、`ui.rtsbuilding.*`（小组件/标签）、`message.rtsbuilding.*`（聊天/提示）、`button.rtsbuilding.*`（按钮）、`tooltip.rtsbuilding.*`（提示）、`key.rtsbuilding.*`（按键）。
- 代码中通过 `Component.translatable(key, args)`（Component）或 `.getString()`（String）取文案；带参数用 `%d`/`%s` 占位符。
- **不迁移**（保留硬编码）：日志输出（`LOGGER.*`）、异常消息（`throw new ...Exception`）、代码注释、测试断言消息。
- **待迁移（后续）**：`BuildShape.hint`（形状交互提示，含 `LineBrushSelector` 的"建造/破坏/右键/左键"替换）、`BindingRenderer`/`RowLayout`（绑定按钮文字）、`RenderingSection`（设置面板颜色标签与 tooltip）。新增 UI 文案时直接按 lang 方式编写，不再产生新的硬编码。

## 八、禁止改动

- `build/`、`rtsbuilding-main/run/`（Minecraft 开发运行目录）、生成源码。
- `docs/app/node_modules/`、`docs/dist/`（docs 前端构建产物，由 `npm run build` 生成）。
- 注意：`.gitignore` 忽略了 `.github/`，若未来加 CI workflow 需 `git add -f`。

## 九、逻辑链路检查（"XX链路检查" 指令）

当用户说「XX 逻辑的链路检查」/「走一遍 XX 链路」/「查询 XX 逻辑」时，执行以下固定流程：

1. **定位**：用 `grep`/`glob` 找出该逻辑涉及的全部文件与调用点，梳理「客户端 → 网络包 → 服务端 → 回包 → 客户端」的完整数据流。
2. **逐节点审查**：读取每个关键调用点，重点排查三类问题：
   - **边界问题**：空值/越界/维度、跨端状态不同步（如服务端依赖客户端上报的坐标）、时序竞态、限流缺失。
   - **冗余代码**：死代码（无调用方的方法）、重复实现、过时注释（引用已删除的类/方法）。
   - **断点**：链路断裂——调用缺失、包未注册、mixin 未生效、返回路径提前 return 不通知客户端。
3. **修复**：先向用户报告问题清单再动手；修复后必须 `.\gradlew.bat :rtsbuilding-main:compileJava --no-daemon --no-configuration-cache` 编译 + `:rtsbuilding-main:test` 测试通过。
4. **产出 JSON 报告**：按 `docs/schemas/logic-review.schema.json` 的结构写 `<链路名>.json` 存入 `docs/reports/`（如 `docs/reports/sound-architecture.json`）。报告数据由 `docs/app`（Vue3 SPA）统一渲染，无需手写 HTML。可选：修改 JSON 后运行 `npm run build`（在 `docs/app/`）刷新 `docs/dist/` 静态站点。

// ============================================================
// RTS Building · 功能总览数据
// 由源码逐模块梳理（rtsbuilding-common / rtsbuilding-main /
// rtsbuilding-api / rtsbuilding-technologized / rtsaddon-*）
// 结构：modules[].features[].{name,desc,classes}
// ============================================================

export const featuresCatalog = {
  generated: '2026-08-13',
  note: '该清单基于主分支源码梳理，覆盖 NeoForge 1.21.1 版本（rtsbuilding-main 主模组 + 内置能源 addon + 4 个宿主模组集成 addon）。',
  modules: [
    {
      id: 'build',
      name: '建造系统',
      tagline: '像 RTS 一样从俯视视角规划、放置方块',
      features: [
        {
          name: '形状建造器',
          desc: '7 种可调形状：线 / 墙 / 平面 / 立方体 / 圆柱 / 球体，每种形状自带几何计算、阶段流转与参数调整。',
          classes: ['BuildShape', 'ShapeParams', 'ShapeInput', 'Phase', 'AdjustKind']
        },
        {
          name: '填充模式',
          desc: '实心 / 空心（外壳薄壳）/ 框架（边缘骨架）/ 连接 / 断点 五种填充模式，各形状支持子集不同。',
          classes: ['FillMode']
        },
        {
          name: '画笔状态机',
          desc: '通用驱动的点选式建造状态机：右键选点、Shift+滚轮调参、右键推进/确认、ESC 回退，按形状缓存几何结果。',
          classes: ['LineBrushSelector', 'PhaseAdvance']
        },
        {
          name: '框选批量操作',
          desc: '三点（A/B/C）框选立方体区域，配合滚轮高度偏移，支持批量放置与批量破坏。',
          classes: ['BoxSelector', 'BoxSelectionPass']
        },
        {
          name: '建造交互处理',
          desc: '单击/长按放置、破坏、快速挖掘、框选批量、形状放置与摧毁、流体放置、方向旋转、漏斗拾取的全套鼠标交互入口。',
          classes: ['BuildInteractionHandler']
        },
        {
          name: '建造模块与状态',
          desc: '管理选中物品/流体/空手、放置旋转步数、放置与撤销发包，以及单方块/连锁挖掘进度裂纹显示与超时兜底。',
          classes: ['BuildingModule', 'MiningModule']
        },
        {
          name: '放置动画与音效',
          desc: '方块从上方「从天降落」的线框下落动画、破坏碎块飘散特效，以及服务端限流播放的放置音效。',
          classes: ['PlaceAnimationPass', 'BreakEffectPass', 'RtsPlacementSound']
        }
      ]
    },
    {
      id: 'mining',
      name: '挖掘系统',
      tagline: '单块、连锁、区域的远程自动化挖掘',
      features: [
        {
          name: '单方块挖掘状态机',
          desc: '每 tick 累积破坏进度、方块破坏、多方块连带记录、作业队列与上下文临时切换。',
          classes: ['RtsMiningStateMachine']
        },
        {
          name: '连锁挖掘（Ultimine）',
          desc: 'BFS 收集同类型相邻方块，限制硬度比 1.5×，客户端高亮合并预览，逐 tick 处理并同步进度。',
          classes: ['RtsUltimineProcessor', 'RtsUltimineCollector', 'UltiminePreviewPass']
        },
        {
          name: '区域挖掘 / 区域破坏',
          desc: '全框区域挖掘与按指定方块列表的区域摧毁，支持工具耐久挂起恢复与批量节流。',
          classes: ['RtsMiningValidator', 'RtsDestructionBatch', 'RtsMiningServiceImpl']
        },
        {
          name: '挖掘限制与校验',
          desc: '硬限制：连锁 256 / 区域 12 / 破坏 32768 / 每 tick 8 块，并处理工具近损坏判定。',
          classes: ['RtsMiningValidator']
        },
        {
          name: '工具租赁与归还',
          desc: '从背包/链接存储借用挖掘工具（含损坏剩余物），用完自动归还或换新。',
          classes: ['RtsToolLeaseManager', 'RtsToolLease']
        },
        {
          name: '掉落物自动吸收',
          desc: '远程破坏后 1.25 格内的掉落物自动存入链接存储或背包，避免材料散落。',
          classes: ['RtsDropAbsorber']
        },
        {
          name: '挖掘速度与裂纹反馈',
          desc: '计算每 tick 破坏进度、效率附魔加成、消除水下惩罚，并把裂纹阶段实时同步给客户端。',
          classes: ['MiningSpeedCalculator', 'RtsMiningNetworkHelper']
        }
      ]
    },
    {
      id: 'camera',
      name: '相机系统',
      tagline: '上帝视角相机：自由/环绕、导航球与视角跳转',
      features: [
        {
          name: '俯视相机总控',
          desc: 'RTS 相机启停、锚点与作用边界、模式切换，以及服务端状态（位置/朝向）的权威管理。',
          classes: ['CameraModule', 'RtsCameraManager', 'RtsCameraEntity']
        },
        {
          name: '自由相机',
          desc: '中键旋转 + Shift+右键平移 + 滚轮推拉，带阻尼与 EMA 平滑输入。',
          classes: ['FreeCameraMode']
        },
        {
          name: '玩家环绕相机',
          desc: '相机围绕玩家实体旋转并跟随玩家移动，适合观察玩家所在位置。',
          classes: ['PlayerOrbitCameraMode', 'CameraModeController']
        },
        {
          name: '视角状态管理',
          desc: '进入 RTS 时保存并应用第一人称视角，关闭晃动/FOV 效果，退出时完整恢复。',
          classes: ['CameraViewManager']
        },
        {
          name: 'XYZ 轴导航球',
          desc: 'Blender 风格 3D 导航球：点轴端切换轴视角、拖拽球体自由旋转。',
          classes: ['AxisViewGizmo', 'TrackballProjection']
        },
        {
          name: '视角跳转与吸附',
          desc: '点击 XYZ 轴调节器后平滑朝向/轨道跳转动画，支持视角吸附。',
          classes: ['CameraViewSnapController']
        },
        {
          name: '镜像相机实体',
          desc: '本地创建 RtsCameraEntity 镜像并设为当前相机实体，保证服务端视角同步。',
          classes: ['CameraEntitySync', 'RtsCameraEntityRenderer']
        }
      ]
    },
    {
      id: 'movement',
      name: '移动与寻路',
      tagline: '右键移动玩家到目标点',
      features: [
        {
          name: '客户端寻路移动',
          desc: '右键选点、逐 tick 朝目标移动、到达检测、目标高亮淡出、卡住兜底（跳/浮起/升空）。',
          classes: ['RtsClientPathfinding', 'PathfindingModule']
        },
        {
          name: '移动模式注册表',
          desc: '按优先级注册/查找当前移动模式，支持鞘翅 / 飞行 / 游泳 / 匍匐 / 行走 五种内置模式。',
          classes: ['RtsMovementModeRegistry', 'BuiltinMovementModes', 'MovementParams']
        }
      ]
    },
    {
      id: 'ui',
      name: 'UI 界面系统',
      tagline: 'RTS 建造面板：四边面板 + 浮动窗口 + 丰富控件',
      features: [
        {
          name: '主界面 BuilderScreen',
          desc: 'RTS 模式顶层 Screen，组装全部面板（顶/左/右/下 + 浮动窗口层），统一事件路由、缩放、光标与退出清理。',
          classes: ['BuilderScreen', 'BuilderScreenEventRouter', 'RtsUiScaleFrame']
        },
        {
          name: '顶栏',
          desc: 'Logo 菜单、文件按钮、相机模式组、工具按钮组、模式切换器、流体遮挡指示、设置开关。',
          classes: ['TopBarPanel', 'ModeSwitcher', 'CameraModeGroup']
        },
        {
          name: '左面板',
          desc: '选择 / 动作 / 建造-破坏 / 连锁挖掘指示灯 / 形状 五组按钮。',
          classes: ['LeftSidebarPanel', 'ShapeButtonGroup', 'UltimineButtonGroup']
        },
        {
          name: '右面板',
          desc: '工作流进度上嵌层 + 功能调节器下嵌层，可拖分隔条调整高度。',
          classes: ['RightSidebarPanel', 'WorkflowProgress', 'FeatureAdjusters']
        },
        {
          name: '下面板',
          desc: '容器绑定左嵌层 + 物品网格右嵌层 + XYZ 轴视角调节器。',
          classes: ['DownSidebarPanel', 'LeftDownOverlayLayer', 'RightDownOverlayLayer']
        },
        {
          name: '物品网格嵌层',
          desc: '物品/流体网格：搜索、排序、类型过滤、分页、常用记录、点选选材。',
          classes: ['ItemGrid', 'GridState', 'RtsStorageSort']
        },
        {
          name: '设置面板',
          desc: '可拖拽缩放浮动窗口，含渲染 / 个性化 / 操作 / 按键 四折叠区。',
          classes: ['GearMenuPanel', 'RenderingSection', 'PersonalizationSection', 'OperationSection', 'KeybindSection']
        },
        {
          name: '调色盘',
          desc: '色轮 + 灰度条 + HEX/DEC 双模式输入 + 预设色，自定义 5 种线框颜色。',
          classes: ['ColorPickerPanel', 'ColorWheelComponent']
        },
        {
          name: '容器标签面板',
          desc: '网页式多标签容器界面，框选目标按容器一个标签一页展示并点击切换，内嵌原版容器 Screen。',
          classes: ['InteractionPanel', 'PageTabBar', 'ScreenCoordinator', 'ContainerInputForwarder']
        },
        {
          name: '工作流恢复面板',
          desc: '材料清单 + 剩余/冲突统计 + 开始 / 跳过 / 覆盖 三策略按钮。',
          classes: ['ResumeWorkflowPanel', 'ResumeWorkflowState']
        },
        {
          name: '浮动窗口框架',
          desc: '可拖动、缩放、置顶的浮动面板基础，含拖拽性能优化。',
          classes: ['RtsPanel', 'RtsFloatingWindowLayer', 'PanelDragPerformanceOptimizer']
        },
        {
          name: '通用控件库',
          desc: '按钮、开关、滑块、数字输入框、HEX 输入、滚动条、折叠区、tooltip 等全套 UI 组件。',
          classes: ['RtsButton', 'ToggleSwitch', 'ScaleSliderComponent', 'TooltipController']
        }
      ]
    },
    {
      id: 'input',
      name: '输入系统',
      tagline: '可自定义按键 + 层级化输入管线',
      features: [
        {
          name: '按键绑定定义',
          desc: '17 个 RTS 按键：设置、调试叠加、相机切换、旋转/平移、移动玩家、选择/绑定/方向旋转/拾取、撤销、循环模式、平直线、填充模式、连锁挖掘等。',
          classes: ['RtsKeyMappings']
        },
        {
          name: '键位持久化',
          desc: '绑定写入 config/rts_building/keybinds.json，启动加载、运行时保存/重置。',
          classes: ['RtsKeybinds']
        },
        {
          name: '层级化输入管线',
          desc: '按注册顺序驱动各 InputLayer 的 tick 与鼠标/键盘事件，支持容器覆盖层与输入门控。',
          classes: ['InputPipeline', 'InputLayer', 'OverlayLayer', 'ClientInputBridge']
        },
        {
          name: '输入接管 mixin',
          desc: 'RTS 界面打开时阻断原版与第三方模组键位，取消鼠标侧键处理，保证事件只进 BuilderScreen。',
          classes: ['KeyboardInputMixin', 'MouseInputMixin']
        }
      ]
    },
    {
      id: 'render',
      name: '渲染系统',
      tagline: '多类线框/高亮缓冲 + 动画特效 + 无人机',
      features: [
        {
          name: '渲染管线',
          desc: '每帧重置并绘制 5 类缓冲区（线/填充盒/角括号/无深度/屏障），驱动全部 RenderPass。',
          classes: ['RenderPipeline', 'RenderPass']
        },
        {
          name: '作用范围可视反馈',
          desc: 'RTS 区域边界墙、交互目标角括号高亮、漏斗拾取范围球、绑定存储高亮、定位闪烁标记、框选线框、实体选择高亮等。',
          classes: ['BoundaryPass', 'InteractionTargetPass', 'FunnelRangePass', 'LinkedStoragePass', 'LocateMarkerPass', 'EntitySelectHighlightPass']
        },
        {
          name: '建造/挖掘预览',
          desc: '连锁挖掘 BFS 预览、工作流恢复剩余/冲突预览、形状画笔覆盖预览（放置蓝/重叠紫/破坏红/空气灰）。',
          classes: ['UltiminePreviewPass', 'ResumePreviewPass', 'LineBrushRenderPass', 'OutlineEdgeExtractor']
        },
        {
          name: '动画特效',
          desc: '放置动画、破坏特效、无人机光束（建造蓝光/破坏红光三段激光），并把动画锚定到方块实际变化帧。',
          classes: ['RtsEffectStateTracker', 'DroneBeamRenderer', 'GhostRingBuffer']
        },
        {
          name: '光标射线与过滤',
          desc: '鼠标射线换算（支持按住 F 流体遮挡、128 格距离），以及按锚点半径过滤作用位置。',
          classes: ['CursorRaycaster', 'ActionRadiusFilter']
        },
        {
          name: '无人机实体渲染',
          desc: 'Blockbench 无人机模型 + 悬停浮动 + 螺旋桨旋转 + 属主隐藏，非 RTS 玩家也能看到光束。',
          classes: ['RtsDroneRenderer', 'rts_drone', 'rts_droneAnimation']
        }
      ]
    },
    {
      id: 'blueprint',
      name: '蓝图系统',
      tagline: '导入多种格式蓝图并逐 tick 自动化建造',
      features: [
        {
          name: '多种蓝图格式',
          desc: '支持原版结构 (.nbt) / Sponge Schematic (.schem) / Litematica (.litematic) / Building Gadgets (.json) 四种格式，按扩展名自动识别。',
          classes: ['BlueprintFormat', 'BlueprintReaders']
        },
        {
          name: '蓝图读取与写出',
          desc: '四种格式解析器 + 世界方块捕获写出（跳过空气/结构虚空/地板层，上限 20000 方块）。',
          classes: ['VanillaStructureNbtReader', 'SpongeSchemReader', 'LitematicReader', 'BuildingGadgetsTemplateReader', 'BlueprintWriters']
        },
        {
          name: '变换与替换规则',
          desc: '绕 Y/X/Z 三轴 90° 倍数旋转、居中偏移、方块状态 Direction/Axis 属性旋转，以及软替换规则（草/花/藤蔓/雪等）。',
          classes: ['BlueprintTransform', 'BlueprintReplaceRules']
        },
        {
          name: '材料统计',
          desc: '自动计算所需材料表，递归扫描 AE2 线缆/总线 NBT 中的材料字符串，回退方块 asItem()。',
          classes: ['RtsBlueprint', 'RtsBlueprintBlock']
        },
        {
          name: '逐 tick 放置执行',
          desc: '放置计划预计算缓存，每 tick 动态限量放置，材料不足即刻挂起并持久化。',
          classes: ['BlockPlacementPlanner', 'BlueprintTickPipe', 'BlueprintExecutePipe']
        },
        {
          name: '恢复与冲突扫描',
          desc: '服务端重启后自动恢复蓝图工作流，扫描剩余/冲突方块并聚合材料需求清单。',
          classes: ['RtsBlueprintJobService', 'BlueprintPersistence', 'BlueprintRestoreHandler']
        }
      ]
    },
    {
      id: 'storage',
      name: '存储系统',
      tagline: '链接箱子/背包/网络，远程浏览与调度物品',
      features: [
        {
          name: '链接存储',
          desc: '框选/右键链接存储方块（箱子、背包、宿主模组网络），支持优先级、提取/双向模式、断链与背包 UUID 迁移。',
          classes: ['RtsLinkedStorageResolver', 'RtsLinkedStorageBindingService', 'LinkedStorageInfo']
        },
        {
          name: '聚合存储与缓存',
          desc: '模拟 AE2 NetworkStorage 的优先级树聚合全部 handler，两级缓存快照差分，跨网络长计数。',
          classes: ['RtsAggregateStorage', 'RtsHandlerCache', 'RtsLinkedHandlerViews']
        },
        {
          name: '自适应刷新调度',
          desc: 'AE2 式自适应节流（繁忙加速到每 tick、空闲降速），避免高频扫描拖垮性能。',
          classes: ['RtsStorageTickService']
        },
        {
          name: '物品传输服务',
          desc: '归还/丢弃/导入/拿去/快速转移/填满背包、合成格 Shift 多轮补料，溢出提示与菜单同步。',
          classes: ['RtsTransferServiceImpl', 'RtsTransferInserter', 'RtsTransferExtractor', 'RtsCraftGridSupport']
        },
        {
          name: '流体系统',
          desc: '会话内流体缓冲、链接流体填充/排空、容器清空、世界流体放置规则（容器/汽化/替换）。',
          classes: ['RtsStorageFluids', 'FluidTransferGate', 'RtsFluidBufferService', 'RtsFluidWorldPlacer']
        },
        {
          name: '页面构建与搜索',
          desc: '分页/搜索/排序/分类（all/mod|ns/tab|ns|key），LRU 页面缓存，内置拼音字典支持 CJK 拼音/首字母模糊匹配。',
          classes: ['RtsPageCore', 'RtsPageSharedHelpers', 'RtsPinyinSearch', 'RtsStorageSort']
        },
        {
          name: 'UI 记忆与最近条目',
          desc: '最近使用条目队列、9 个快捷槽、8 个 GUI 绑定槽位，会话内跨页面保持。',
          classes: ['RtsUiMemory', 'RtsStorageBindings', 'RtsStorageRecentEntries', 'RtsQuickSlotBindingService']
        }
      ]
    },
    {
      id: 'workflow',
      name: '工作流引擎',
      tagline: '服务端权威的多工作流并行执行与恢复',
      features: [
        {
          name: '工作流核心',
          desc: '按「玩家 × 维度」管理全部工作流，9 种类型（单挖/连锁/区域挖掘/形状破坏/单放/批量/快速建造/蓝图/停止），状态含总数/完成/失败/挂起/缺失物品。',
          classes: ['RtsWorkflowEngine', 'RtsWorkflowType', 'RtsWorkflowStatus']
        },
        {
          name: '优先级与槽位管理',
          desc: 'LOW/NORMAL/HIGH/CRITICAL 四级优先级可抢占，每玩家最多 8 槽位按优先级+FIFO 排序，超时僵尸清理。',
          classes: ['RtsWorkflowPriority', 'RtsWorkflowSlotManager']
        },
        {
          name: '进度同步与批量发包',
          desc: '同 tick 多次进度更新只发一个全量包，空槽位发 idle 包，进度文本/填充宽度/挂起标记计算。',
          classes: ['RtsWorkflowSyncService', 'RtsWorkflowProgressProcessor', 'RtsWorkflowProgressBatchPayload']
        },
        {
          name: '持久化与恢复',
          desc: '工作流条目内存↔存档读写，服务端重启后自动恢复蓝图/放置/破坏管道。',
          classes: ['WorkflowPersistenceService', 'RtsWorkflowStore']
        },
        {
          name: '管道/管线架构',
          desc: '按工作流类型注册/执行管道，fail-fast 语义 + 失败自动回滚（还工具+取消条目），支持逐 tick 管道。',
          classes: ['WorkflowPipeline', 'PipelineRegistry', 'TickablePipe', 'ActivePipeline', 'RtsPipelineRegistration']
        }
      ]
    },
    {
      id: 'history',
      name: '历史与撤销',
      tagline: '服务端权威的放置/破坏撤销',
      features: [
        {
          name: '撤销/重做栈',
          desc: '每玩家撤销栈：10 分钟过期清理、1000 条上限、200ms 撤销冷却、单次 64 方块预算。',
          classes: ['ServerHistoryManager', 'RtsHistoryConstants']
        },
        {
          name: '历史执行器',
          desc: '放置批次→逐方块破坏、破坏批次→恢复放置；创造恢复 NBT、生存防刷物品（背包+存储扣料）、跳过占用位、退还容器内容物。',
          classes: ['HistoryExecutor', 'HistoryEntry', 'HistoryBlockRecord']
        },
        {
          name: '已放置方块追踪',
          desc: 'SavedData 记录模组放置的方块位置，供撤销安全破坏与恢复判定。',
          classes: ['PlacedBlockTrackerData', 'RtsBlockTrackingEvents']
        }
      ]
    },
    {
      id: 'network',
      name: '网络协议',
      tagline: '统一 C2S 通道 + 按域分派的 S2C 消息',
      features: [
        {
          name: '统一 C2S 动作通道',
          desc: '单通道 NBT 参数承载全部客户端动作，ordinal 越界防恶意包；高频相机姿态走专用免 NBT payload。',
          classes: ['C2SAction', 'ServerActionHandler', 'ActionType', 'C2SCameraPosePayload']
        },
        {
          name: 'S2C 按域分派',
          desc: '统一分派桥按域分发：相机、存储、建造、反馈、恢复、蓝图六类回包，杜绝专用服务器加载客户端类。',
          classes: ['ClientPayloadDispatcher', 'RtsClientNetworkHandlers']
        },
        {
          name: '协议注册与安全',
          desc: '全部 payload 集中注册，含交互源/链接/流体源类型、位置上限 32768、蓝图文件大小上限等安全常量。',
          classes: ['RtsPayloadRegistrar', 'NetworkConstants']
        }
      ]
    },
    {
      id: 'content',
      name: '注册内容',
      tagline: '方块、物品、实体、创造标签页',
      features: [
        {
          name: 'RTS 终端物品',
          desc: 'rts_terminal 开启 RTS 模式的核心物品，带 terminal_uuid/terminal_lit 数据组件；接入能源 addon 后为能量型终端。',
          classes: ['RtsItems', 'RtsTerminalItem']
        },
        {
          name: '相机实体',
          desc: 'rts_camera：无物理无碰撞，snapTo/snapInterpolated 插值、轨道模式插值参数，追踪距离 128。',
          classes: ['RtsEntities', 'RtsCameraEntity']
        },
        {
          name: '无人机实体',
          desc: 'rts_drone：悬浮展示单位，飞向目标、飞行倾角模拟、螺旋桨动画、每 tick 动画状态广播。',
          classes: ['RtsDroneEntity']
        },
        {
          name: '创造标签页',
          desc: 'RTSBUILDING_TAB 主标签页自动收集全部标记物品/方块。',
          classes: ['RtsCreativeTabs']
        }
      ]
    },
    {
      id: 'api',
      name: '公开 API',
      tagline: 'rtsbuilding-api：供其他模组与 addon 调用的能力',
      features: [
        {
          name: 'RtsAPI 门面',
          desc: '全局单例聚合 9 个子 API：存储查询 / 蓝图 / 放置 / 交互 / 挖掘 / 传输 / 流体 / 绑定 / 会话 / 能量，线程安全。',
          classes: ['RtsAPI', 'RtsAPIImpl']
        },
        {
          name: '远程建造 API',
          desc: '远程放置（含旋转/强制/跳过占用）、批量放置与进度查询、单块/连锁/区域挖掘、远程右键交互、远程旋转/拆除。',
          classes: ['RtsPlacementAPI', 'RtsMiningAPI', 'RtsInteractionAPI']
        },
        {
          name: '存储与传输 API',
          desc: '按谓词统计链接存储物品、材料统计/提取/退款、物品转移、流体操作、存储绑定管理。',
          classes: ['RtsStorageQueryAPI', 'RtsBlueprintAPI', 'RtsTransferAPI', 'RtsFluidAPI', 'RtsBindingsAPI']
        },
        {
          name: '领地保护系统',
          desc: '第三方领地插件（FTB Chunks / GriefPrevention / Lands）实现 ProtectionCheck 注册，任一 DENY 即拒绝远程操作。',
          classes: ['ProtectionRegistry', 'ProtectionCheck']
        },
        {
          name: '兼容扩展点',
          desc: '注册存储/流体网络提供方、背包提供方、图标解析器四类兼容，附扩展 handler 接口（任意槽插入/直取/快照刷新）。',
          classes: ['RtsCompatRegistry', 'RtsStorageNetworkProvider', 'RtsFluidNetworkProvider', 'RtsBackpackProvider', 'RtsIconResolver']
        },
        {
          name: '能量 API（仿 Mekanism）',
          desc: '能量网格创建/查询/插拔 FE、多容器能量处理器、Action/AutomationType 双语义容器、原子「检查并扣费」consume。',
          classes: ['RtsEnergyAPI', 'IEnergyHandler', 'IEnergyContainer', 'Action', 'AutomationType']
        },
        {
          name: '建造模式枚举',
          desc: '定义 RTS 模式 8 种玩家操作模式：OFF / 框选 / 链接存储 / 漏斗 / 交互 / 旋转 / 建造 / 蓝图，持久化到玩家 NBT。',
          classes: ['BuilderMode']
        }
      ]
    },
    {
      id: 'energy',
      name: '能源系统',
      tagline: '内置 addon（rtsbuilding_technologized）：热能发电 + 储能 + 建造计费',
      features: [
        {
          name: '热能发电机',
          desc: '烧岩浆产电：60 FE/tick，每 20 tick 消耗 1 mB 岩浆，内部缓冲 20,000 FE，支持岩浆桶装填/空桶抽取。',
          classes: ['RtsThermalGeneratorBlock', 'RtsThermalGeneratorBlockEntity']
        },
        {
          name: '储能单元',
          desc: '纯存储方块，单块 400 万 FE 容量，无 ticker；能量方块归属放置者。',
          classes: ['RtsEnergyBankBlock', 'RtsEnergyBankBlockEntity']
        },
        {
          name: '玩家能量网格',
          desc: '按「维度+坐标」登记能量节点、按玩家聚合个人网格（无传统线缆），节点破坏/卸载自动扣除缓冲。',
          classes: ['RtsEnergyNetworkManager', 'RtsEnergyNode', 'RtsEnergyBlockEntity']
        },
        {
          name: '建造能量计费',
          desc: '每次远程放置扣 50 FE（可配置）；无能量方块的玩家永不收费（经济自愿加入），扣费尽力而为、绝不阻塞放置。',
          classes: ['RtsEnergyCostService', 'RtsBuildEnergy']
        },
        {
          name: '终端能量',
          desc: '终端能量数据组件：容量 1,000,000 FE、充放 5,000 FE/t、开启 RTS 模式耗 500 FE、Mekanism 风格亮绿能量条。',
          classes: ['RtsTerminalEnergyImpl', 'RtsTerminalEnergy']
        }
      ]
    },
    {
      id: 'compat',
      name: '宿主模组集成',
      tagline: '内置 4 个 addon：接入主流存储/背包模组',
      features: [
        {
          name: 'AE2 集成',
          desc: '反射接入 AE2 存储网格：虚拟槽位视图、10 tick 缓存节流、任意槽插入/提取、long 精度报告数量、流体收集、图标解析。',
          classes: ['RtsAe2Addon', 'Ae2StorageProvider', 'Ae2NetworkItemHandler', 'Ae2FluidProvider']
        },
        {
          name: 'Refined Storage 集成',
          desc: '反射接入 RS2 网络存储：存储视图虚拟槽位、任意槽插入/提取、10 tick 缓存节流。',
          classes: ['RtsRefinedStorageAddon', 'RsStorageProvider', 'RsNetworkItemHandler']
        },
        {
          name: 'BeyondDimensions 集成',
          desc: '编译期依赖 BD API，接入玩家主维度网络；仅目标方块确为网络成员才返回网络句柄，避免重复接入。',
          classes: ['RtsBeyondDimensionsAddon', 'BdStorageProvider', 'BdDirectItemHandler', 'BdFluidProvider']
        },
        {
          name: 'Sophisticated Backpacks 集成',
          desc: '识别背包方块实体、按 UUID 打开背包返回 IItemHandler，反射调用 openBackpack 优先、背包物品匹配回退。',
          classes: ['RtsSophisticatedBackpacksAddon', 'BackpackProvider']
        }
      ]
    },
    {
      id: 'infra',
      name: '服务端基础设施',
      tagline: '会话、持久化、相机、漏斗、反馈与配置',
      features: [
        {
          name: '服务定位与聚合',
          desc: 'ServiceLoader 自动发现全部服务实现，RtsServer 聚合访问器统一提供。',
          classes: ['RtsServer', 'RtsService']
        },
        {
          name: '会话服务',
          desc: '懒加载、按玩家持久化 DataCluster、启用/禁用资源清理、登出完整清理。',
          classes: ['RtsSessionServiceImpl', 'RtsStorageSession']
        },
        {
          name: '数据持久化',
          desc: 'DataCluster 内存优先、按组件独立脏标记、增量刷盘；SaveScheduler 统一生命周期、200 tick 批量刷盘、旧文件迁移。',
          classes: ['DataCluster', 'DataComponent', 'SaveScheduler', 'RtsAtomicNbtStore']
        },
        {
          name: '相机管理',
          desc: 'RTS 相机会话生命周期、锚点跟随玩家、动作范围 AABB 校验（32 格）、客户端权威姿态钳位、孤儿相机清理。',
          classes: ['RtsCameraManager', 'RtsCameraEntityHelper']
        },
        {
          name: '漏斗（物品拾取）',
          desc: '球心持续吸取（半径 1~5 可调、每 tick 16 个）+ 框选一次性吸取，handler 签名缓存、聚合零拷贝吸收。',
          classes: ['RtsFunnelService', 'FunnelRangePass']
        },
        {
          name: '伤害反馈',
          desc: 'RTS 相机模式下追踪血量下降并发送 HUD 伤害反馈。',
          classes: ['RtsDamageFeedbackManager', 'S2CRtsDamageFeedbackPayload']
        },
        {
          name: '远程菜单',
          desc: '反射替换 stillValid/ContainerLevelAccess 实现远程开箱，第三方容器（Iron Furnaces 等）同样保活。',
          classes: ['RtsRemoteMenuService', 'ChestMenuMixin', 'ModdedRemoteStillValidMixin']
        },
        {
          name: '配置系统',
          desc: '主配置（蓝图开关/上限、预览动画、能量插件开关与 FE 成本）+ 性能配置（各类渲染开关、渲染距离剔除）。',
          classes: ['Config', 'PerformanceConfig']
        }
      ]
    },
    {
      id: 'framework',
      name: 'UI 渲染框架',
      tagline: '自定义矢量渲染、主题、动画与字体',
      features: [
        {
          name: 'SDF 矢量渲染',
          desc: '圆角矩形、按钮背景、输入框、进度条等全部矢量 UI 绘制，配自定义 shader（rounded_rect/chevron/textured/reset_icon）。',
          classes: ['SdfRenderer', 'RtsShaders', 'ShaderState']
        },
        {
          name: '主题系统',
          desc: '深浅色主题切换与监听，深色调色板从 dark.png 纹理读取基础色。',
          classes: ['ThemeManager', 'ThemeListener', 'DarkUiPalette']
        },
        {
          name: '九宫格与精灵',
          desc: '九宫格拼接与主题化贴图区域、纹理过滤状态管理、物品图标统一绘制（清理深度污染防穿透）。',
          classes: ['SpriteRenderer', 'NineSliceTiler', 'NineSliceRegion', 'GuiItemRenderer']
        },
        {
          name: '动画系统',
          desc: 'Easing 缓动曲线库、悬浮动画、颜色过渡、交叉淡出渲染与混合状态管理。',
          classes: ['Easing', 'AnimFloat', 'ColorAnimation', 'CrossFadeRenderer']
        },
        {
          name: '文本与数字',
          desc: '带缩放/居中文本渲染、字形渲染增强、紧凑数字格式化（K/M/B）。',
          classes: ['TextRenderer', 'FontRenderEnhancer', 'NumberFormatter']
        }
      ]
    }
  ]
}

/**
 * 统计功能总数（各模块 feature 数量合计）。
 * @returns {number}
 */
export function totalFeatureCount() {
  return featuresCatalog.modules.reduce((sum, m) => sum + m.features.length, 0)
}

/**
 * 统计关键类引用总数（去重）。
 * @returns {number}
 */
export function totalClassCount() {
  const set = new Set()
  for (const m of featuresCatalog.modules) {
    for (const f of m.features) {
      for (const c of f.classes || []) set.add(c)
    }
  }
  return set.size
}

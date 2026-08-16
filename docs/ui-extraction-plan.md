# UI 前置模组提取 · 迁移计划

> 目标：将 RTS Building 的 UI 层（渲染/动画/主题/窗口框架/控件）提取为独立前置 mod `rtsbuilding_ui`
> 基包：`com.rtsbuilding.rtsui`
> 状态：进行中

## 迁移清单

### 1. render 包（17 + 4 model = 21 类）→ com.rtsbuilding.rtsui.render
BlendScope / CrossFadeRenderer / DarkUiPalette / FilterState / FontRenderEnhancer /
GuiItemRenderer / GuiRenderTypes / PanelDragPerformanceOptimizer / RtsShaders /
SdfRenderer / ShaderState / SpriteRenderer / TextRenderer +
model/NineSliceRegion / NineSliceTiler / SpriteRegion / TextureInfo

### 2. animate 包（3 类）→ com.rtsbuilding.rtsui.animate
AnimFloat / ColorAnimation / Easing

### 3. theme 包（2 类）→ com.rtsbuilding.rtsui.theme
ThemeListener / ThemeManager

### 4. state 包（3 类）→ com.rtsbuilding.rtsui.state
FeatureAdjusterState（需解耦网络）/ HoverSuppression / TooltipController

### 5. window 包（17 类）→ com.rtsbuilding.rtsui.window
RtsPanelApi / AbstractButtonGroup / CollapsibleSection / EdgeResizeHandler / ScrollBar /
SettingsSection / DownOverlayLayer / OverlayContext / BasePopup / PanelInputHandler /
RtsFloatingWindowLayer / RtsPanel / WindowFrameRenderer / PanelDragHandler /
PanelResizeHandler / PanelBounds / ResizeEdge

### 6. component 包（8 类）→ com.rtsbuilding.rtsui.component
ColorPickerButton / HexInputComponent / NumericInputBox / ResetButton / RtsButton /
ScaleSliderComponent / TextInputBox / ToggleSwitch

### 7. color 包（3 类）→ com.rtsbuilding.rtsui.component.color
ColorPickerPanel / ColorWheelComponent / GrayscaleBarComponent

### 8. 资源
shaders/core（9 json + 18 fsh/vsh）→ assets/rtsbuilding_ui/shaders/core
textures/gui/base（dark.png 等）→ assets/rtsbuilding_ui/textures/gui/base

## 解耦点
1. BuilderScreen → RtsPanelHost 接口（window 包定义，BuilderScreen 实现）
2. FeatureAdjusterState → 网络调用移回业务
3. AbstractButtonGroup → TopBarLayoutHelper
4. 资源命名空间 rtsbuilding → rtsbuilding_ui
5. shader 注册迁移到 UI mod 的 RegisterShadersEvent

## 主模块改造
- 100+ import 更新 com.rtsbuilding.rtsbuilding.client.util.* → com.rtsbuilding.rtsui.*
- 业务面板保留在 main（gear/topbar/leftbar/rightbar/downbar/blueprint/plugin/interaction）
- 删 main 中已迁移的 UI 源文件

## 打包
- rtsbuilding-ui 并入主 JAR（mods{}+jar{from}），或独立 JAR
- neoforge.mods.toml 声明 rtsbuilding_ui + 依赖

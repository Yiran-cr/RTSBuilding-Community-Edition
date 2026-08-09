# RTS Building: Build From Above

Minecraft RTS-style top-down building mod. NeoForge 1.21.1 / Forge 1.20.1 (branch `forge-1.20.1`). Java 21.

## 语言约定

- 与用户交流、回复、思考均使用简体中文。

## Build / Run

- Build (Windows): `.\gradlew.bat build --no-daemon --no-configuration-cache`
- Linux/macOS: `./gradlew build --no-daemon --no-configuration-cache`
- Gradle daemon/parallel/config-cache are enabled by default in `gradle.properties`.

## Module Structure (Gradle multi-module)

- `rtsbuilding-common` — shared gameplay logic (packages: `common`, `core`, `server`, `util`), no loader-specific code.
- `rtsbuilding-api` — public API module for addons / other mods to depend on.
- `rtsbuilding-main` — the main mod's NeoForge platform module. Loader-specific code in `client`, `network`, `platform`, `mixin`, `compat`, `server`, `common`.
- `rtsbuilding-technologized` — built-in addon mod (`rtsbuilding_technologized`): energy & power system. Separate project, packaged inside the main mod JAR.
- `rtsaddon-ae2/`, `rtsaddon-beyonddimensions/`, `rtsaddon-refinedstorage/`, `rtsaddon-sophisticatedbackpacks/` — built-in addon mods (host-mod integrations: AE2, Refined Storage, BeyondDimensions, Sophisticated Backpacks). Each is a separate project at the repo root, packaged inside the main mod JAR.

## Conventions

- Base package: `com.rtsbuilding.rtsbuilding`.
- Mod ID: `rtsbuilding` (see `mod_id` in `gradle.properties`); built-in energy addon uses `rtsbuilding_technologized`.
- Parchment mappings for 1.21.1: `parchment_mappings_version=2024.11.17`, NeoForge `21.1.219`.
- Keep logic out of loader-specific modules; put shared behavior in `common`, expose cross-mod hooks via `rtsbuilding-api`.
- The main mod must not compile-reference the built-in addon modules (`rtsbuilding-technologized`, `rtsaddon-*`) — they inject their services via bridges in `common`; the main mod JAR merges all built-in addon outputs and declares them in `neoforge.mods.toml`.
- New host-mod integrations go under `rtsaddon-<host>/` at the repo root, must be registered in `settings.gradle`, merged into the main JAR, and declared in `rtsbuilding-main/src/main/templates/META-INF/neoforge.mods.toml`.

## Do not touch

- `build/`, `rtsbuilding-main/run/` (Minecraft dev run dir), generated sources.
- `docs/app/node_modules/`, `docs/dist/` (docs 前端构建产物，由 `npm run build` 生成).

## 逻辑链路检查（"XX链路检查" 指令）

当用户说「XX 逻辑的链路检查」/「走一遍 XX 链路」/「查询 XX 逻辑」时，执行以下固定流程：

1. **定位**：用 `grep`/`glob` 找出该逻辑涉及的全部文件与调用点，梳理「客户端 → 网络包 → 服务端 → 回包 → 客户端」的完整数据流。
2. **逐节点审查**：读取每个关键调用点，重点排查三类问题：
   - **边界问题**：空值/越界/维度、跨端状态不同步（如服务端依赖客户端上报的坐标）、时序竞态、限流缺失。
   - **冗余代码**：死代码（无调用方的方法）、重复实现、过时注释（引用已删除的类/方法）。
   - **断点**：链路断裂——调用缺失、包未注册、mixin 未生效、返回路径提前 return 不通知客户端。
3. **修复**：先向用户报告问题清单再动手；修复后必须 `.\gradlew.bat :rtsbuilding-main:compileJava --no-daemon --no-configuration-cache` 编译 + `:rtsbuilding-main:test` 测试通过。
4. **产出 JSON 报告**：按 `docs/schemas/logic-review.schema.json` 的结构写 `<链路名>.json` 存入 `docs/reports/`（如 `docs/reports/sound-architecture.json`）。报告数据由 `docs/app`（Vue3 SPA）统一渲染，无需手写 HTML。可选：修改 JSON 后运行 `npm run build`（在 `docs/app/`）刷新 `docs/dist/` 静态站点。

### JSON 报告格式规范（统一）

- 遵循 `docs/schemas/logic-review.schema.json`：必填字段 `id / title / subtitle / tags / classes / sections / issues / boundaries`。
- `classes`：核心类职责，对象数组 `{ name, path, desc }`。
- `sections`：链路小节，`type` 取值 `table`（`headers`+`rows`）/ `flow`（`nodes`，`side`=`srv`|`cli`）/ `note` / `cards`。
- `issues`：问题卡片，`status` 取值 `fixed`（前端渲染为「已修复」绿徽章）/ `kept`（「保留」黄徽章）。
- `boundaries`：边界与设计说明字符串数组。
- 富文本字段（`desc` / `why` / `note` / 表格单元格 / 节点 `text`）可直接内联 `<code>`、`<b>`、`<strong>`，前端 `v-html` 渲染；禁止脚本。
- 语言：简体中文。

### 前端预览

- 开发：`cd docs/app && npm install && npm run dev`（http://localhost:5173）。
- 构建：`cd docs/app && npm run build`（产物到 `docs/dist/`，file:// 直接打开 `docs/dist/index.html` 即可）。
- 功能：报告列表 → 搜索（标题/类/路径/问题）→ 状态筛选（含已修复/保留）→ 详情页（类职责表 / 链路小节 / 问题卡片 / 边界说明）。

## 每日修改总结（记忆）

每次问答结束时，若本次会话有代码修改，**追加**一条总结到当天日期的文件 `docs/change-log/YYYY-MM-DD.md`（日期取当天，按 `2026-08-10` 格式）。

- **追加而非覆盖**：当天多次修改在同一文件内逐条追加；跨天则新建当天文件。
- **精简**：每条只写 1~3 行，格式为 `- [HH:MM] 主题：做了什么（涉及的关键文件/类，一句话）`。不做长篇描述，不列详细代码。
- **必须覆盖**：实际改动了哪些代码、新增/删除的文件、行为变更。纯对话/查询（无代码改动）不写。
- 若文件或 `docs/change-log/` 目录不存在，先创建。
- 不要在总结中写入 secrets、账号等信息。



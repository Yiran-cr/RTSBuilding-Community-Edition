package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.ActionRadiusFilter;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.OutlineEdgeExtractor;
import com.rtsbuilding.rtsbuilding.client.render.util.UltimineBlockMerger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 线模式建造预览：以轮廓边线框样式高亮当前形状覆盖的方块。
 * 仅在 {@link LineBrushSelector} 处于拖拽状态时渲染，颜色可通过渲染设置面板调整。
 *
 * <p><b>性能优化：</b>位置可达 {@code NetworkConstants.MAX_POSITIONS = 32768}，
 * 逐方块渲染完整角支架会达到千万级顶点/帧。本 pass 先把位置按颜色分组，
 * 再用 {@link OutlineEdgeExtractor} 提取各组的<strong>外轮廓边</strong>（含共线合并）
 * 渲染，顶点量从 O(n) 降到 O(轮廓边数)。</p>
 *
 * <p><b>放置侧：</b>空气（可放置）位置蓝色，与已有方块重叠位置紫色提示冲突。</p>
 * <p><b>破坏侧：</b>空气位置灰色（形状覆盖但无目标），可破坏方块红色高亮，
 * 不可破坏方块（破坏速度 &lt; 0，如基岩）紫色提示无法破坏。</p>
 */
public final class LineBrushRenderPass implements RenderPass {

    /** 线模式预览线框颜色（ARGB，默认蓝色），可在渲染设置面板中调整。 */
    public static int lineBrushColor = 0xFF3388FF;

    /** 线模式中与世界中已有方块重叠时的线框颜色（ARGB，默认紫色），可在渲染设置面板中调整。 */
    public static int lineBrushOverlapColor = 0xFFAA00FF;

    /** 破坏侧画笔预览线框颜色（ARGB，默认红色），可在渲染设置面板中调整。 */
    public static int lineBrushBreakColor = 0xFFFF4455;

    /** 破坏侧画笔预览中空气位置的颜色（ARGB，默认灰色），表示形状覆盖但无目标方块。 */
    public static int lineBrushAirColor = 0xFF888888;

    private static final CornerBracketRenderer.Rgb color = new CornerBracketRenderer.Rgb();

    private final LineBrushSelector brush;

    public LineBrushRenderPass(LineBrushSelector brush) {
        this.brush = brush;
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen
                && brush.isActive()
                && brush.getStart() != null
                // 球形状几何只依赖球心+半径，不要求 hover 非空；其余形状需要悬停点作端点/半径
                && (brush.isSphereActive() || brush.getHover() != null);
    }

    @Override
    public void render(Minecraft mc, RenderPass.BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;
        if (!(mc.screen instanceof com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen screen)) return;
        // XYZ 轴调节器拖拽期间跳过：光标隐藏后悬停点固定，视角旋转会让线框预览跳动
        if (screen.isAnyDragActive()) return;
        Vec3 cameraPos = mc.getCameraEntity().getEyePosition(partialTick);

        // 当前「形状 × 阶段」应渲染的方块列表（圆/球在选点阶段即渲染完整形状，
        // 墙/面/体仅在扩展阶段渲染扩展结果，其余阶段渲染走向线）
        List<BlockPos> line = brush.computePositions();
        if (line.isEmpty()) return;

        // 任一确认阶段（线微调 / 宽度 / 高度 / 球半径）：线框闪烁提示玩家再次确认
        float flicker;
        if (brush.isAdjusting() || brush.isWidthAdjusting() || brush.isHeightAdjusting()
                || brush.isRadiusAdjusting()) {
            double phase = Math.sin(System.currentTimeMillis() / 160.0D);
            flicker = 0.30F + 0.70F * (float) (0.5D + 0.5D * phase);
        } else {
            flicker = 1.0F;
        }
        float depthAlpha = 1.0F; // 深度测试线框 100% 完全不透明（不随闪烁调制）
        float noDepthAlpha = CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA * flicker;

        // 按颜色分组：破坏侧红/紫/灰、放置侧蓝/紫，每组独立提取轮廓并着色，
        // 保留逐位置的状态区分同时大幅降低顶点量
        Map<Integer, Set<BlockPos>> groups = new LinkedHashMap<>();
        boolean breakMode = brush.isBreakActive();
        for (BlockPos p : line) {
            // 动作半径过滤：与服务端 canAccessWorldTarget 对齐，避免预览超出半径的方块"点了没反应"
            if (!ActionRadiusFilter.isWithinActionRadius(p)) continue;
            BlockState state = mc.level.getBlockState(p);
            int argb;
            if (breakMode) {
                // 破坏侧：空气灰色、不可破坏（破坏速度 < 0）紫色、可破坏红色
                argb = state.isAir() ? lineBrushAirColor
                        : (state.getDestroySpeed(mc.level, p) < 0.0F
                        ? lineBrushOverlapColor : lineBrushBreakColor);
            } else {
                // 放置侧：空气（可放置）蓝色、与已有方块重叠紫色
                argb = state.isAir() ? lineBrushColor : lineBrushOverlapColor;
            }
            groups.computeIfAbsent(argb, k -> new HashSet<>()).add(p);
        }
        if (groups.isEmpty()) return;

        // 每组分颜色提取外轮廓边并渲染（深度层 + 穿透层）
        for (Map.Entry<Integer, Set<BlockPos>> entry : groups.entrySet()) {
            List<UltimineBlockMerger.EdgeLine> edges =
                    OutlineEdgeExtractor.extractEdges(entry.getValue());
            if (edges.isEmpty()) continue;
            color.update(entry.getKey());
            float r = color.r, g = color.g, b = color.b;
            // 线框粗度随距离缩放：用组中心到相机的距离
            double distance = groupDistance(entry.getValue(), cameraPos);

            CornerBracketRenderer.renderEdges(poseStack, alloc.brackets(), edges,
                    r, g, b, depthAlpha, distance);
            if (BoxSelectionPass.depthTestEnabled) {
                CornerBracketRenderer.renderEdges(poseStack, alloc.noDepth(), edges,
                        r, g, b, noDepthAlpha, distance);
            }
        }
    }

    /** 组内方块的平均位置到相机的距离，用于线框粗度缩放。 */
    private static double groupDistance(Set<BlockPos> group, Vec3 cameraPos) {
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : group) {
            cx += p.getX() + 0.5D;
            cy += p.getY() + 0.5D;
            cz += p.getZ() + 0.5D;
        }
        int n = group.size();
        return n == 0 ? 0 : cameraPos.distanceTo(new Vec3(cx / n, cy / n, cz / n));
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8;
    }
}


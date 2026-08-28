package com.rtsbuilding.rtsbuilding.planetrise.client.link;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.planetrise.block.PowerTowerBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.ThermalGeneratorBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.WindGeneratorBlock;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.PowerTowerBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.block.entity.WindGeneratorBlockEntity;
import com.rtsbuilding.rtsbuilding.planetrise.client.power.PowerRangeVisualStore;
import com.rtsbuilding.rtsbuilding.planetrise.power.IPowerGridNode;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerRole;
import com.rtsbuilding.rtsbuilding.planetrise.power.PowerScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 能量节点<b>放置连接虚线渲染器</b>（客户端，参照 power_system_design.md 视觉反馈）。
 * <p>
 * 当玩家<b>将要放置</b>一个具有连接能力（可组网）的能量设备（输电塔 / 热能发电机 / 风力发电机）时，
 * 在目标放置位置与附近<b>已放置的其他能量节点</b>之间绘制<b>虚线</b>，提示该新设备能连到哪些节点：
 * <ul>
 *   <li><b>蓝色虚线</b>——距离 &le; 新设备的链路范围，放置后可组网连接；</li>
 *   <li><b>红色虚线</b>——链路范围 &lt; 距离 &le; 1.5 × 链路范围，超出可连接范围（警告）；</li>
 *   <li><b>不显示</b>——距离 &gt; 1.5 × 链路范围。</li>
 * </ul>
 * 触发场景（两种模式统一入口，表现一致）：
 * <ul>
 *   <li><b>RTS 模式</b>（{@link BuilderScreen} 打开）：建造模式下选中的建造物品为能量设备；</li>
 *   <li><b>手动放置</b>：玩家主手/副手持有能量设备。</li>
 * </ul>
 * 目标节点集合复用 {@link PowerRangeVisualStore}（客户端已在装载时注册所有可组网节点），
 * 与「范围圈视觉反馈」共用同一份节点数据源，保证链路范围判断与运行时组网一致。
 * <p>
 * 虚线端点为各节点<b>模型最顶部中心</b>（输电塔连到塔顶、风机连到塔顶、单格方块连到方块顶），
 * 而非固定单格方块顶——保证对跨格塔类设备的视觉贴合。本渲染器挂在
 * {@link com.rtsbuilding.rtsbuilding.planetrise.client.EnergyClient#onRenderLevel} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，自建 {@link RenderType#lines()} 缓冲并立即绘制，
 * 与 {@link com.rtsbuilding.rtsbuilding.planetrise.client.power.PowerRangeOverlayRenderer} 的
 * Tesselator 绘制方式一致。
 */
public final class RtsGridLinkPreviewRenderer {

    /** 能量设备物品注册 ID：无线输电塔。 */
    private static final ResourceLocation POWER_TOWER =
            ResourceLocation.tryParse("rtsbuilding_planetrise:power_tower");
    /** 能量设备物品注册 ID：热能发电机。 */
    private static final ResourceLocation THERMAL_GENERATOR =
            ResourceLocation.tryParse("rtsbuilding_planetrise:thermal_generator");
    /** 能量设备物品注册 ID：风力发电机。 */
    private static final ResourceLocation WIND_GENERATOR =
            ResourceLocation.tryParse("rtsbuilding_planetrise:wind_generator");

    /** 可连接范围量级（1.5 倍：超出即不显示）。 */
    private static final double UNREACHABLE_FACTOR = 1.5D;

    /** 可连接颜色（蓝）。 */
    private static final float OK_R = 0.25F, OK_G = 0.65F, OK_B = 1.0F;
    /** 超范围颜色（红）。 */
    private static final float OVER_R = 1.0F, OVER_G = 0.30F, OVER_B = 0.30F;

    /** 线框透明度。 */
    private static final float LINE_ALPHA = 0.9F;

    /** 虚线渲染缓冲区容量。 */
    private static final int BUFFER_CAPACITY = 1024 * 16;

    /** 每段虚线段长度（格）。 */
    private static final double DASH_LENGTH = 0.28D;
    /** 每段虚线间隔（格）。 */
    private static final double GAP_LENGTH = 0.16D;

    /** 最多绘制的连接线数量（远离目标/覆盖的节点按距离取最近者，避免刷屏）。 */
    private static final int MAX_LINES = 12;

    private RtsGridLinkPreviewRenderer() {
    }

    /** 渲染回调入口：玩家正在放置能量设备时，绘制其与附近已放置能量节点的连接虚线。 */
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // 1) 解析当前「正在放置的能量设备」的目标位置与链路范围
        BlockPos targetPos;
        long linkRange;
        Block targetBlock = null;
        if (mc.screen instanceof BuilderScreen screen) {
            // RTS 模式：读取建造模式选中的建造物品
            BuildingModule bm = RtsClientKernel.get().module(BuildingModule.class);
            if (bm == null) {
                return;
            }
            String itemId = bm.getSelectedItemId();
            linkRange = linkRangeForId(itemId);
            if (linkRange <= 0) {
                return;
            }
            targetBlock = blockById(itemId);
            if (targetBlock == null) {
                return;
            }
            CursorRaycaster.CursorRay ray = CursorRaycaster.computeCursorRay(mc, screen);
            if (ray == null) {
                return;
            }
            BlockHitResult hit = ray.raycastBlock(mc);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            targetPos = hit.getBlockPos().relative(hit.getDirection());
        } else {
            // 手动放置：检查主手/副手持有的能量设备
            ItemStack held = heldEnergyItem(mc.player);
            if (held.isEmpty()) {
                return;
            }
            linkRange = linkRangeForId(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
            if (linkRange <= 0) {
                return;
            }
            targetBlock = Block.byItem(held.getItem());
            if (targetBlock == null) {
                return;
            }
            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                return;
            }
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            targetPos = hit.getBlockPos().relative(hit.getDirection());
        }

        // 2) 遍历已放置的能量节点，按距离收集落在「1.5×链路范围」内的候选连线。
        // 判定基准与 PowerScheduler.samePhysicalGrid 完全一致：用<b>方块坐标距离平方</b>
        // （等价方块中心距离），阈值用 <b>max(新设备链路范围, 该节点链路范围)</b>（含覆盖接入）。
        // 原实现用「设备顶部中心」距离 + 仅新设备自身链路范围，对塔/风机等高塔类设备会因顶部高度
        // 抬升距离、且忽略另一节点更大链路范围，导致连接虚线与实际组网范围不对齐。
        BlockPos target = targetPos.immutable();
        // 目标端按「即将放置的设备」模型顶部中心定位（塔/风机是整个塔顶，非单格方块顶）——仅作绘制端点。
        Vec3 targetTop = deviceTopCenter(target, targetBlock);
        // 目标端（新设备）的角色与供电范围：决定「覆盖接入」（塔覆盖发电机）判定。
        PowerRole targetRole = nodeRoleForBlock(targetBlock);
        long targetPowerRange = targetRole == PowerRole.TOWER ? Config.powerTowerPowerRange() : 0L;

        List<Candidate> candidates = new ArrayList<>();
        for (IPowerGridNode node : PowerRangeVisualStore.INSTANCE.nodes()) {
            if (!(node instanceof BlockEntity be)) {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            if (pos.equals(target)) {
                continue;
            }
            // 距离用方块坐标距离平方（与 samePhysicalGrid 的 squaredDistance 一致），非顶部中心距离。
            long distSq = squareDistance(target, pos);
            double link = Math.max(linkRange, node.linkRange());
            double maxDistSq = link * link * UNREACHABLE_FACTOR * UNREACHABLE_FACTOR;
            if (distSq > maxDistSq) {
                continue;
            }
            Vec3 otherTop = deviceTopCenter(pos, be.getBlockState().getBlock());
            candidates.add(new Candidate(otherTop, Math.sqrt(distSq), distSq, node, pos));
        }
        if (candidates.isEmpty()) {
            return;
        }
        // 取距离最近的前 N 条（已按 1.5×链路范围过滤）
        candidates.sort(Comparator.comparingDouble(c -> c.dist));

        // 3) 绘制虚线（虚线 = 按固定 dash/gap 分段的小线段）
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        ByteBufferBuilder backing = new ByteBufferBuilder(BUFFER_CAPACITY);
        BufferBuilder builder = new BufferBuilder(backing,
                RenderType.lines().mode, RenderType.lines().format);

        int drawn = 0;
        for (Candidate c : candidates) {
            if (drawn >= MAX_LINES) {
                break;
            }
            // 可连判定复用 PowerScheduler.sameNetwork（与服务端组网同一份权威规则，渲染所见即服务端所连）。
            boolean ok = PowerScheduler.sameNetwork(
                    target.getX(), target.getY(), target.getZ(), targetRole, linkRange, targetPowerRange,
                    c.pos().getX(), c.pos().getY(), c.pos().getZ(),
                    c.node.role(), c.node.linkRange(), c.node.powerRange());
            float r = ok ? OK_R : OVER_R;
            float g = ok ? OK_G : OVER_G;
            float b = ok ? OK_B : OVER_B;
            drawDashedLine(builder, poseStack, targetTop, c.otherTop,
                    r, g, b, LINE_ALPHA, DASH_LENGTH, GAP_LENGTH);
            drawn++;
        }

        MeshData mesh = builder.build();
        if (mesh != null) {
            RenderType.lines().draw(mesh);
            mesh.close();
        }
        backing.close();
        poseStack.popPose();
    }

    /** 由目标放置方块推断其组网角色：输电塔→TOWER，发电机→GENERATOR，未知返回 null。 */
    private static PowerRole nodeRoleForBlock(Block block) {
        if (block instanceof PowerTowerBlock) {
            return PowerRole.TOWER;
        }
        if (block instanceof WindGeneratorBlock || block instanceof ThermalGeneratorBlock) {
            return PowerRole.GENERATOR;
        }
        return null;
    }

    /** 两方块坐标距离平方（整数，与 {@code GridNode.squaredDistance} / 组网判定一致，避免浮点误差）。 */
    private static long squareDistance(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** 一个连线候选：目标节点顶面中心 + 其组网节点（用于角色范围判定）+ 距离。 */
    private record Candidate(Vec3 otherTop, double dist, long distSq, IPowerGridNode node, BlockPos pos) {
    }

    /**
     * 取设备<b>模型最顶部中心</b>（x+0.5, topY, z+0.5）——虚线端点与距离判定基准。
     * <p>
     * 顶部高度按<b>设备真实模型</b>计算：
     * <ul>
     *   <li>输电塔（Java 模型，5 格高）：顶部在 {@code y + (BOUNDING_HEIGHT+1)}；</li>
     *   <li>风力发电机（Java 模型，3 格高）：顶部在 {@code y + (BOUNDING_HEIGHT+1)}；</li>
     *   <li>其余能量节点（普通方块模型，单格）：顶部在 {@code y + 1}。</li>
     * </ul>
     * 保证连接线从设备<b>模型顶部</b>连出，而非固定到方块顶（单格高），契合塔类设备外观。
     */
    private static Vec3 deviceTopCenter(BlockPos pos, Block block) {
        double topY = pos.getY() + deviceTopHeight(block);
        return new Vec3(pos.getX() + 0.5D, topY, pos.getZ() + 0.5D);
    }

    /** 返回设备<b>模型顶部相对主方块的偏移高度</b>（格）。 */
    private static double deviceTopHeight(Block block) {
        if (block instanceof PowerTowerBlock) {
            return PowerTowerBlockEntity.BOUNDING_HEIGHT + 1.0D;
        }
        if (block instanceof WindGeneratorBlock) {
            return WindGeneratorBlockEntity.BOUNDING_HEIGHT + 1.0D;
        }
        // 单格方块（热能发电机等）：模型顶部即方块顶。
        return 1.0D;
    }

    /** 按键位 id 解析对应方块（用于目标端设备顶部判定）；非方块物品返回 null。 */
    private static Block blockById(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        return null;
    }

    /** 按键位 id 判定是否为能量设备，并返回其链路范围；非能量设备返回 0。 */
    private static long linkRangeForId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0L;
        }
        try {
            if (POWER_TOWER.toString().equals(itemId)) {
                return Config.powerTowerLinkRange();
            }
            if (THERMAL_GENERATOR.toString().equals(itemId) || WIND_GENERATOR.toString().equals(itemId)) {
                return Config.generatorLinkRange();
            }
        } catch (RuntimeException e) {
            // 配置未加载时安全回退：不渲染。
            return 0L;
        }
        return 0L;
    }

    /** 取玩家主手/副手中持有的能量设备物品（非能量设备返回空）。 */
    private static ItemStack heldEnergyItem(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isEnergyItem(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        if (isEnergyItem(offHand)) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    /** 判定物品是否为可组网能量设备（输电塔 / 发电机）。 */
    private static boolean isEnergyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return POWER_TOWER.equals(key) || THERMAL_GENERATOR.equals(key) || WIND_GENERATOR.equals(key);
    }

    /**
     * 绘制一条<b>虚线</b>：从起点到终点，按「画 DASH 长、空 GAP 长」交替分段生成小线段。
     * 均写入 {@code RenderType.lines()}（POSITION_COLOR_NORMAL，带法线），顶点格式与
     * {@link com.rtsbuilding.rtsbuilding.client.render.pass.TowerRangePreviewPass} 的
     * 网格线一致，保证线宽/深度表现统一。
     */
    private static void drawDashedLine(VertexConsumer consumer, PoseStack poseStack,
            Vec3 from, Vec3 to,
            float r, float g, float b, float a,
            double dashLength, double gapLength) {
        Vec3 delta = to.subtract(from);
        double total = delta.length();
        if (total < 1.0e-6) {
            return;
        }
        Vec3 dir = delta.scale(1.0 / total);
        double step = dashLength + gapLength;
        double t = 0.0D;
        while (t < total) {
            double dashEnd = Math.min(t + dashLength, total);
            Vec3 a0 = from.add(dir.scale(t));
            Vec3 a1 = from.add(dir.scale(dashEnd));
            lineSegment(consumer, poseStack, a0, a1, r, g, b, a);
            t += step;
        }
    }

    /** 追加一条线段顶点（LINES 格式，POSITION_COLOR_NORMAL）。 */
    private static void lineSegment(VertexConsumer consumer, PoseStack poseStack,
            Vec3 from, Vec3 to, float r, float g, float b, float alpha) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0e-6) {
            return;
        }
        float nx = (float) (delta.x / len);
        float ny = (float) (delta.y / len);
        float nz = (float) (delta.z / len);
        var pose = poseStack.last();
        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, nx, ny, nz);
    }
}

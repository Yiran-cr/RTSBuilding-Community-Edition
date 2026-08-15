package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.core.network.ActionType;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;
import com.rtsbuilding.rtsbuilding.network.message.C2SAction;
import com.rtsbuilding.rtsbuilding.network.message.C2SCameraPosePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class RtsClientPacketGateway {
    private RtsClientPacketGateway() {}

    private static C2SAction act(ActionType type, CompoundTag t) { return new C2SAction(type, t); }
    private static CompoundTag tag() { return new CompoundTag(); }

    public static void sendSetMode(BuilderMode mode) {
        var t = tag(); t.putByte("mode", (byte) mode.ordinal());
        PacketDistributor.sendToServer(act(ActionType.SET_MODE, t));
    }

    public static void sendToggleCamera(boolean startAtPlayerHead) {
        var t = tag(); t.putBoolean("startAtPlayerHead", startAtPlayerHead);
        PacketDistributor.sendToServer(act(ActionType.TOGGLE_CAMERA, t));
    }

    public static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        // 服务端按 byte 模式语义读取（MODE_BIDIRECTIONAL=0 / MODE_EXTRACT_ONLY=1），
        // 不能 putBoolean：true 会编码为 1，恰好撞上 MODE_EXTRACT_ONLY 导致新绑定变成仅提取。
        var t = tag(); t.putLong("pos", pos.asLong());
        t.putByte("allowStore", (byte) (allowStore ? NetworkConstants.MODE_BIDIRECTIONAL : NetworkConstants.MODE_EXTRACT_ONLY));
        PacketDistributor.sendToServer(act(ActionType.LINK_STORAGE, t));
    }

    public static void sendUpdateLinkedStorage(BlockPos pos, boolean extractOnly, int priority) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putBoolean("extractOnly", extractOnly); t.putInt("priority", priority);
        PacketDistributor.sendToServer(act(ActionType.UPDATE_LINKED_STORAGE, t));
    }

    public static void sendUnlinkStorage(BlockPos pos) {
        var t = tag(); t.putLong("pos", pos.asLong());
        PacketDistributor.sendToServer(act(ActionType.UNLINK_STORAGE, t));
    }

    /**
     * 通知服务端从会话“最近使用”记录中删除一条条目（按注册表 ID）。
     * 服务端真正移除后，条目不会在重进/重启后复活。
     */
    public static void sendRemoveRecentEntry(String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        var t = tag(); t.putString("itemId", itemId);
        PacketDistributor.sendToServer(act(ActionType.REMOVE_RECENT_ENTRY, t));
    }

    public static void sendRequestStoragePage(int page, String search, String category,
                                               com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort sort,
                                               boolean ascending, int pageSize) {
        var t = tag(); t.putInt("page", page); t.putString("search", search == null ? "" : search);
        t.putString("category", category == null ? "" : category);
        t.putByte("sort", (byte) sort.ordinal()); t.putBoolean("ascending", ascending); t.putInt("pageSize", pageSize);
        PacketDistributor.sendToServer(act(ActionType.REQUEST_PAGE, t));
    }

    public static void sendCloseRemoteMenu() {
        PacketDistributor.sendToServer(act(ActionType.CLOSE_REMOTE_MENU, tag()));
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied,
                                  String itemId, int rotateSteps,
                                  Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild) {
        var t = tag(); t.putLong("pos", hit.getBlockPos().asLong());
        t.putByte("face", (byte) hit.getDirection().get3DDataValue());
        t.putDouble("hitX", hit.getLocation().x); t.putDouble("hitY", hit.getLocation().y); t.putDouble("hitZ", hit.getLocation().z);
        t.putByte("rotateSteps", (byte) rotateSteps); t.putBoolean("forcePlace", forcePlace);
        t.putBoolean("skipIfOccupied", skipIfOccupied);
        t.putString("itemId", itemId == null ? "" : itemId);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        t.putBoolean("quickBuild", quickBuild);
        PacketDistributor.sendToServer(act(ActionType.PLACE_BLOCK, t));
    }

    public static void sendPlaceFluid(BlockHitResult hit, boolean forcePlace, String fluidId, Vec3 rayOrigin, Vec3 rayDir) {
        var t = tag(); t.putLong("pos", hit.getBlockPos().asLong());
        t.putByte("face", (byte) hit.getDirection().get3DDataValue());
        t.putDouble("hitX", hit.getLocation().x); t.putDouble("hitY", hit.getLocation().y); t.putDouble("hitZ", hit.getLocation().z);
        t.putBoolean("forcePlace", forcePlace); t.putString("fluidId", fluidId == null ? "" : fluidId);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        PacketDistributor.sendToServer(act(ActionType.PLACE_FLUID, t));
    }

    public static void sendMineStart(BlockPos pos, int face, int toolSlot, String toolItemId,
                                      boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putByte("face", (byte) face);
        t.putBoolean("start", true); t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putString("toolItemId", toolItemId == null ? "" : toolItemId);
        t.putBoolean("allowPlacedBlockRecovery", allowPlacedBlockRecovery);
        t.putBoolean("toolProtectionEnabled", toolProtectionEnabled);
        PacketDistributor.sendToServer(act(ActionType.MINE_BLOCK, t));
    }

    public static void sendMineAbort(BlockPos pos, int face, int toolSlot) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putByte("face", (byte) face);
        t.putBoolean("start", false); t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        PacketDistributor.sendToServer(act(ActionType.MINE_BLOCK, t));
    }

    public static void sendUltimineStart(BlockPos pos, int face, int toolSlot, int limit, byte mode,
                                          String toolItemId, boolean toolProtectionEnabled) {
        var t = tag(); t.putLong("pos", pos.asLong()); t.putByte("face", (byte) face);
        t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putString("toolItemId", toolItemId == null ? "" : toolItemId);
        t.putShort("limit", (short) Mth.clamp(limit, 1, 256)); t.putByte("mode", mode);
        t.putBoolean("toolProtectionEnabled", toolProtectionEnabled);
        PacketDistributor.sendToServer(act(ActionType.ULTIMINE, t));
    }

    public static void sendRotateBlock(BlockPos pos) {
        var t = tag(); t.putLong("pos", pos.asLong());
        PacketDistributor.sendToServer(act(ActionType.ROTATE_BLOCK, t));
    }

    public static void sendPathfindingGoTo(BlockPos target) {
        var t = tag(); t.putLong("target", target.asLong());
        PacketDistributor.sendToServer(act(ActionType.PATHFIND, t));
    }

    public static void sendUndo() {
        PacketDistributor.sendToServer(act(ActionType.UNDO, tag()));
    }

    /**
     * 实时上报客户端 RTS 相机姿态（位置 + 朝向）给服务端。
     * <p>相机移动/旋转是纯客户端计算（CameraModule 每 tick/渲染帧更新本地状态），
     * 服务端通过此方法获取相机的真实位置与朝向，供权威逻辑（如动作范围校验、实体跟随）使用。</p>
     * <p>高频路径：使用专用 payload 直接字段编解码，不走 NBT CompoundTag。</p>
     *
     * @param x     相机世界 X 坐标
     * @param y     相机世界 Y 坐标
     * @param z     相机世界 Z 坐标
     * @param yaw   偏航角（度）
     * @param pitch 俯仰角（度）
     */
    public static void sendCameraPose(double x, double y, double z, float yaw, float pitch) {
        PacketDistributor.sendToServer(new C2SCameraPosePayload(x, y, z, yaw, pitch));
    }

    public static void sendInteractEntityEmptyHand(int entityId, Vec3 hitLocation,
                                                    @javax.annotation.Nullable BlockHitResult blockHit,
                                                    Vec3 rayOrigin, Vec3 rayDir) {
        BlockPos clickedPos; byte face;
        if (blockHit != null) { clickedPos = blockHit.getBlockPos(); face = (byte) blockHit.getDirection().get3DDataValue(); }
        else { clickedPos = BlockPos.containing(hitLocation); face = 1; }
        var t = tag(); t.putInt("entityId", entityId); t.putLong("clickedPos", clickedPos.asLong());
        t.putByte("face", face); t.putDouble("hitX", hitLocation.x); t.putDouble("hitY", hitLocation.y); t.putDouble("hitZ", hitLocation.z);
        t.putByte("sourceType", (byte) NetworkConstants.INTERACT_EMPTY_HAND); t.putByte("toolSlot", (byte) 0);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        PacketDistributor.sendToServer(act(ActionType.INTERACT_BLOCK, t));
    }

    public static void sendPauseWorkflow(int entryId) {
        var t = tag(); t.putInt("entryId", entryId);
        PacketDistributor.sendToServer(act(ActionType.PAUSE_WORKFLOW, t));
    }

    public static void sendDeleteWorkflow(int entryId) {
        var t = tag(); t.putInt("entryId", entryId);
        PacketDistributor.sendToServer(act(ActionType.DELETE_WORKFLOW, t));
    }

    // ── Linked-storage ↔ container menu transfers ──

    /**
     * Pick {@code amount} of the linked-storage item {@code prototype} into the
     * open container menu carried slot (server-authoritative).
     */
    public static void sendLinkedPickup(ItemStack prototype, int amount) {
        sendLinkedPickup(prototype, amount, false);
    }

    /**
     * Pick up {@code amount} of {@code prototype} into the carried slot.
     *
     * @param fromInventory {@code true} when the clicked grid entry sources from the player inventory
     *                      (extract only from player inventory instead of linked storage)
     */
    public static void sendLinkedPickup(ItemStack prototype, int amount, boolean fromInventory) {
        if (prototype == null || prototype.isEmpty() || amount <= 0) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var t = tag();
        t.put("prototype", prototype.saveOptional(level.registryAccess()));
        t.putInt("amount", amount);
        t.putBoolean("fromInventory", fromInventory);
        PacketDistributor.sendToServer(act(ActionType.LINKED_PICKUP, t));
    }

    /**
     * Return {@code amount} of the carried item matching {@code itemId}
     * (registry item id, e.g. {@code minecraft:dirt}) back to the linked storage.
     */
    public static void sendReturnCarried(String itemId, int amount) {
        if (itemId == null || itemId.isBlank() || amount <= 0) return;
        var t = tag(); t.putString("itemId", itemId); t.putInt("amount", amount);
        PacketDistributor.sendToServer(act(ActionType.RETURN_CARRIED, t));
    }

    /**
     * Shift-style quick move: push the linked-storage item {@code prototype}
     * straight into the open menu (single-item merge pass).
     */
    public static void sendLinkedQuickMove(ItemStack prototype) {
        sendLinkedQuickMove(prototype, false);
    }

    /**
     * Shift-style quick move: push the item straight into the open menu (single-item merge pass).
     *
     * @param fromInventory {@code true} when the clicked grid entry sources from the player inventory
     *                      (extract from player inventory and store into linked storage instead)
     */
    public static void sendLinkedQuickMove(ItemStack prototype, boolean fromInventory) {
        if (prototype == null || prototype.isEmpty()) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var t = tag();
        t.put("prototype", prototype.saveOptional(level.registryAccess()));
        t.putBoolean("fromInventory", fromInventory);
        PacketDistributor.sendToServer(act(ActionType.LINKED_QUICK_MOVE, t));
    }

    /**
     * Shift-click a slot of the open container menu: import the whole slot
     * (or one craft cycle for crafting tables) into linked storage.
     */
    public static void sendImportMenuSlot(int menuSlot) {
        if (menuSlot < 0) return;
        var t = tag(); t.putInt("slot", menuSlot);
        PacketDistributor.sendToServer(act(ActionType.IMPORT_MENU_SLOT, t));
    }

    // ── Box-select area operations (build mode) ──

    /**
     * 框选模式批量破坏：收集框选区域 [min, max) 内的可破坏方块位置，
     * 交给服务端 {@code AREA_DESTROY} 队列按 tick 节流破坏。
     */
    public static void sendAreaBoxDestroy(BlockPos min, BlockPos max, int toolSlot,
                                          String toolItemId, boolean toolProtectionEnabled) {
        if (min == null || max == null) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        List<Long> positions = collectAreaPositions(level, min, max, false, false);
        if (positions.isEmpty()) return;
        sendAreaDestroyTag(positions, toolSlot, toolItemId, toolProtectionEnabled);
    }

    /**
     * 形状画笔批量破坏：把画笔生成的形状位置列表直接交给服务端 {@code AREA_DESTROY} 队列按 tick 节流破坏。
     * 与框选破坏共用同一破坏标签构建，只是目标来自形状几何计算而非框选扫描。
     */
    public static void sendShapeAreaDestroy(List<BlockPos> positions, int toolSlot,
                                            String toolItemId, boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) return;
        int limit = Math.min(positions.size(), NetworkConstants.MAX_POSITIONS);
        List<Long> longs = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            longs.add(positions.get(i).asLong());
        }
        sendAreaDestroyTag(longs, toolSlot, toolItemId, toolProtectionEnabled);
    }

    /** 构建并发送 {@code AREA_DESTROY} 批量破坏请求的公共标签。 */
    private static void sendAreaDestroyTag(List<Long> positions, int toolSlot,
                                           String toolItemId, boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) return;
        var t = tag();
        var list = new ListTag();
        for (Long l : positions) list.add(net.minecraft.nbt.LongTag.valueOf(l));
        t.put("positions", list);
        t.putByte("toolSlot", (byte) Mth.clamp(toolSlot, 0, 8));
        t.putString("toolItemId", toolItemId == null ? "" : toolItemId);
        t.putBoolean("toolProtectionEnabled", toolProtectionEnabled);
        PacketDistributor.sendToServer(act(ActionType.AREA_DESTROY, t));
    }

    /**
     * 框选模式批量放置：收集框选区域 [min, max) 内的可替换（空气）位置，
     * 交给服务端 {@code PLACE_BATCH} 队列按 tick 节流放置当前选中方块。
     */
    public static void sendAreaBoxPlace(BlockPos min, BlockPos max, byte rotateSteps,
                                        boolean forcePlace, boolean skipIfOccupied, String itemId,
                                        Vec3 rayOrigin, Vec3 rayDir) {
        if (min == null || max == null || itemId == null || itemId.isBlank()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        List<Long> positions = collectAreaPositions(level, min, max, true, forcePlace);
        if (positions.isEmpty()) return;
        sendBatchTag(positions, rotateSteps, forcePlace, skipIfOccupied, itemId,
                true, rayOrigin, rayDir);
    }

    /**
     * 线模式建造批量放置：沿起点到终点线段生成的位置列表，交给服务端 {@code PLACE_BATCH} 队列按 tick 节流放置。
     * 形状位置是客户端已解析好的精确坐标，走快速建造路径（{@code quickBuild=true}），
     * 服务端直接 {@code setBlock} 到目标位置，避免交互式放置按面偏移一格。
     */
    public static void sendLinePlace(List<BlockPos> positions, byte rotateSteps,
                                     boolean forcePlace, boolean skipIfOccupied, String itemId,
                                     Vec3 rayOrigin, Vec3 rayDir) {
        if (positions == null || positions.isEmpty() || itemId == null || itemId.isBlank()) return;
        int limit = Math.min(positions.size(), NetworkConstants.MAX_POSITIONS);
        List<Long> longs = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            longs.add(positions.get(i).asLong());
        }
        sendBatchTag(longs, rotateSteps, forcePlace, skipIfOccupied, itemId,
                true, rayOrigin, rayDir);
    }

    /** 构建并发送 {@code PLACE_BATCH} 批量放置请求的公共标签。 */
    private static void sendBatchTag(List<Long> positions, byte rotateSteps,
                                     boolean forcePlace, boolean skipIfOccupied, String itemId,
                                     boolean quickBuild, Vec3 rayOrigin, Vec3 rayDir) {
        if (positions == null || positions.isEmpty()) return;
        var t = tag();
        var list = new ListTag();
        for (Long l : positions) list.add(net.minecraft.nbt.LongTag.valueOf(l));
        t.put("positions", list);
        t.putByte("face", (byte) Direction.UP.get3DDataValue());
        t.putByte("rotateSteps", rotateSteps);
        t.putBoolean("forcePlace", forcePlace);
        t.putBoolean("skipIfOccupied", skipIfOccupied);
        t.putBoolean("quickBuild", quickBuild);
        t.putString("itemId", itemId);
        t.putDouble("hitOffsetX", 0.5);
        t.putDouble("hitOffsetY", 0.5);
        t.putDouble("hitOffsetZ", 0.5);
        t.putDouble("rayOriginX", rayOrigin.x); t.putDouble("rayOriginY", rayOrigin.y); t.putDouble("rayOriginZ", rayOrigin.z);
        t.putDouble("rayDirX", rayDir.x); t.putDouble("rayDirY", rayDir.y); t.putDouble("rayDirZ", rayDir.z);
        PacketDistributor.sendToServer(act(ActionType.PLACE_BATCH, t));
    }

    /**
     * 遍历框选区域 [min, max) 收集目标位置，最多 {@link NetworkConstants#MAX_POSITIONS} 个。
     *
     * @param place   {@code true} 收集可替换位置（批量放置），{@code false} 收集可破坏方块位置（批量破坏）
     * @param replace 仅 place=true 时有效：为 {@code true} 时收集区域内全部位置（替换模式，覆盖已有方块）
     */
    private static List<Long> collectAreaPositions(Level level, BlockPos min, BlockPos max,
                                                   boolean place, boolean replace) {
        List<Long> positions = new ArrayList<>();
        for (int y = min.getY(); y < max.getY(); y++) {
            for (int z = min.getZ(); z < max.getZ(); z++) {
                for (int x = min.getX(); x < max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean match;
                    if (place) {
                        match = replace || state.isAir() || state.canBeReplaced();
                    } else {
                        match = !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
                    }
                    if (match) {
                        positions.add(pos.asLong());
                        if (positions.size() >= NetworkConstants.MAX_POSITIONS) return positions;
                    }
                }
            }
        }
        return positions;
    }

    // ── Funnel (item pickup) ──

    /**
     * 同步物品拾取（漏斗）开关状态到服务端。
     * 客户端点击左栏“物品拾取”按钮或按快捷键时发送，服务端据此开启/关闭漏斗能力。
     */
    public static void sendSetFunnelEnabled(boolean enabled) {
        var t = tag(); t.putBoolean("enabled", enabled);
        PacketDistributor.sendToServer(act(ActionType.SET_FUNNEL, t));
    }

    /**
     * 同步漏斗（物品拾取）吸取范围半径（格）到服务端。
     * 客户端右面板下嵌层调节器拖动时发送，服务端按玩家保存半径。
     */
    public static void sendSetFunnelRadius(double radius) {
        var t = tag(); t.putDouble("radius", radius);
        PacketDistributor.sendToServer(act(ActionType.SET_FUNNEL_RADIUS, t));
    }

    // ── 工作流恢复 ──

    /** 请求扫描暂停工作流的恢复数据（服务端回 S2CResumeScanPayload）。 */
    public static void sendRequestResumeScan(int workflowEntryId) {
        PacketDistributor.sendToServer(new com.rtsbuilding.rtsbuilding.network.resume.C2SRequestResumeScanPayload(workflowEntryId));
    }

    /** 对暂停工作流执行恢复动作：0=开始，1=跳过，2=覆盖。 */
    public static void sendResumeAction(int workflowEntryId, byte strategy) {
        PacketDistributor.sendToServer(new com.rtsbuilding.rtsbuilding.network.resume.C2SResumeActionPayload(workflowEntryId, strategy));
    }

    /**
     * 点击模式漏斗：以目标方块位置为球心、半径 2 格持续吸取掉落物到储存空间。
     * 服务端每 tick 最多提取 64 个物品，直到区域清空。
     */
    public static void sendFunnelPickup(BlockPos pos) {
        if (pos == null) return;
        var t = tag(); t.putLong("pos", pos.asLong());
        PacketDistributor.sendToServer(act(ActionType.FUNNEL_PICKUP, t));
    }

    /**
     * 框选模式漏斗：客户端同步框选区域内的掉落物实体 ID 列表给服务端，
     * 服务端将这些实体一次性吸取到储存空间。
     */
    public static void sendFunnelBoxPickup(List<Integer> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return;
        var t = tag();
        var list = new ListTag();
        int limit = Math.min(entityIds.size(), 256);
        for (int i = 0; i < limit; i++) list.add(net.minecraft.nbt.IntTag.valueOf(entityIds.get(i)));
        t.put("entities", list);
        PacketDistributor.sendToServer(act(ActionType.FUNNEL_BOX_PICKUP, t));
    }
}

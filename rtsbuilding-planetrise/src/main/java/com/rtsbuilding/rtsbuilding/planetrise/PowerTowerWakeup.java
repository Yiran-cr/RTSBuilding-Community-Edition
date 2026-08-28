package com.rtsbuilding.rtsbuilding.planetrise;

import com.rtsbuilding.rtsbuilding.planetrise.block.entity.PowerTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 输电塔<b>事件唤醒</b>控制器——解决「自适应退避扫描下，新增设备响应慢」的问题，且<b>支持大量塔与
 * 范围重叠</b>的可扩展实现。
 * <p>
 * 电网稳定时 {@link PowerTowerBlockEntity} 会把扫描周期退避到低频（省主线程成本，最多 10 秒一扫），
 * 导致供电范围内<b>新放置的设备</b>要等很久才被识别供电。本类监听服务器的<b>方块放置 / 破坏</b>
 * 事件，若事件位置落在某个输电塔的供电范围内，立即调用 {@link PowerTowerBlockEntity#forceRescan()}
 * 唤醒它——下个 tick 即开始扫一圈（≤{@code SCAN_INTERVAL} tick / 1 秒），把供电延迟从「最长 10 秒」
 * 降到「1 秒内」。退避仍在稳态省成本，两者兼得。
 * <p>
 * <b>可扩展性</b>：用<b>空间哈希</b>而非线性遍历所有塔。每座塔把供电范围 AABB 覆盖的所有网格单元
 * （{@link #CELL} 大小）登记到桶中；事件时只查「事件位置所在单元」的候选塔，再 AABB 精确校验。
 * 复杂度由 <b>O(维度内全部塔数)</b> 降为 <b>O(覆盖该单元的重叠塔数)</b>——塔再多、范围再重叠，
 * 单次放置/破坏的唤醒开销也只跟「落在这个格子里互相重叠的塔」成正比。仅服务端生效。
 */
public final class PowerTowerWakeup {

    private PowerTowerWakeup() {
    }

    /** 空间哈希单元大小（格）。塔的供电范围 AABB 覆盖多少个单元就登记到多少个桶。 */
    private static final int CELL = 32;

    /** 维度 → (单元 key → 覆盖该单元的输电塔)。维度随 WeakHashMap 卸载自动回收。 */
    private static final Map<ServerLevel, Map<Long, List<PowerTowerBlockEntity>>> BUCKETS = new WeakHashMap<>();

    /** 输电塔装载（chunk 加载）时注册，把其供电范围覆盖的所有单元登记到桶。 */
    public static void register(ServerLevel level, PowerTowerBlockEntity tower) {
        Map<Long, List<PowerTowerBlockEntity>> buckets = BUCKETS.computeIfAbsent(level, k -> new HashMap<>());
        for (long cell : coveredCells(tower)) {
            List<PowerTowerBlockEntity> list = buckets.computeIfAbsent(cell, k -> new ArrayList<>());
            if (!list.contains(tower)) {
                list.add(tower);
            }
        }
    }

    /** 输电塔卸载时注销：从其覆盖的所有单元桶中移除。 */
    public static void unregister(ServerLevel level, PowerTowerBlockEntity tower) {
        Map<Long, List<PowerTowerBlockEntity>> buckets = BUCKETS.get(level);
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        for (long cell : coveredCells(tower)) {
            List<PowerTowerBlockEntity> list = buckets.get(cell);
            if (list != null && !list.isEmpty()) {
                list.remove(tower);
                if (list.isEmpty()) {
                    buckets.remove(cell);
                }
            }
        }
        if (buckets.isEmpty()) {
            BUCKETS.remove(level);
        }
    }

    /** 单方块放置：唤醒覆盖该位置的输电塔。 */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        wakeup(event.getLevel(), event.getPos());
    }

    /** 多方块放置（桶/平铺等一批）：唤醒覆盖各位置的所有输电塔。 */
    @SubscribeEvent
    public static void onEntityMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        wakeup(event.getLevel(), event.getPos());
    }

    /** 方块破坏：唤醒（供电范围内拆除设备也可能释放名额，让塔及时重扫）。 */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        wakeup(event.getLevel(), event.getPos());
    }

    /** 事件位置所在单元内的候选塔，经 AABB 精确校验后唤醒。 */
    private static void wakeup(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return;
        }
        Map<Long, List<PowerTowerBlockEntity>> buckets = BUCKETS.get(serverLevel);
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        List<PowerTowerBlockEntity> list = buckets.get(cellKey(pos));
        if (list == null || list.isEmpty()) {
            return;
        }
        for (PowerTowerBlockEntity tower : list) {
            if (tower.isInSupplyRange(pos)) {
                tower.forceRescan();
            }
        }
    }

    /** 塔的供电范围 AABB 覆盖的所有单元 key 列表（供注册/注销）。 */
    private static List<Long> coveredCells(PowerTowerBlockEntity tower) {
        BlockPos p = tower.getBlockPos();
        int rX = tower.supplyRadiusX();
        int rY = tower.supplyRadiusY();
        int rZ = tower.supplyRadiusZ();
        int x0 = Math.floorDiv(p.getX() - rX, CELL), x1 = Math.floorDiv(p.getX() + rX, CELL);
        int y0 = Math.floorDiv(p.getY() - rY, CELL), y1 = Math.floorDiv(p.getY() + rY, CELL);
        int z0 = Math.floorDiv(p.getZ() - rZ, CELL), z1 = Math.floorDiv(p.getZ() + rZ, CELL);
        List<Long> cells = new ArrayList<>((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1));
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) {
                    cells.add(cellKey(cx, cy, cz));
                }
            }
        }
        return cells;
    }

    /** 某世界坐标所在单元的 key。 */
    private static long cellKey(BlockPos pos) {
        return cellKey(Math.floorDiv(pos.getX(), CELL),
                Math.floorDiv(pos.getY(), CELL),
                Math.floorDiv(pos.getZ(), CELL));
    }

    /** 单元坐标 → 无碰撞 long key（每维 20 bit ≈ ±1,048,575 单元，×32 格 ≥ MC 世界半径）。 */
    private static long cellKey(int cx, int cy, int cz) {
        return ((long) (cx & 0xFFFFF) << 40) | ((long) (cy & 0xFFFFF) << 20) | ((long) (cz & 0xFFFFF));
    }
}

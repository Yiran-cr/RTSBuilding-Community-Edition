package com.rtsbuilding.rtsbuilding.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 电网节点归属的静态桥（电网多人系统）。 
 * <p>
 * 主模组在（手动 / RTS）放置方块后调用 {@link #onPlaced(ServerLevel, BlockPos, UUID)}；内置
 * 能量插件（rtsbuilding-planetrise）在启动时通过 {@link #install(Binding)} 注入实现，把该位置上
 * 若是能量节点方块实体的 {@code gridOwner} 绑定到放置者，从而将节点纳入其电网组。
 * <p>
 * 之所以走静态桥而非直接调用：主模组与内置插件之间<b>禁止编译期依赖</b>（见 AGENTS.md），
 * 插件内容合入主模组 JAR，只能通过 {@code AtomicReference} 桥实现单向通信（与
 * {@link RtsTerminalEnergy} 同模式）。
 */
public final class RtsGridOwnerBinding {

    /** 归属绑定实现：主模组放置后把位置上报，插件判断并设置节点归属。 */
    @FunctionalInterface
    public interface Binding {
        /** 在服务器端某位置被（某玩家）放置后回调。 */
        void onPlaced(ServerLevel level, BlockPos pos, UUID playerUuid);
    }

    private static final AtomicReference<Binding> BINDING = new AtomicReference<>();

    private RtsGridOwnerBinding() {
    }

    /** 安装归属绑定实现（仅内置插件在 commonSetup 调用一次）。 */
    public static void install(@Nullable Binding binding) {
        BINDING.set(binding);
    }

    /**
     * 在某位置（被某玩家）放置方块后由主模组调用。
     * 未安装实现或位置不是能量节点时为空操作。
     */
    public static void onPlaced(ServerLevel level, BlockPos pos, UUID playerUuid) {
        Binding binding = BINDING.get();
        if (binding != null && playerUuid != null && level != null && pos != null) {
            binding.onPlaced(level, pos, playerUuid);
        }
    }
}

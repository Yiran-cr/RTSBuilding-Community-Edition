package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 让隐藏区域对应的区块网格失效并重新编译。
 *
 * <p>这里只负责渲染器刷新，不读取或修改剔除圆柱。Embeddium / Sodium 替换了原版
 * 区块渲染器，因此安装时直接使用它们的区域重建入口；未安装时继续调用原版
 * {@code LevelRenderer#setBlocksDirty}。可选模组通过一次性反射适配，
 * 不把它们变成运行前置依赖。</p>
 */
public final class RtsCullingRenderInvalidator {

    /** Sodium 世界渲染器入口（反射，{@code initialize=false} 不会触发静态初始化）。 */
    private static final RendererMethods SODIUM = RendererMethods.find(
            "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
    /** Embeddium 世界渲染器入口（反射同 Sodium）。 */
    private static final RendererMethods EMBEDDIUM = RendererMethods.find(
            "org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer");

    private RtsCullingRenderInvalidator() {
    }

    /**
     * 把矩形区块区域标记为脏并请求重建（区域外扩 1 格保证网格边缘正确）。
     *
     * @param min 区域最小角（方块坐标，整数格）
     * @param max 区域最大角（方块坐标，整数格）
     */
    public static void markBlocksDirty(BlockPos min, BlockPos max) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.levelRenderer == null || min == null || max == null) {
            return;
        }

        int minX = Math.min(min.getX(), max.getX()) - 1;
        int minY = Math.min(min.getY(), max.getY()) - 1;
        int minZ = Math.min(min.getZ(), max.getZ()) - 1;
        int maxX = Math.max(min.getX(), max.getX()) + 1;
        int maxY = Math.max(min.getY(), max.getY()) + 1;
        int maxZ = Math.max(min.getZ(), max.getZ()) + 1;

        // 优先走 Sodium/Embeddium 的区域重建入口（后台网格编译路径）
        if (SODIUM.schedule(minX, minY, minZ, maxX, maxY, maxZ)
                || EMBEDDIUM.schedule(minX, minY, minZ, maxX, maxY, maxZ)) {
            return;
        }
        minecraft.levelRenderer.setBlocksDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 渲染器重建方法的反射封装：{@code instanceNullable()} 取单例（可能为 null），
     * 再调 {@code scheduleRebuildForBlockArea(...)} 请求区域重建。
     */
    private record RendererMethods(Method instanceNullable, Method scheduleRebuild) {

        /** 按类名反射查找两个方法；类不存在或方法缺失时返回空载体（安静降级）。 */
        private static RendererMethods find(String className) {
            try {
                Class<?> renderer = Class.forName(
                        className,
                        false,
                        RtsCullingRenderInvalidator.class.getClassLoader());
                return new RendererMethods(
                        renderer.getMethod("instanceNullable"),
                        renderer.getMethod("scheduleRebuildForBlockArea",
                                int.class, int.class, int.class, int.class, int.class, int.class, boolean.class));
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                return new RendererMethods(null, null);
            }
        }

        /** 请求区域重建；单例不存在或反射失败返回 false（回退原版入口）。 */
        private boolean schedule(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (instanceNullable == null || scheduleRebuild == null) {
                return false;
            }
            try {
                Object renderer = instanceNullable.invoke(null);
                if (renderer == null) {
                    return false;
                }
                scheduleRebuild.invoke(renderer, minX, minY, minZ, maxX, maxY, maxZ, false);
                return true;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return false;
            }
        }
    }
}
package com.rtsbuilding.rtsbuilding.client.render.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

/**
 * 方块模型渲染顶点消费者包装器：在 {@link RtsAlphaVertexConsumer} 的半透明基础上，
 * 额外对 RGB 通道统一乘以染色系数，用于蓝图放置冲突提示（目标位置已被占用时虚影染红）。
 * 不改写纹理 UV 与光照，仅影响表面颜色：{@code setColor} 输出 {@code r*tintR, g*tintG, b*tintB, a*alpha}。
 */
public final class RtsTintVertexConsumer extends VertexConsumerWrapper {

    /** 透明度系数（0~1，外部已裁剪）。 */
    private final float alpha;
    /** RGB 染色系数（0~1，冲突提示时 R 保持、G/B 衰减以呈现红色调）。 */
    private final float tintR;
    private final float tintG;
    private final float tintB;

    public RtsTintVertexConsumer(VertexConsumer parent, float alpha, float tintR, float tintG, float tintB) {
        super(parent);
        this.alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        this.tintR = Math.max(0.0F, Math.min(1.0F, tintR));
        this.tintG = Math.max(0.0F, Math.min(1.0F, tintG));
        this.tintB = Math.max(0.0F, Math.min(1.0F, tintB));
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        parent.setColor(
                Math.round(r * tintR),
                Math.round(g * tintG),
                Math.round(b * tintB),
                Math.round(a * alpha));
        return this;
    }
}
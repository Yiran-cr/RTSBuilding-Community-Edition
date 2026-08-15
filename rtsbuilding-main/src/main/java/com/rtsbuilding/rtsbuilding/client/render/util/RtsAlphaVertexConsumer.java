package com.rtsbuilding.rtsbuilding.client.render.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

/**
 * 方块模型渲染顶点消费者包装器：将写入顶点的透明度（a）统一乘以 alpha 因子后转发给父消费者，
 * 用于方块缩放动画（放置 grow / 破坏 shrink）的半透明淡入淡出。
 * 仅改写 alpha，不影响 RGB 颜色、纹理 UV 与光照，可叠加在 {@code RenderPipeline#BLOCK_ANIMATION}
 * 缓冲之上，参考 BuildingGadgets2 的 {@code DireVertexConsumer} 实现思路。
 */
public final class RtsAlphaVertexConsumer extends VertexConsumerWrapper {

    /** 透明度系数（0~1，外部已裁剪）。 */
    private final float alpha;

    public RtsAlphaVertexConsumer(VertexConsumer parent, float alpha) {
        super(parent);
        this.alpha = Math.max(0.0F, Math.min(1.0F, alpha));
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        parent.setColor(r, g, b, Math.round(a * alpha));
        return this;
    }
}

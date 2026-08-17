package com.rtsbuilding.uifw.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;

import java.io.IOException;

/**
 * 带 mipmap 的简单纹理。
 *
 * <p>Minecraft 的 {@link net.minecraft.client.renderer.texture.SimpleTexture} 加载 GUI 贴图时不会生成
 * mipmap（传给 {@link NativeImage#upload} 的 mipmap 参数恒为 false），导致大比例缩小（如 512px 图标缩到
 * 24px）时即便 GL_LINEAR 也会严重混叠。本类在加载时用 {@link MipmapGenerator} 生成完整 mipmap 链并
 * 以 {@code GL_LINEAR_MIPMAP_LINEAR} 采样，实现平滑缩放。</p>
 *
 * <p>使用方式：通过 {@code TextureManager.register(location, new MipmapTexture(location))} 注册（宿主 mod
 * 负责在资源重载后重新注册，uifw 不感知具体纹理）。对应 {@link TextureInfo.FilterMode#HQ}。</p>
 */
public class MipmapTexture extends AbstractTexture {

    /** 纹理资源路径（含 .png 后缀）。 */
    private final ResourceLocation location;

    public MipmapTexture(ResourceLocation location) {
        this.location = location;
    }

    @Override
    public void load(ResourceManager manager) throws IOException {
        var resource = manager.getResource(location)
                .orElseThrow(() -> new IOException("Cannot load texture resource " + location));
        try (NativeImage image = NativeImage.read(resource.open())) {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            // 非 2 的幂无法生成 mipmap：退化为普通线性过滤单级，避免 GL 报错
            if ((width & (width - 1)) != 0 || (height & (height - 1)) != 0) {
                TextureUtil.prepareImage(this.getId(), 0, width, height);
                image.upload(0, 0, 0, 0, 0, width, height, true, true, false, true);
                this.setFilter(true, false);
                return;
            }
            // mip 层数取短边的 log2：非正方形纹理（如 1024x512）若按长边计算，
            // 最后几级短边会右移到 0，TextureUtil.prepareImage 直接 width>>i 无 max(1,) 保护，
            // 会产生 GL_INVALID_VALUE 使纹理不完整（渲染为黑块）。
            int maxLevels = Mth.ceillog2(Math.min(width, height));
            NativeImage[] mipmaps = MipmapGenerator.generateMipLevels(new NativeImage[] { image }, maxLevels);
            int levels = mipmaps.length - 1;
            TextureUtil.prepareImage(this.getId(), levels, width, height);
            for (int level = 0; level < mipmaps.length; level++) {
                NativeImage mip = mipmaps[level];
                mip.upload(level, 0, 0, 0, 0, mip.getWidth(), mip.getHeight(), true, true, false, true);
                mip.close();
            }
            // 只有 2 层及以上才可用 mipmap 过滤，否则 GL_LINEAR_MIPMAP_LINEAR 会导致纹理不完整
            this.setFilter(levels > 0, levels > 0);
        }
    }
}

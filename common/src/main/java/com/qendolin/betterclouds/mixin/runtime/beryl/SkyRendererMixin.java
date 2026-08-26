package com.qendolin.betterclouds.mixin.runtime.beryl;

import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.vulkanmod.vulkan.VRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.beryl.render.SkyRenderer2", remap = false)
public class SkyRendererMixin {
    private static final Identifier BLUE_NOISE = Identifier.fromNamespaceAndPath("betterclouds", "textures/blue_noise.png");

    @Inject(method = "renderSkyDisc", at = @At("HEAD"))
    private void onRenderSky(float f1, float f2, float f3, CallbackInfo ci) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(BLUE_NOISE);
        if (tex == null) {
            net.minecraft.client.renderer.texture.SimpleTexture stex = new net.minecraft.client.renderer.texture.SimpleTexture(BLUE_NOISE);
            Minecraft.getInstance().getTextureManager().registerAndLoad(BLUE_NOISE, stex);
            tex = stex;
            
            // Set repeat mode via GL compat
            int glId = com.qendolin.betterclouds.util.RenderHelper.getTextureId(tex);
            com.mojang.blaze3d.opengl.GlStateManager._bindTexture(glId);
            org.lwjgl.opengl.GL32.glTexParameteri(org.lwjgl.opengl.GL32.GL_TEXTURE_2D, org.lwjgl.opengl.GL32.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL32.GL_REPEAT);
            org.lwjgl.opengl.GL32.glTexParameteri(org.lwjgl.opengl.GL32.GL_TEXTURE_2D, org.lwjgl.opengl.GL32.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL32.GL_REPEAT);
            com.mojang.blaze3d.opengl.GlStateManager._bindTexture(0);
        }
        if (tex != null) {
            VRenderSystem.setShaderTexture(6, tex.getTextureView());
        }
    }
}

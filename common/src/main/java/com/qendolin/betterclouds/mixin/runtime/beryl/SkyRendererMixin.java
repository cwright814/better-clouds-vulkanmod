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
        if (tex != null) {
            VRenderSystem.setShaderTexture(1, tex.getTextureView());
        }
    }
}

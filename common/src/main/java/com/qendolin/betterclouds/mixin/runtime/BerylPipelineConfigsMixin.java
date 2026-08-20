package com.qendolin.betterclouds.mixin.runtime;

import net.vulkanmod.vulkan.shader.PipelineConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.beryl.render.shader.BerylPipelineConfigs", remap = false)
public class BerylPipelineConfigsMixin {

    private static void injectCloudUBO(PipelineConfig.Builder builder) {
        boolean[] hasBinding = new boolean[8];
        if (builder.ubs != null) {
            for (PipelineConfig.UB ub : builder.ubs) {
                if (ub.binding >= 0 && ub.binding < 8) {
                    hasBinding[ub.binding] = true;
                }
            }
        }
        if (builder.imageDescriptors != null) {
            for (net.vulkanmod.vulkan.shader.PipelineConfig.ImageDescriptorInfo img : builder.imageDescriptors) {
                if (img.binding() >= 0 && img.binding() < 8) {
                    hasBinding[img.binding()] = true;
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            if (!hasBinding[i]) {
                PipelineConfig.UB dummy = PipelineConfig.UB.builder(i, 31)
                        .addUniform("float", "Dummy" + i)
                        .build();
                builder.addUB(dummy);
            }
        }

        PipelineConfig.UB cloudUbo = PipelineConfig.UB.builder(8, 31)
                .addUniform("float", "WindDriftX")
                .addUniform("float", "WindDriftZ")
                .addUniform("float", "Cloudiness")
                .addUniform("float", "CloudScale")
                .addUniform("float", "CloudShadowsEnabled")
                .addUniform("float", "CloudTime")
                .addUniform("float", "CameraX")
                .addUniform("float", "CameraZ")
                .addUniform("float", "CloudShadowIntensity")
                .build();
        
        builder.addUB(cloudUbo);
    }

    @Redirect(
        method = "getTerrainConfig(Z)Lnet/vulkanmod/vulkan/shader/PipelineConfig;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/vulkanmod/vulkan/shader/PipelineConfig$Builder;build()Lnet/vulkanmod/vulkan/shader/PipelineConfig;"
        )
    )
    private static PipelineConfig onBuildTerrainConfig(PipelineConfig.Builder builder) {
        System.out.println("[BetterClouds Debug] Injecting CloudUBO into Beryl's Terrain Config!");
        injectCloudUBO(builder);
        return builder.build();
    }

    @Redirect(
        method = "getTranslucentTerrainConfig(Z)Lnet/vulkanmod/vulkan/shader/PipelineConfig;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/vulkanmod/vulkan/shader/PipelineConfig$Builder;build()Lnet/vulkanmod/vulkan/shader/PipelineConfig;"
        )
    )
    private static PipelineConfig onBuildTranslucentTerrainConfig(PipelineConfig.Builder builder) {
        System.out.println("[BetterClouds Debug] Injecting CloudUBO into Beryl's Translucent Terrain Config!");
        injectCloudUBO(builder);
        return builder.build();
    }
}

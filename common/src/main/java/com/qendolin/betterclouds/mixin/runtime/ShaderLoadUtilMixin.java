package com.qendolin.betterclouds.mixin.runtime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Mixin(targets = "net.vulkanmod.render.shader.ShaderLoadUtil", remap = false)
public class ShaderLoadUtilMixin {
    @Inject(method = "getInputStream", at = @At("HEAD"), cancellable = true)
    private static void onGetInputStream(String path, CallbackInfoReturnable<InputStream> cir) {
//        System.out.println("[BetterClouds Debug] getInputStream called for: " + path);
        if (path.endsWith("basic/terrain/terrain.fsh")) {
            System.out.println("[BetterClouds Debug] Intercepting terrain.fsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/terrain.fsh");
            if (is != null) cir.setReturnValue(is);
            else System.out.println("[BetterClouds Debug] FAILED to find custom terrain.fsh!");
        } else if (path.endsWith("basic/terrain/terrain.vsh")) {
            System.out.println("[BetterClouds Debug] Intercepting terrain.vsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/terrain.vsh");
            if (is != null) cir.setReturnValue(is);
            else System.out.println("[BetterClouds Debug] FAILED to find custom terrain.vsh!");
        } else if (path.endsWith("beryl/shaders/terrain/terrain.fsh")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL terrain.fsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/terrain.fsh");
            if (is != null) cir.setReturnValue(is);
            else System.out.println("[BetterClouds Debug] FAILED to find custom BERYL terrain.fsh!");
        } else if (path.endsWith("beryl/shaders/sky/sky.fsh")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL sky.fsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/sky.fsh");
            if (is != null) cir.setReturnValue(is);
            else System.out.println("[BetterClouds Debug] FAILED to find custom BERYL sky.fsh!");
        } else if (path.endsWith("beryl/shaders/sky/sky.json")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL sky.json!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/sky.json");
            if (is != null) cir.setReturnValue(is);
            else System.out.println("[BetterClouds Debug] FAILED to find custom BERYL sky.json!");
        } else if (path.endsWith("beryl/shaders/terrain/terrain.vsh")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL terrain.vsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/terrain.vsh");
            if (is != null) cir.setReturnValue(is);
            else System.out.println("[BetterClouds Debug] FAILED to find custom BERYL terrain.vsh!");
        } else if (path.endsWith("beryl/shaders/terrain/terrain.json")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL terrain.json!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/terrain.json");
            if (is != null) cir.setReturnValue(is);
            else System.out.println("[BetterClouds Debug] FAILED to find custom BERYL terrain.json!");
        } else if (path.endsWith("beryl/shaders/entity/entity.json")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL entity.json!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/entity.json");
            if (is != null) cir.setReturnValue(is);
        } else if (path.endsWith("beryl/shaders/entity/entity.fsh")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL entity.fsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/entity.fsh");
            if (is != null) cir.setReturnValue(is);
        } else if (path.endsWith("beryl/shaders/entity/entity.vsh")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL entity.vsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/entity.vsh");
            if (is != null) cir.setReturnValue(is);
        } else if (path.endsWith("beryl/shaders/translucent/translucent.json")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL translucent.json!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/translucent.json");
            if (is != null) cir.setReturnValue(is);
        } else if (path.endsWith("beryl/shaders/translucent/translucent.fsh")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL translucent.fsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/translucent.fsh");
            if (is != null) cir.setReturnValue(is);
        } else if (path.endsWith("beryl/shaders/translucent/translucent.vsh")) {
            System.out.println("[BetterClouds Debug] Intercepting BERYL translucent.vsh!");
            InputStream is = ShaderLoadUtilMixin.class.getResourceAsStream("/assets/betterclouds/shaders/vulkanmod/beryl/translucent.vsh");
            if (is != null) cir.setReturnValue(is);
        }


    }
}

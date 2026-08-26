package com.qendolin.betterclouds.shadow;

import com.qendolin.betterclouds.BetterClouds;
import com.qendolin.betterclouds.clouds.ChunkedGenerator;
import com.qendolin.betterclouds.config.ConfigManager;
import net.minecraft.client.Minecraft;

public class VulkanShadows {
    public static void registerUniforms() {
        try {
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("WindDriftX", () -> {
                ChunkedGenerator gen = getGenerator();
                return gen != null ? (float) gen.originX() : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("WindDriftZ", () -> {
                ChunkedGenerator gen = getGenerator();
                return gen != null ? (float) gen.originZ() : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("Cloudiness", () -> {
                ChunkedGenerator gen = getGenerator();
                float c = gen != null ? gen.currentCloudiness() : 1.0f;
                // com.qendolin.betterclouds.BetterCloudsStatic.getLogger().info("Cloudiness: " + c);
                return c;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("CloudScale", () -> {
                var config = ConfigManager.instance();
                return config != null ? config.samplingScale : 1.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("CloudShadowsEnabled", () -> {
                var config = ConfigManager.instance();
                return (config != null && config.shadowsEnabled) ? 1.0f : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("BerylSkyDithering", () -> {
                var config = ConfigManager.instance();
                return (config != null && config.berylSkyDithering) ? 1.0f : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("DhShadowIntensity", () -> {
                var config = ConfigManager.instance();
                return config != null ? config.dhShadowIntensity : 0.3f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("CloudShadowIntensity", () -> {
                var config = ConfigManager.instance();
                return config != null ? config.shadowIntensity : 0.85f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("CloudTime", () -> {
                ChunkedGenerator gen = getGenerator();
                return gen != null ? (float) (gen.sampler.getSeed() % 1000000000) : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("NoiseOffsetX", () -> {
                ChunkedGenerator gen = getGenerator();
                return gen != null ? gen.sampler.noiseOffsetX : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("NoiseOffsetZ", () -> {
                ChunkedGenerator gen = getGenerator();
                return gen != null ? gen.sampler.noiseOffsetZ : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("ShadowOffsetX", () -> {
                var config = ConfigManager.instance();
                return config != null ? config.shadowOffsetX : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("ShadowOffsetZ", () -> {
                var config = ConfigManager.instance();
                return config != null ? config.shadowOffsetZ : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("ShadowRotation", () -> {
                var config = ConfigManager.instance();
                return config != null ? config.shadowRotation : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("ShadowFlipX", () -> {
                var config = ConfigManager.instance();
                return (config != null && config.shadowFlipX) ? 1.0f : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("ShadowScale", () -> {
                var config = ConfigManager.instance();
                return config != null ? config.shadowScale : 1.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("ShadowFlipZ", () -> {
                var config = ConfigManager.instance();
                return (config != null && config.shadowFlipZ) ? 1.0f : 0.0f;
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("CameraX", () -> {
                return getCameraPos(true);
            });
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("CameraZ", () -> {
                return getCameraPos(false);
            });
            for (int i = 0; i < 8; i++) {
                net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("Dummy" + i, () -> 0.0f);
            }
        } catch (Throwable t) {
            com.qendolin.betterclouds.BetterCloudsStatic.getLogger().error("Failed to register VulkanMod uniforms for cloud shadows", t);
        }
    }

    private static ChunkedGenerator getGenerator() {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer instanceof com.qendolin.betterclouds.duck.WorldRendererDuck duck) {
            var vRenderer = duck.betterclouds$getVulkanRenderer();
            if (vRenderer != null && vRenderer.resources() != null) {
                return vRenderer.resources().generator;
            }
        }
        return null;
    }

    private static Object cameraObj = null;
    private static java.lang.reflect.Method getPosMethod = null;
    private static java.lang.reflect.Method getXMethod = null;
    private static java.lang.reflect.Method getZMethod = null;
    private static boolean cameraReflectionFailed = false;
    
    private static float getCameraPos(boolean isX) {
        if (cameraReflectionFailed) return 0.0f;
        try {
            var gr = Minecraft.getInstance().gameRenderer;
            if (cameraObj == null) {
                try {
                    cameraObj = gr.getClass().getMethod("mainCamera").invoke(gr);
                } catch (Exception e) {
                    try {
                        cameraObj = gr.getClass().getMethod("getCamera").invoke(gr);
                    } catch (Exception e2) {
                        for (var m : gr.getClass().getMethods()) {
                            if (m.getReturnType().getName().endsWith("Camera")) {
                                cameraObj = m.invoke(gr);
                                break;
                            }
                        }
                    }
                }
                if (cameraObj == null) {
                    cameraReflectionFailed = true;
                    return 0.0f;
                }
            }
            if (getPosMethod == null) {
                for (var m : cameraObj.getClass().getMethods()) {
                    if (m.getName().equals("position") || m.getName().equals("getPos")) {
                        getPosMethod = m;
                        break;
                    }
                }
                if (getPosMethod == null) {
                    for (var m : cameraObj.getClass().getMethods()) {
                        if (m.getReturnType().getName().endsWith("Vec3") || m.getReturnType().getName().endsWith("Vec3d")) {
                            getPosMethod = m;
                            break;
                        }
                    }
                }
                if (getPosMethod == null) {
                    cameraReflectionFailed = true;
                    return 0.0f;
                }
            }
            
            var vec3 = getPosMethod.invoke(cameraObj);
            if (vec3 != null) {
                if (isX && getXMethod == null) {
                    for (var m : vec3.getClass().getMethods()) {
                        if (m.getName().equals("x") || m.getName().equals("getX")) {
                            getXMethod = m;
                            break;
                        }
                    }
                    if (getXMethod == null) cameraReflectionFailed = true;
                } else if (!isX && getZMethod == null) {
                    for (var m : vec3.getClass().getMethods()) {
                        if (m.getName().equals("z") || m.getName().equals("getZ")) {
                            getZMethod = m;
                            break;
                        }
                    }
                    if (getZMethod == null) cameraReflectionFailed = true;
                }
                
                if (cameraReflectionFailed) return 0.0f;
                
                if (isX) return ((Number) getXMethod.invoke(vec3)).floatValue();
                else return ((Number) getZMethod.invoke(vec3)).floatValue();
            }
        } catch (Exception e) {
            cameraReflectionFailed = true;
        }
        return 0.0f;
    }
}

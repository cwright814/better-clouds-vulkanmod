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
            net.vulkanmod.vulkan.shader.Uniforms.vec1f_uniformMap.put("CloudTime", () -> {
                ChunkedGenerator gen = getGenerator();
                return gen != null ? (float) (gen.sampler.getSeed() % 1000000000) : 0.0f;
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
    
    private static float getCameraPos(boolean isX) {
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
            }
            if (cameraObj != null) {
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
                }
                if (getPosMethod != null) {
                    var vec3 = getPosMethod.invoke(cameraObj);
                    if (vec3 != null) {
                        String targetMethodName = isX ? "x" : "z";
                        String altMethodName = isX ? "getX" : "getZ";
                        for (var m : vec3.getClass().getMethods()) {
                            if (m.getName().equals(targetMethodName) || m.getName().equals(altMethodName)) {
                                return ((Number) m.invoke(vec3)).floatValue();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently fall back to 0.0f
        }
        return 0.0f;
    }
}

package com.qendolin.betterclouds.compat;

import com.qendolin.betterclouds.BetterClouds;
import com.qendolin.betterclouds.BetterCloudsStatic;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiColorDepthTextureCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import org.joml.Matrix4f;

import java.util.Optional;

public abstract class DistantHorizonsSharedCompatImpl extends DistantHorizonsCompat {
    protected boolean textureCreateFlag = false;
    private boolean isDhInitialized = false;
    private DhApiRenderParam lastRenderParam = null;

    @SuppressWarnings("deprecation")
    public DistantHorizonsSharedCompatImpl() {
        BetterCloudsStatic.getLogger().info("Registering DH Api events");
        // Lambdas didn't work
        DhApiEventRegister.on(DhApiAfterDhInitEvent.class, new DhApiAfterDhInitEvent() {
            @Override
            public void afterDistantHorizonsInit(DhApiEventParam<Void> dhApiEventParam) {
                isDhInitialized = true;
            }
        });
        DhApiEventRegister.on(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> dhApiEventParam) {
                if (dhApiEventParam.value.renderPass == EDhApiRenderPass.OPAQUE || dhApiEventParam.value.renderPass == EDhApiRenderPass.OPAQUE_AND_TRANSPARENT) {
                    lastRenderParam = dhApiEventParam.value;
                }
                // With shaders the transparent rendering pass might be deferred and doesn't have a 'valid' dhProjectionMatrix
                // Don't know if that's how it's supposed to be, but I can't use it.

                if (BetterClouds.isEnabled()) {
                    disableLodClouds();
                }
            }
        });
        DhApiEventRegister.on(DhApiColorDepthTextureCreatedEvent.class, new DhApiColorDepthTextureCreatedEvent() {
            @Override
            public void onResize(DhApiEventParam<EventParam> dhApiEventParam) {
                textureCreateFlag = true;
            }
        });
    }

    @Override
    public boolean isReady() {
        return isDhInitialized && lastRenderParam != null;
    }

    @Override
    public boolean isEnabled() {
        return isDhInitialized && DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        float[] mat = getDhProjectionMatrixValues(lastRenderParam);
        return new Matrix4f(mat[0], mat[4], mat[8], mat[12],
                mat[1], mat[5], mat[9], mat[13],
                mat[2], mat[6], mat[10], mat[14],
                mat[3], mat[7], mat[11], mat[15]);
    }

    abstract float[] getDhProjectionMatrixValues(DhApiRenderParam renderParam);

    private static boolean reflectionInitialized = false;
    private static java.lang.reflect.Field activeIntegrationField;
    private static java.lang.reflect.Method getBackendMethod;
    private static java.lang.reflect.Method getDhFramebufferMethod;
    private static java.lang.reflect.Method getFramebufferMethod;
    private static java.lang.reflect.Method getDepthAttachmentMethod;
    private static java.lang.reflect.Method getTextureMethod;
    private static java.lang.reflect.Method getIdMethod;

    @Override
    public Optional<Integer> getDepthTextureId() {
        if (!reflectionInitialized) {
            try {
                Class<?> entrypointClass = Class.forName("com.braffolk.dhvulkan.DhVulkanModEntrypoint");
                activeIntegrationField = entrypointClass.getDeclaredField("activeIntegration");
                activeIntegrationField.setAccessible(true);
                
                Class<?> integrationClass = Class.forName("com.braffolk.dhvulkan.bridge.DhIntegration");
                getBackendMethod = integrationClass.getMethod("getBackend");
                
                Class<?> backendClass = Class.forName("com.braffolk.dhvulkan.core.VulkanRenderEngine");
                getDhFramebufferMethod = backendClass.getMethod("getDhFramebuffer");
                
                Class<?> dhFramebufferClass = Class.forName("com.braffolk.dhvulkan.core.DhVulkanFramebuffer");
                getFramebufferMethod = dhFramebufferClass.getMethod("getFramebuffer");
                
                Class<?> framebufferClass = Class.forName("net.vulkanmod.vulkan.framebuffer.Framebuffer");
                getDepthAttachmentMethod = framebufferClass.getMethod("getDepthAttachment");
                
                Class<?> attachmentClass = Class.forName("net.vulkanmod.vulkan.texture.VulkanImage");
                getTextureMethod = null; // Removed, VulkanImage is already the texture
                getIdMethod = attachmentClass.getMethod("getId");
            } catch (Exception e) {
                // Keep trying if we haven't successfully loaded classes yet, 
                // but once we succeed or fail at finding DHV classes, we stop.
                if (e instanceof ClassNotFoundException || e instanceof NoSuchMethodException) {
                    reflectionInitialized = true;
                }
            }
            if (activeIntegrationField != null && getBackendMethod != null) {
                reflectionInitialized = true;
            }
        }

        try {
            if (activeIntegrationField != null && getBackendMethod != null && getDhFramebufferMethod != null && 
                getFramebufferMethod != null && getDepthAttachmentMethod != null && getIdMethod != null) {
                
                Object activeIntegration = activeIntegrationField.get(null);
                if (activeIntegration != null) {
                    Object backend = getBackendMethod.invoke(activeIntegration);
                    if (backend != null && backend.getClass().getName().equals("com.braffolk.dhvulkan.core.VulkanRenderEngine")) {
                        Object dhFramebuffer = getDhFramebufferMethod.invoke(backend);
                        if (dhFramebuffer != null) {
                            Object framebuffer = getFramebufferMethod.invoke(dhFramebuffer);
                            if (framebuffer != null) {
                                Object depthAttachment = getDepthAttachmentMethod.invoke(framebuffer);
                                if (depthAttachment != null) {
                                    int id = ((Number) getIdMethod.invoke(depthAttachment)).intValue();
                                    return Optional.of(id);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallthrough
        }

        DhApiResult<Integer> result = DhApi.Delayed.renderProxy.getDhDepthTextureId();
        if (result.success) {
            return Optional.of(result.payload);
        }
        return Optional.empty();
    }
    @Override
    public boolean isTextureCreateFlagSet() {
        return textureCreateFlag;
    }

    @Override
    public void resetTextureCreateFlag() {
        textureCreateFlag = false;
    }
}

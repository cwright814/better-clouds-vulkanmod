package com.qendolin.betterclouds.clouds.vulkan;

import com.qendolin.betterclouds.BetterCloudsStatic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import com.qendolin.betterclouds.clouds.Renderer; // for PrepareResult

import com.qendolin.betterclouds.duck.BiomeManagerDuck;

public class VulkanRenderer implements AutoCloseable {
    private final Minecraft client;
    private ClientLevel world = null;
    private VulkanResources res = new VulkanResources();

    public VulkanRenderer(Minecraft client) {
        this.client = client;
    }

    public void setWorld(ClientLevel world) {
        this.world = world;
    }

    public long getWorldSeed() {
        if (client.level == null) return 0;
        return ((BiomeManagerDuck) client.level.getBiomeManager()).better_clouds$biomeSeed();
    }

    public void reload(ResourceManager manager) {
        BetterCloudsStatic.getLogger().info("Reloading Vulkan cloud renderer...");
        res.reloadMeshPrimitives();
        res.reloadShaders(manager);
        res.reloadGenerator(getWorldSeed());
    }

    public VulkanResources resources() {
        return res;
    }

    private Matrix4f viewMatrix = new Matrix4f();
    private Matrix4f projMatrix = new Matrix4f();
    private Matrix4f mvpMatrix = new Matrix4f();
    private Matrix4f dhMatrix = new Matrix4f();
    private long lastSeed = 0L;
    private float lastSizeXZ = -1;
    private float lastSizeY = -1;

    public Renderer.PrepareResult prepare(Matrix4f viewMat, Matrix4f projMat, int ticks, float tickDelta, Vector3d cam) {
        long currentSeed = getWorldSeed();
        if (currentSeed != lastSeed) {
            lastSeed = currentSeed;
            res.reloadGenerator(currentSeed);
        }

        viewMatrix.set(viewMat);
        projMatrix.set(projMat);
        
        com.qendolin.betterclouds.config.Config config = com.qendolin.betterclouds.config.ConfigManager.instance();
        float currentSizeXZ = config.sizeXZ;
        float currentSizeY = config.sizeY;
        if (currentSizeXZ != lastSizeXZ || currentSizeY != lastSizeY) {
            lastSizeXZ = currentSizeXZ;
            lastSizeY = currentSizeY;
            res.reloadShaders(client.getResourceManager());
        }

        res.generator.reallocateIfStale(config, true);

        float cloudiness = com.qendolin.betterclouds.clouds.CloudinessProvider.getCloudiness(client.level, tickDelta);
        res.generator.update(cam, ticks, ticks, tickDelta, config, cloudiness);
        
        if (res.generator.canGenerate() && !res.generator.generating() && !com.qendolin.betterclouds.clouds.Debug.generatorPause) {
            res.generator.generate();
        }

        if (res.generator.canSwap()) {
            res.generator.swap();
        }

        return Renderer.PrepareResult.RENDER;
    }

    public void render(int ticks, float tickDelta, Vector3d cam, Vector3d frustumPos, Frustum frustum) {
        if (res.shader == null || res.shader.getPipeline() == null) return;
        com.qendolin.betterclouds.config.Config config = com.qendolin.betterclouds.config.ConfigManager.instance();
        
        // Calculate matrices
        float cloudsHeight = client.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT, new net.minecraft.world.phys.Vec3(cam.x, cam.y, cam.z));
        Matrix4f mvMatrix = new Matrix4f(viewMatrix);
        // BetterClouds fix for translation
        mvMatrix.m30(0); mvMatrix.m31(0); mvMatrix.m32(0); mvMatrix.m33(1);
        mvMatrix.m23(0); mvMatrix.m13(0); mvMatrix.m03(0);
        
        mvMatrix.translate((float) res.generator.renderOriginX(cam.x), (float) (cloudsHeight - cam.y), (float) res.generator.renderOriginZ(cam.z));
        mvpMatrix.set(projMatrix).mul(mvMatrix);

        // Calculate uniforms
        float sunAngle = com.qendolin.betterclouds.clouds.EffectTintProvider.getSunAngleRadians(client.level, cam);
        long skyTime = client.level.getOverworldClockTime() % 24000L;
        float skyAngleRad = com.qendolin.betterclouds.clouds.EffectTintProvider.getSunAngleRadians(client.level, cam);
        float dayNightFactor = com.qendolin.betterclouds.util.MathUtil.interpolateDayNightFactor(skyTime, config.shaderPreset().sunriseStartTime, config.shaderPreset().sunriseEndTime, config.shaderPreset().sunsetStartTime, config.shaderPreset().sunsetEndTime);
        float brightness = (1.0f - dayNightFactor) * config.shaderPreset().nightBrightness + dayNightFactor * config.shaderPreset().dayBrightness;
        float mappedTime = com.qendolin.betterclouds.util.MathUtil.mapTimeOfDay(skyTime, config.shaderPreset().sunriseStartTime, config.shaderPreset().sunriseEndTime, config.shaderPreset().sunsetStartTime, config.shaderPreset().sunsetEndTime);

        float sunPathAngleRad = config.shaderPreset().sunPathAngle * net.minecraft.util.Mth.DEG_TO_RAD;
        org.joml.Vector3d realSunDir = new org.joml.Vector3d(0, 1, 0).rotateX(sunPathAngleRad).rotateZ(skyAngleRad).normalize();
        org.joml.Vector4f sunDir = new org.joml.Vector4f((float)realSunDir.x, (float)realSunDir.y, (float)realSunDir.z, mappedTime / 24000f);
        
        float sunAxisY = (float) Math.sin(sunPathAngleRad);
        float sunAxisZ = (float) Math.cos(sunPathAngleRad);
        org.joml.Vector3f sunAxis = new org.joml.Vector3f(0.0f, sunAxisY, sunAxisZ).normalize();
        
        float time = ((float) client.level.getGameTime() + tickDelta) / 20.0f;
        res.shader.setUniformFloat("u_time", time);
        
        res.shader.setUniformMat4("u_vp_matrix", projMatrix);
        res.shader.setUniformMat4("u_mv_matrix", mvMatrix);
        res.shader.setUniformMat4("u_mc_p_matrix", projMatrix);
        res.shader.setUniformMat4("u_dh_p_matrix", dhMatrix);
        res.shader.setUniformMat4("u_mvp_matrix", mvpMatrix);
        
        res.shader.setUniformVec4("u_bounding_box", new org.joml.Vector4f(
                (float) cam.x, (float) cam.z,
                config.blockDistance() - config.chunkSize / 2f,
                config.yRange + config.sizeY
        ));
        res.shader.setUniformVec4("u_sun_direction", sunDir);
        res.shader.setUniformVec4("u_color_grading", new org.joml.Vector4f(brightness, 1f / config.shaderPreset().gamma(), 0.0f, config.shaderPreset().saturation));
        res.shader.setUniformVec3("u_origin_offset", new org.joml.Vector3f(
            (float) -res.generator.renderOriginX(cam.x),
            (float) (cam.y - cloudsHeight),
            (float) -res.generator.renderOriginZ(cam.z)
        ));
        res.shader.setUniformFloat("u_time", time);
        res.shader.setUniformVec4("u_miscellaneous", new org.joml.Vector4f(config.scaleFalloffMin, config.windEffectFactor, config.windSpeedFactor, config.yOffset));
        res.shader.setUniformFloat("u_noise_factor", config.colorVariationFactor);
        res.shader.setUniformVec3("u_sun_axis", sunAxis);
        float rain = client.level.getRainLevel(tickDelta);
        float thunder = client.level.getThunderLevel(tickDelta);
        float darknessMult = 1.0f - (rain * config.rainDarkness) - (thunder * config.thunderDarkness);
        darknessMult = Math.max(0.1f, darknessMult);
        
        res.shader.setUniformVec3("u_opacity", new org.joml.Vector3f(config.shaderPreset().opacity, config.shaderPreset().opacityFactor, config.shaderPreset().opacityExponent));
        res.shader.setUniformVec3("u_tint", new org.joml.Vector3f(
                config.shaderPreset().tintRed * darknessMult,
                config.shaderPreset().tintGreen * darknessMult,
                config.shaderPreset().tintBlue * darknessMult
        ));
        res.shader.setUniformVec2("u_fog_range", new org.joml.Vector2f(config.blockDistance() * 0.8f, config.blockDistance()));
        res.shader.setUniformVec2("u_depth_range", new org.joml.Vector2f(0.1f, 1000.0f));

        // Get drawer
        net.vulkanmod.vulkan.Drawer drawer = net.vulkanmod.vulkan.Renderer.getDrawer();
        net.vulkanmod.vulkan.Renderer renderer = net.vulkanmod.vulkan.Renderer.getInstance();
        
        // Update expanded buffer
        com.qendolin.betterclouds.clouds.Buffer buffer = res.generator.buffer();
        java.nio.FloatBuffer drawBuffer = buffer != null ? buffer.getDrawBuffer() : null;
        if (drawBuffer != null) {
            int numClouds = res.generator.cloudCount();
            if (numClouds > 0) {
                boolean fancy = client.options.cloudStatus().get() == net.minecraft.client.CloudStatus.FANCY;
                res.updateExpandedBuffer(drawBuffer, numClouds, fancy);

                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
                org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                org.lwjgl.opengl.GL11.glDepthMask(true);

                com.mojang.blaze3d.opengl.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);
                com.qendolin.betterclouds.util.RenderHelper.bindTexture(client.getTextureManager().getTexture(com.qendolin.betterclouds.clouds.Resources.NOISE_TEXTURE));
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);

                com.mojang.blaze3d.opengl.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE2);
                com.qendolin.betterclouds.util.RenderHelper.bindTexture(client.getTextureManager().getTexture(com.qendolin.betterclouds.clouds.Resources.LIGHTING_TEXTURE));
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
                
                com.mojang.blaze3d.opengl.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);

                res.shader.setUniformFloat("u_is_fancy", fancy ? 1.0f : 0.0f);

                renderer.bindGraphicsPipeline(res.shader.getPipeline());
                for (net.vulkanmod.vulkan.shader.descriptor.UBO ubo : res.shader.getPipeline().getBuffers()) {
                    ubo.setUpdate(true);
                }
                renderer.uploadAndBindUBOs(res.shader.getPipeline());
                
                int vertsPerInstance = fancy ? 36 : 6;
                int verts = numClouds * vertsPerInstance;
                drawer.draw(res.expandedVbo, verts);
            }
        }
    }

    @Override
    public void close() {
        res.cleanUp();
    }
}

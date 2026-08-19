package com.qendolin.betterclouds.mixin.required;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.qendolin.betterclouds.BetterCloudsStatic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class WorldRendererLateMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void renderCloudsLate(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, CameraRenderState cameraRenderState, Matrix4fc positionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunkSections, CallbackInfo ci) {
        Runnable task = BetterCloudsStatic.getLateRenderTask();
        if (task != null) {
            task.run();
            BetterCloudsStatic.setLateRenderTask(null);
        }
    }
}

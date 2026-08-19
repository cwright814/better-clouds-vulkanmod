package com.qendolin.betterclouds.mixin.runtime;

import com.qendolin.betterclouds.shadow.CloudShadowMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class LightEngineMixin {
    private static final ThreadLocal<BlockPos> lastPos = new ThreadLocal<>();

    @Inject(method = "getState", at = @At("RETURN"))
    private void captureBlockPos(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        lastPos.set(pos);
    }

    @Inject(method = "getOpacity", at = @At("RETURN"), cancellable = true)
    private void injectCloudShadows(BlockState state, CallbackInfoReturnable<Integer> cir) {
        BlockPos pos = lastPos.get();
        if (pos == null) return;
        
        int cloudY = CloudShadowMap.cloudY;
        if (pos.getY() == cloudY) {
            int cloudOpacity = CloudShadowMap.getOpacity(pos.getX(), pos.getZ());
            if (cloudOpacity > 0) {
                // Return whichever is higher: the block's natural opacity, or the cloud's opacity
                cir.setReturnValue(Math.max(cir.getReturnValue(), cloudOpacity));
            }
        }
    }
}

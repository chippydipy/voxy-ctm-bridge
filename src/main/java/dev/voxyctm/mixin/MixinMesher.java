// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm.mixin;

import dev.voxyctm.MeshCtx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.cortex.voxy.client.core.rendering.building.RenderDataFactory$Mesher")
public abstract class MixinMesher {
    @Shadow
    public int axis;

    @Shadow
    protected abstract void emitQuad(int x, int z, int length, int width, long data);

    /** Quads tagged with a voxel position are re-emitted with the right connected-texture variant. */
    @Inject(method = "emitQuad", at = @At("HEAD"), cancellable = true)
    private void voxyctm$emit(int x, int z, int length, int width, long data, CallbackInfo ci) {
        if ((data & MeshCtx.MARK) == 0L) {
            return;
        }
        ci.cancel();
        this.emitQuad(x, z, length, width, MeshCtx.TL.get().resolve(this.axis, length, width, data));
    }
}

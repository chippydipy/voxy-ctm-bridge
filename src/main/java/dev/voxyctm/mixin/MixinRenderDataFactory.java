// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm.mixin;

import dev.voxyctm.CtmBaker;
import dev.voxyctm.CtmVariants;
import dev.voxyctm.MeshCtx;
import it.unimi.dsi.fastutil.ints.IntIterator;
import me.cortex.voxy.client.core.model.IdNotYetComputedException;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderDataFactory.class)
public abstract class MixinRenderDataFactory {
    @Shadow
    @Final
    private WorldEngine world;

    @Shadow
    @Final
    private ModelFactory modelMan;

    @Shadow
    @Final
    private long[] sectionData;

    @Shadow
    @Final
    private long[] neighboringFaces;

    @Inject(method = "generateMesh", at = @At("HEAD"))
    private void voxyctm$begin(WorldSection section, CallbackInfoReturnable<BuiltSection> cir) {
        // Only the finest level: coarser levels cover several blocks per voxel, so connections mean nothing there.
        boolean enable = section.lvl == 0 && CtmBaker.enabled() && CtmVariants.ready();
        MeshCtx.TL.get().begin(enable, this.modelMan, this.world.getMapper(), this.neighboringFaces);
    }

    @Inject(method = "prepareSectionData", at = @At("RETURN"))
    private void voxyctm$tag(long[] rawSectionData, CallbackInfoReturnable<Integer> cir) {
        MeshCtx ctx = MeshCtx.TL.get();
        if (!ctx.active || cir.getReturnValueI() < 0) {
            return;
        }
        ctx.markVoxels(rawSectionData, this.sectionData);
    }

    /** If any variant model was not ready yet, ask for all of them at once and make Voxy retry this section. */
    @Inject(method = "generateMesh", at = @At("RETURN"))
    private void voxyctm$finish(WorldSection section, CallbackInfoReturnable<BuiltSection> cir) {
        MeshCtx ctx = MeshCtx.TL.get();
        if (!ctx.active) {
            return;
        }
        ctx.active = false;
        if (ctx.missing.isEmpty()) {
            return;
        }
        int first = -1;
        IntIterator it = ctx.missing.iterator();
        while (it.hasNext()) {
            int id = it.nextInt();
            if (first < 0) {
                first = id;
            }
            CtmVariants.requestBake(id);
        }
        ctx.missing.clear();
        BuiltSection built = cir.getReturnValue();
        if (built != null) {
            built.free();
        }
        throw new IdNotYetComputedException(first, true);
    }
}

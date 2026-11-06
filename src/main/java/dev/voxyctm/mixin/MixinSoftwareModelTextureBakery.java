// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm.mixin;

import java.util.List;

import dev.voxyctm.Config;
import dev.voxyctm.CtmBaker;
import dev.voxyctm.CtmVariants;
import dev.voxyctm.PaneFilter;
import me.cortex.voxy.client.core.model.bakery.ReuseVertexConsumer;
import me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoftwareModelTextureBakery.class)
public abstract class MixinSoftwareModelTextureBakery {
    @Shadow
    @Final
    private ReuseVertexConsumer opaqueVC;

    @Shadow
    @Final
    private ReuseVertexConsumer translucentVC;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxyctm$hooked(CallbackInfo ci) {
        CtmVariants.bakeryHooked = true;
    }

    /**
     * Three jobs, everything else keeps Voxy's own path: bakes that belong to a connected-texture variant use
     * Continuity's quads for that neighbourhood, glass panes lose their thin strip faces, and leaves keep their
     * see-through gaps when a leaf mode is configured.
     */
    @Inject(method = "bakeBlockModel", at = @At("HEAD"), cancellable = true)
    private void voxyctm$bake(BlockState state, CallbackInfo ci) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        Config.Leaves leafMode = state.is(BlockTags.LEAVES) ? Config.leaves() : Config.Leaves.SOLID;
        boolean leaf = leafMode != Config.Leaves.SOLID;
        int[] ctx = CtmVariants.currentBake();
        boolean pane = PaneFilter.isPane(state);
        List<BakedQuad> quads = ctx != null ? CtmBaker.bakeVariant(state, ctx[0], ctx[1]) : null;
        if (quads == null) {
            if (!pane && !leaf) {
                return;
            }
            quads = CtmBaker.plainQuads(state);
            if (quads == null) {
                return;
            }
        }
        if (pane) {
            quads = PaneFilter.withoutThin(state, quads);
        }
        boolean leaves = state.is(BlockTags.LEAVES);
        for (BakedQuad quad : quads) {
            if (leaf) {
                // forceSolid=false keeps the texture's see-through pixels instead of filling them in
                (leafMode == Config.Leaves.TRANSLUCENT ? this.translucentVC : this.opaqueVC).quad(quad, false);
            } else {
                ReuseVertexConsumer target = quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT ? this.translucentVC : this.opaqueVC;
                target.quad(quad, leaves);
            }
        }
        ci.cancel();
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm.mixin;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

import dev.voxyctm.Config;
import dev.voxyctm.CtmVariants;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelFactory.class)
public abstract class MixinModelFactory {
    @Shadow
    @Final
    private int[] idMappings;

    @Shadow
    @Final
    private IntOpenHashSet blockStatesInFlight;

    @Shadow
    @Final
    private ReentrantLock blockStatesInFlightLock;

    @Shadow
    @Final
    private Mapper mapper;

    @SuppressWarnings("rawtypes")
    @Shadow
    @Final
    private ConcurrentLinkedDeque bakeQueue;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxyctm$hooked(CallbackInfo ci) {
        CtmVariants.reset();
        CtmVariants.factoryHooked = true;
    }

    /** Requests for synthetic variant ids are queued here instead of going through the block-state mapper. */
    @Inject(method = "addEntry", at = @At("HEAD"), cancellable = true)
    private void voxyctm$addVariant(int blockId, CallbackInfoReturnable<Boolean> cir) {
        if (blockId < CtmVariants.SYN_BASE) {
            return;
        }
        cir.setReturnValue(this.voxyctm$enqueueVariant(blockId));
    }

    @SuppressWarnings("unchecked")
    @Unique
    private boolean voxyctm$enqueueVariant(int synId) {
        if (this.idMappings[synId] != -1) {
            return false;
        }
        BlockState state = this.mapper.getBlockStateFromBlockId(CtmVariants.baseOf(synId));
        this.blockStatesInFlightLock.lock();
        try {
            if (!this.blockStatesInFlight.add(synId)) {
                return false;
            }
            if (this.idMappings[synId] != -1) {
                return false;
            }
            this.bakeQueue.add(CtmVariants.newBlockBake(synId, state));
            return true;
        } finally {
            this.blockStatesInFlightLock.unlock();
        }
    }

    /** Tells the bakery which variant (face + neighbour mask) the entry about to be baked belongs to. */
    @Inject(method = "processModelResult", at = @At("HEAD"))
    private void voxyctm$beforeBake(CallbackInfoReturnable<Boolean> cir) {
        Object head = this.bakeQueue.peek();
        int[] ctx = null;
        if (head != null) {
            int id = CtmVariants.blockIdOf(head);
            if (id >= CtmVariants.SYN_BASE) {
                ctx = new int[] {CtmVariants.faceOf(id), CtmVariants.maskOf(id)};
            }
        }
        CtmVariants.setBake(ctx);
    }

    @Inject(method = "processModelResult", at = @At("RETURN"))
    private void voxyctm$afterBake(CallbackInfoReturnable<Boolean> cir) {
        CtmVariants.setBake(null);
    }

    /**
     * Voxy forces every leaf block into the solid layer. When a leaf mode is configured, leaves get the
     * cutout or translucent layer instead (the bakery has already kept their see-through gaps).
     */
    @ModifyArgs(method = "processModelResult", at = @At(value = "INVOKE",
            target = "Lme/cortex/voxy/client/core/model/ModelFactory;processTextureBakeResult(ILnet/minecraft/world/level/block/state/BlockState;[Lme/cortex/voxy/client/core/model/ColourDepthTextureData;ZZLnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lme/cortex/voxy/client/core/model/ModelFactory$ModelBakeResultUpload;"))
    private void voxyctm$leafLayer(Args args) {
        Config.Leaves mode = Config.leaves();
        BlockState state = args.get(1);
        if (mode != Config.Leaves.SOLID && state.is(BlockTags.LEAVES)) {
            args.set(5, mode == Config.Leaves.TRANSLUCENT ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.CUTOUT);
        }
    }

    /**
     * Same-block faces of glass are culled because glass reports "skip rendering" towards itself. Leaves with
     * gaps would otherwise stop hiding the faces between neighbouring leaf blocks and flood the mesh, so in a
     * leaf mode they cull against themselves too.
     */
    @Redirect(method = "processTextureBakeResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;skipRendering(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean voxyctm$leafCullSame(BlockState self, BlockState other, Direction dir) {
        if (Config.leaves() != Config.Leaves.SOLID && self.is(BlockTags.LEAVES)) {
            return true;
        }
        return self.skipRendering(other, dir);
    }
}

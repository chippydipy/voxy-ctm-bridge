// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm.mixin;

import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import dev.voxyctm.CtmVariants;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.model.ModelFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBakerySubsystem.class)
public abstract class MixinModelBakerySubsystem {
    @Shadow
    @Final
    public ModelFactory factory;

    @Shadow
    @Final
    private Thread processingThread;

    @Shadow
    @Final
    private ReentrantLock enqueueLock;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxyctm$hooked(CallbackInfo ci) {
        CtmVariants.setSubsystem((ModelBakerySubsystem) (Object) this);
        CtmVariants.subsystemHooked = true;
    }

    /** Voxy rejects ids beyond its block-state count, so variant requests are handled here. */
    @Inject(method = "requestBlockBake", at = @At("HEAD"), cancellable = true)
    private void voxyctm$requestVariant(int blockId, CallbackInfo ci) {
        if (blockId < CtmVariants.SYN_BASE) {
            return;
        }
        ci.cancel();
        this.enqueueLock.lock();
        try {
            this.factory.addEntry(blockId);
        } finally {
            this.enqueueLock.unlock();
        }
        LockSupport.unpark(this.processingThread);
    }
}

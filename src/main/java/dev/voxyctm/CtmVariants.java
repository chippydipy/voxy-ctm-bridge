// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registry of connected-texture "variants".
 *
 * <p>A variant is (base block id, face, neighbour mask). Each gets a synthetic block id above Voxy's
 * real block ids, so Voxy's own model baking pipeline (queue, dedupe, upload) can handle it unchanged.
 * Synthetic ids never reach Voxy's database.
 */
public final class CtmVariants {
    public static final int SYN_BASE = 0xF0000;
    private static final int SYN_COUNT = 0x10000;
    private static final int GIVE_UP_AFTER = 400;

    public static volatile boolean factoryHooked;
    public static volatile boolean subsystemHooked;
    public static volatile boolean bakeryHooked;
    private static volatile ModelBakerySubsystem subsystem;

    private static final ConcurrentHashMap<Long, Integer> BY_KEY = new ConcurrentHashMap<>();
    private static final long[] KEY_BY_INDEX = new long[SYN_COUNT];
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ConcurrentHashMap<Integer, Integer> MISSES = new ConcurrentHashMap<>();
    /** 0 = unknown, 1 = not capable, 2 = capable; indexed by Voxy block id. */
    private static final byte[] CAPABLE = new byte[0x100000];

    private static final ThreadLocal<int[]> BAKE = new ThreadLocal<>();

    private static final Constructor<?> BAKE_CTOR;
    private static final Method BAKE_BLOCK_ID;

    static {
        Constructor<?> ctor = null;
        Method accessor = null;
        try {
            Class<?> c = Class.forName("me.cortex.voxy.client.core.model.ModelFactory$BlockBake");
            ctor = c.getDeclaredConstructor(int.class, BlockState.class);
            ctor.setAccessible(true);
            accessor = c.getDeclaredMethod("blockId");
            accessor.setAccessible(true);
        } catch (Throwable t) {
            ctor = null;
            accessor = null;
        }
        BAKE_CTOR = ctor;
        BAKE_BLOCK_ID = accessor;
    }

    private CtmVariants() {
    }

    public static boolean ready() {
        return BAKE_CTOR != null && BAKE_BLOCK_ID != null && factoryHooked && subsystemHooked && bakeryHooked
                && subsystem != null && CtmBaker.usable();
    }

    public static void setSubsystem(ModelBakerySubsystem s) {
        subsystem = s;
    }

    public static void requestBake(int synId) {
        ModelBakerySubsystem s = subsystem;
        if (s != null) {
            s.requestBlockBake(synId);
        }
    }

    /** Called when Voxy creates a fresh model factory (world join, resource reload). */
    public static void reset() {
        Config.reload();
        BY_KEY.clear();
        MISSES.clear();
        COUNTER.set(0);
        java.util.Arrays.fill(CAPABLE, (byte) 0);
    }

    public static boolean isSynthetic(int id) {
        return id >= SYN_BASE;
    }

    /** @return the synthetic id for this variant, or -1 if the id space is exhausted. */
    public static int variantId(int base, int face, int mask) {
        long key = ((long) base << 16) | ((long) face << 8) | mask;
        Integer existing = BY_KEY.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (BY_KEY) {
            existing = BY_KEY.get(key);
            if (existing != null) {
                return existing;
            }
            int n = COUNTER.get();
            if (n >= SYN_COUNT) {
                return -1;
            }
            COUNTER.set(n + 1);
            KEY_BY_INDEX[n] = key;
            int id = SYN_BASE + n;
            BY_KEY.put(key, id);
            return id;
        }
    }

    public static int baseOf(int synId) {
        return (int) (KEY_BY_INDEX[synId - SYN_BASE] >> 16);
    }

    public static int faceOf(int synId) {
        return (int) ((KEY_BY_INDEX[synId - SYN_BASE] >> 8) & 0xFF);
    }

    public static int maskOf(int synId) {
        return (int) (KEY_BY_INDEX[synId - SYN_BASE] & 0xFF);
    }

    /** True once a variant has been reported missing so often that we stop waiting for it. */
    public static boolean giveUp(int synId) {
        return MISSES.merge(synId, 1, Integer::sum) > GIVE_UP_AFTER;
    }

    public static boolean isCapable(int blockId, Mapper mapper) {
        byte c = CAPABLE[blockId];
        if (c == 0) {
            boolean capable = blockId != 0 && CtmBaker.isCapable(mapper.getBlockStateFromBlockId(blockId));
            CAPABLE[blockId] = (byte) (capable ? 2 : 1);
            return capable;
        }
        return c == 2;
    }

    /** Variant context for the bake currently running on this thread, as {face, mask}, or null. */
    public static int[] currentBake() {
        return BAKE.get();
    }

    public static void setBake(int[] ctx) {
        if (ctx == null) {
            BAKE.remove();
        } else {
            BAKE.set(ctx);
        }
    }

    public static Object newBlockBake(int blockId, BlockState state) {
        try {
            return BAKE_CTOR.newInstance(blockId, state);
        } catch (Throwable t) {
            throw new IllegalStateException("Cannot create Voxy bake entry", t);
        }
    }

    public static int blockIdOf(Object bake) {
        try {
            return (int) BAKE_BLOCK_ID.invoke(bake);
        } catch (Throwable t) {
            return -1;
        }
    }
}

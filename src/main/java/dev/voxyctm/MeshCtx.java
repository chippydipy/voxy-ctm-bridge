// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.model.ModelQueries;
import me.cortex.voxy.common.world.other.Mapper;

/**
 * Per-thread state for one section mesh. Voxels of connected-texture blocks are tagged with their own
 * position in spare bits of the quad word, which keeps them from being merged and lets us find the
 * owning voxel (and so its neighbours) when the mesher finally emits the quad.
 */
public final class MeshCtx {
    /** Spare bits of Voxy's quad word that the mesher carries along and strips on output. */
    public static final long MARK = 1L << 18;
    public static final long INDEX_MASK = 0x7FFFL << 3;
    public static final long SCRATCH = MARK | INDEX_MASK;
    private static final int UNKNOWN = -1;

    public static final ThreadLocal<MeshCtx> TL = ThreadLocal.withInitial(MeshCtx::new);

    public boolean active;
    private ModelFactory factory;
    private Mapper mapper;
    private long[] neighbors;
    private long[] raw;
    public final IntOpenHashSet missing = new IntOpenHashSet();
    private final int[] off = new int[3];

    public void begin(boolean enable, ModelFactory factory, Mapper mapper, long[] neighbors) {
        this.active = enable;
        this.factory = factory;
        this.mapper = mapper;
        this.neighbors = neighbors;
        this.raw = null;
        this.missing.clear();
    }

    public void markVoxels(long[] raw, long[] sectionData) {
        this.raw = raw;
        for (int i = 0; i < 32768; i++) {
            long block = raw[i];
            if (Mapper.isAir(block)) {
                continue;
            }
            long q = sectionData[i << 1];
            if (((q >>> 26) & 0xFFFFL) == 0L) {
                continue;
            }
            if (!CtmVariants.isCapable(Mapper.getBlockId(block), this.mapper)) {
                continue;
            }
            sectionData[i << 1] = q | MARK | ((long) i << 3);
        }
    }

    private int blockIdAt(int x, int y, int z) {
        boolean ox = x < 0 || x > 31;
        boolean oy = y < 0 || y > 31;
        boolean oz = z < 0 || z > 31;
        int outside = (ox ? 1 : 0) + (oy ? 1 : 0) + (oz ? 1 : 0);
        if (outside == 0) {
            return Mapper.getBlockId(this.raw[(y << 10) | (z << 5) | x]);
        }
        if (outside > 1) {
            return UNKNOWN;
        }
        int idx;
        if (ox) {
            idx = (x < 0 ? 0 : 1024) + ((y << 5) | z);
        } else if (oy) {
            idx = (y < 0 ? 2048 : 3072) + ((z << 5) | x);
        } else {
            idx = (z < 0 ? 4096 : 5120) + ((y << 5) | x);
        }
        return Mapper.getBlockId(this.neighbors[idx]);
    }

    private boolean sameBlock(int a, int b) {
        if (a == b) {
            return true;
        }
        if (a == 0 || b == 0) {
            return false;
        }
        return this.mapper.getBlockStateFromBlockId(a).getBlock() == this.mapper.getBlockStateFromBlockId(b).getBlock();
    }

    private int computeMask(int idx, int face, int base) {
        int x = idx & 31;
        int z = (idx >> 5) & 31;
        int y = (idx >> 10) & 31;
        int mask = 0;
        int unknown = 0;
        for (int k = 0; k < 8; k++) {
            Planes.offset(face, k, this.off);
            int id = this.blockIdAt(x + this.off[0], y + this.off[1], z + this.off[2]);
            if (id == UNKNOWN) {
                unknown |= 1 << k;
            } else if (this.sameBlock(base, id)) {
                mask |= 1 << k;
            }
        }
        // Across a section corner we cannot see the diagonal block: assume it connects when both
        // neighbouring edge blocks do.
        for (int k = 0; k < 8; k++) {
            if ((unknown & (1 << k)) != 0 && Planes.CORNER_EDGE_A[k] >= 0
                    && (mask & (1 << Planes.CORNER_EDGE_A[k])) != 0 && (mask & (1 << Planes.CORNER_EDGE_B[k])) != 0) {
                mask |= 1 << k;
            }
        }
        return mask;
    }

    /**
     * Turns a tagged quad word into the word to emit: tag removed and, when the variant is baked, the
     * variant's model id and quad type substituted.
     */
    public long resolve(int axis, int length, int width, long data) {
        long cleaned = data & ~SCRATCH;
        if (!this.active || length != 1 || width != 1) {
            return cleaned;
        }
        int idx = (int) ((data >>> 3) & 0x7FFFL);
        int face = (axis << 1) | (int) (data & 1L);
        int base = Mapper.getBlockId(this.raw[idx]);
        int mask = this.computeMask(idx, face, base);
        int syn = CtmVariants.variantId(base, face, mask);
        if (syn < 0) {
            return cleaned;
        }
        if (!this.factory.hasModelForBlockId(syn)) {
            if (!CtmVariants.giveUp(syn)) {
                this.missing.add(syn);
            }
            return cleaned;
        }
        int model = this.factory.getModelId(syn);
        long meta = this.factory.getModelMetadataFromClientId(model);
        long typing = 7L & (20L >> (int) (ModelQueries._isTranslucent(meta) * 6L + ModelQueries._isDoubleSided(meta) * 3L));
        long keep = cleaned & ~(0x3FFFFFFL | 0x3FFFC000000L);
        return keep | (cleaned & 1L) | typing | ((long) model << 26);
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm;

/**
 * Geometry shared by the fake world used for baking and the neighbour scan done while meshing.
 *
 * <p>Faces use Minecraft's {@code Direction.get3DDataValue()} order, which is also Voxy's face order:
 * 0 DOWN, 1 UP, 2 NORTH, 3 SOUTH, 4 WEST, 5 EAST. A "mask" has 8 bits, one for each of the eight
 * blocks around a face inside the plane of that face; a set bit means "that block connects".
 */
public final class Planes {
    /** In-plane axes per face, as axis indices (0=x, 1=y, 2=z). */
    public static final int[][] AXES = {{0, 2}, {0, 2}, {0, 1}, {0, 1}, {2, 1}, {2, 1}};
    /** Offsets along the two in-plane axes for each mask bit. */
    public static final int[][] OFFSETS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    public static final int ALL = 0xFF;

    private static final int[][] BIT_AT = new int[3][3];
    /** For corner bits: the two edge bits next to it, or -1 for edge bits. */
    public static final int[] CORNER_EDGE_A = new int[8];
    public static final int[] CORNER_EDGE_B = new int[8];

    static {
        for (int k = 0; k < 8; k++) {
            BIT_AT[OFFSETS[k][0] + 1][OFFSETS[k][1] + 1] = k;
        }
        BIT_AT[1][1] = -1;
        for (int k = 0; k < 8; k++) {
            int du = OFFSETS[k][0];
            int dv = OFFSETS[k][1];
            if (du != 0 && dv != 0) {
                CORNER_EDGE_A[k] = BIT_AT[du + 1][1];
                CORNER_EDGE_B[k] = BIT_AT[1][dv + 1];
            } else {
                CORNER_EDGE_A[k] = -1;
                CORNER_EDGE_B[k] = -1;
            }
        }
    }

    private Planes() {
    }

    /** Mask bit for an in-plane offset, or -1 for (0,0). */
    public static int bit(int du, int dv) {
        return BIT_AT[du + 1][dv + 1];
    }

    /** Writes the world-space offset (dx,dy,dz) of a mask bit for a given face into {@code out}. */
    public static void offset(int face, int bit, int[] out) {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[AXES[face][0]] = OFFSETS[bit][0];
        out[AXES[face][1]] = OFFSETS[bit][1];
    }
}

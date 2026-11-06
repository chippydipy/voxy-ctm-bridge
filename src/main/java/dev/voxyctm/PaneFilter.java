// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/**
 * Makes distant glass panes read as flat sheets of glass instead of thin translucent boxes.
 *
 * <p>Voxy bakes a pane by looking at it from all six sides, so the 2px thickness of the pane shows up as
 * thin strips: the top and bottom, and the end of every arm. This removes those caps. Thin faces that lie in
 * the plane of a glass sheet (the centre post's sides) are kept, because they close the gaps where sheets meet
 * at corners and junctions.
 *
 * <p>Controlled by {@code trimPanes} in the config file.
 */
public final class PaneFilter {
    /** A face whose shorter side is at most this fraction of a block (3/16) counts as thin. */
    static final float THIN = 3f / 16f + 1e-4f;

    private static final ConcurrentHashMap<Block, Boolean> IS_PANE = new ConcurrentHashMap<>();

    private PaneFilter() {
    }

    public static boolean isPane(BlockState state) {
        if (!Config.trimPanes()) {
            return false;
        }
        Block block = state.getBlock();
        return IS_PANE.computeIfAbsent(block, b -> b instanceof IronBarsBlock
                && BuiltInRegistries.BLOCK.getKey(b).getPath().contains("pane"));
    }

    /** True if this planar quad is a thin strip: its second-largest extent is no more than 3/16 of a block. */
    static boolean isThin(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3) {
        float minX = Math.min(Math.min(p0.x(), p1.x()), Math.min(p2.x(), p3.x()));
        float maxX = Math.max(Math.max(p0.x(), p1.x()), Math.max(p2.x(), p3.x()));
        float minY = Math.min(Math.min(p0.y(), p1.y()), Math.min(p2.y(), p3.y()));
        float maxY = Math.max(Math.max(p0.y(), p1.y()), Math.max(p2.y(), p3.y()));
        float minZ = Math.min(Math.min(p0.z(), p1.z()), Math.min(p2.z(), p3.z()));
        float maxZ = Math.max(Math.max(p0.z(), p1.z()), Math.max(p2.z(), p3.z()));
        float a = maxX - minX;
        float b = maxY - minY;
        float c = maxZ - minZ;
        float smallest = Math.min(a, Math.min(b, c));
        float largest = Math.max(a, Math.max(b, c));
        float middle = a + b + c - smallest - largest;
        return middle <= THIN;
    }

    /** Quads this close to the block boundary (in blocks) are end caps of arms. */
    static final float EDGE = 0.01f;

    /**
     * Whether a quad stays in the baked pane.
     *
     * @param thin   the quad is a thin strip (see {@link #isThin})
     * @param normal axis the quad faces along
     * @param plane  coordinate of the quad along that axis, in blocks (0..1)
     * @param armsX  the pane has an arm along the X axis (east or west connection)
     * @param armsZ  the pane has an arm along the Z axis (north or south connection)
     */
    static boolean keepQuad(boolean thin, Direction.Axis normal, float plane, boolean armsX, boolean armsZ) {
        if (!thin) {
            return true;
        }
        if (normal == Direction.Axis.Y) {
            return false; // top and bottom caps
        }
        if (plane <= EDGE || plane >= 1f - EDGE) {
            return false; // end cap of an arm, on the block boundary
        }
        if (!armsX && !armsZ) {
            return true; // a lone post: keep its sides
        }
        // A sheet along X faces +-Z and a sheet along Z faces +-X. Thin faces in the plane of a sheet fill the
        // junctions; thin faces pointing along a sheet are the inside of a cap and are dropped.
        return normal == Direction.Axis.Z ? armsX : armsZ;
    }

    /** The quads without the pane's caps; if that would leave nothing, the quads are returned unchanged. */
    public static List<BakedQuad> withoutThin(BlockState state, List<BakedQuad> quads) {
        boolean armsX = state.getValue(IronBarsBlock.EAST) || state.getValue(IronBarsBlock.WEST);
        boolean armsZ = state.getValue(IronBarsBlock.NORTH) || state.getValue(IronBarsBlock.SOUTH);
        List<BakedQuad> kept = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            Direction.Axis axis = q.direction().getAxis();
            boolean thin = isThin(q.position0(), q.position1(), q.position2(), q.position3());
            float plane = axis == Direction.Axis.X ? q.position0().x() : (axis == Direction.Axis.Z ? q.position0().z() : q.position0().y());
            if (keepQuad(thin, axis, plane, armsX, armsZ)) {
                kept.add(q);
            }
        }
        return kept.isEmpty() ? quads : kept;
    }
}

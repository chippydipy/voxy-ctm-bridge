// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A fake world for asking a block model what it looks like on one face, given which of the eight
 * blocks around that face (in the face's plane) connect to it.
 *
 * <p>Everything in front of the face is air, everything behind it is the block itself, and the
 * blocks in the plane are the block itself or air according to the mask.
 */
final class FaceLevel implements BlockAndTintGetter {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private BlockState state = AIR;
    private int nx, ny, nz;
    private int axis1, axis2;
    private int mask;

    void configure(BlockState state, Direction face, int mask) {
        this.state = state;
        this.nx = face.getStepX();
        this.ny = face.getStepY();
        this.nz = face.getStepZ();
        int f = face.get3DDataValue();
        this.axis1 = Planes.AXES[f][0];
        this.axis2 = Planes.AXES[f][1];
        this.mask = mask;
    }

    private static int comp(BlockPos pos, int axis) {
        return axis == 0 ? pos.getX() : (axis == 1 ? pos.getY() : pos.getZ());
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int dot = this.nx * pos.getX() + this.ny * pos.getY() + this.nz * pos.getZ();
        if (dot >= 1) {
            return AIR;
        }
        if (dot <= -1) {
            return this.state;
        }
        int c1 = Math.max(-1, Math.min(1, comp(pos, this.axis1)));
        int c2 = Math.max(-1, Math.min(1, comp(pos, this.axis2)));
        int bit = Planes.bit(c1, c2);
        if (bit < 0) {
            return this.state;
        }
        return ((this.mask >> bit) & 1) != 0 ? this.state : AIR;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 0;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return -1;
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinY() {
        return -64;
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Asks Continuity (through the Fabric Rendering API) what a block looks like for a given neighbourhood. */
public final class CtmBaker {
    private static final Logger LOG = LoggerFactory.getLogger("voxy-ctm-bridge");
    static final boolean DEBUG = Boolean.getBoolean("voxyctm.debug");
    private static final int MAX_FAILURES = 16;
    private static final Direction[] FACES = Direction.values();
    private static final Direction[] FACES_AND_NULL = new Direction[FACES.length + 1];

    static {
        System.arraycopy(FACES, 0, FACES_AND_NULL, 0, FACES.length);
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static volatile int failures;

    private static final class Scratch {
        final MutableMesh mesh = Renderer.get().mutableMesh();
        final FaceLevel level = new FaceLevel();
    }

    private CtmBaker() {
    }

    private static void fail(String what, BlockState state, Throwable t) {
        int n = ++failures;
        LOG.warn("Connected-texture {} failed for {} (failure {}/{})", what, state, n, MAX_FAILURES, t);
        if (n >= MAX_FAILURES) {
            LOG.error("Too many failures, voxy-ctm-bridge is disabled for this session");
        }
    }

    /** True if connected-texture edges are switched on (config file, JVM arguments). */
    public static boolean enabled() {
        return Config.connectedTextures();
    }

    static boolean usable() {
        return enabled() && failures < MAX_FAILURES;
    }

    /** True if connected textures can change how this state looks (so it needs per-neighbour variants). */
    public static boolean isCapable(BlockState state) {
        if (!usable()) {
            return false;
        }
        try {
            if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof StairBlock
                    || !state.getFluidState().isEmpty() || state.getRenderShape() == RenderShape.INVISIBLE) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            Map<TextureAtlasSprite, Integer> plain = plainSprites(model);
            int[] none = new int[6];
            int[] all = {Planes.ALL, Planes.ALL, Planes.ALL, Planes.ALL, Planes.ALL, Planes.ALL};
            boolean capable = !spritesOf(emit(state, model, none)).equals(plain) || !spritesOf(emit(state, model, all)).equals(plain);
            if (DEBUG) {
                LOG.info("CTM capable? {} -> {}", state, capable);
            }
            return capable;
        } catch (Throwable t) {
            fail("capability check", state, t);
            return false;
        }
    }

    /**
     * Quads for the whole model where face {@code face} uses neighbour mask {@code mask} and every other
     * face is baked as fully connected. Returns null to keep Voxy's normal bake.
     */
    public static List<BakedQuad> bakeVariant(BlockState state, int face, int mask) {
        if (!usable()) {
            return null;
        }
        try {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            int[] masks = {Planes.ALL, Planes.ALL, Planes.ALL, Planes.ALL, Planes.ALL, Planes.ALL};
            masks[face] = mask;
            List<BakedQuad> quads = emit(state, model, masks);
            return quads.isEmpty() ? null : quads;
        } catch (Throwable t) {
            fail("variant bake", state, t);
            return null;
        }
    }

    /** The quads Voxy would bake for this state by itself (same order and seed), or null on failure. */
    public static List<BakedQuad> plainQuads(BlockState state) {
        try {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(new SingleThreadedRandomSource(42L), parts);
            List<BakedQuad> out = new ArrayList<>();
            for (BlockStateModelPart part : parts) {
                for (Direction dir : FACES_AND_NULL) {
                    out.addAll(part.getQuads(dir));
                }
            }
            return out;
        } catch (Throwable t) {
            fail("plain bake", state, t);
            return null;
        }
    }

    private static Map<TextureAtlasSprite, Integer> plainSprites(BlockStateModel model) {
        Map<TextureAtlasSprite, Integer> plain = new IdentityHashMap<>();
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(new SingleThreadedRandomSource(42L), parts);
        for (BlockStateModelPart part : parts) {
            for (Direction dir : FACES_AND_NULL) {
                for (BakedQuad quad : part.getQuads(dir)) {
                    plain.merge(quad.materialInfo().sprite(), 1, Integer::sum);
                }
            }
        }
        return plain;
    }

    private static Map<TextureAtlasSprite, Integer> spritesOf(List<BakedQuad> quads) {
        Map<TextureAtlasSprite, Integer> m = new IdentityHashMap<>();
        for (BakedQuad q : quads) {
            m.merge(q.materialInfo().sprite(), 1, Integer::sum);
        }
        return m;
    }

    /** One emit pass per face, each with its own neighbour mask; keeps the quads that belong to that face. */
    private static List<BakedQuad> emit(BlockState state, BlockStateModel model, int[] faceMasks) {
        Minecraft mc = Minecraft.getInstance();
        SpriteFinder finder = ((FabricTextureAtlas) (Object) mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)).spriteFinder();
        FabricBlockStateModel fabricModel = (FabricBlockStateModel) (Object) model;
        Scratch scratch = SCRATCH.get();
        List<BakedQuad> out = new ArrayList<>();
        for (Direction face : FACES) {
            scratch.level.configure(state, face, faceMasks[face.get3DDataValue()]);
            scratch.mesh.clear();
            fabricModel.emitQuads(scratch.mesh.emitter(), scratch.level, BlockPos.ZERO, state,
                    new SingleThreadedRandomSource(42L), dir -> false);
            scratch.mesh.forEach(q -> {
                Direction lf = q.lightFace();
                if (lf == face || (lf == null && face == Direction.DOWN)) {
                    out.add(q.toBakedQuad(finder.find(q)));
                }
            });
        }
        scratch.mesh.clear();
        return out;
    }
}

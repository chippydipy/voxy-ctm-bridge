# Voxy CTM Bridge

A small client-side Fabric mod that makes **Voxy's distant terrain (LODs) show Continuity's connected textures**, so far-away glass, and other blocks your resource pack connects, get proper edges instead of looking flat, empty or framed block by block. It also hides the thin strip faces of glass panes on LODs.

> Not affiliated with Voxy, Continuity or Sodium. This repository contains none of their code or assets.

## What it does

- **Connected-texture edges on LODs.** For each exposed face of a connected-texture block on Voxy's finest LOD level, the mod looks at the eight blocks around that face and uses the tile Continuity would pick for that neighbourhood. Edges appear only where a run of glass (or other connected block) ends.
- **Works with any resource pack.** Nothing is pack-specific: the mod asks Continuity what the active pack makes each block look like. Switch packs, reload (F3+T) and it re-asks.
- **Flat glass panes at distance.** Voxy bakes a pane by looking at it from six sides, so its 2px thickness shows up as thin strips and the pane looks like a translucent box. The mod drops the pane's top, bottom and end caps so it reads as a flat sheet of glass, and keeps the faces in the plane of the glass so junctions and corners stay closed. This part works without Continuity.
- **See-through leaves.** Voxy fills in the gaps of leaf textures and draws leaves as solid blocks. By default (`leaves=cutout`) leaves keep their gaps on LODs; `translucent` draws them in the glass pass, `solid` restores Voxy's own look.

Everything else keeps Voxy's normal look. (Leaves are the one default change: set `leaves=solid` to keep Voxy's own look for them too.)

## Requirements

| | Version tested |
|---|---|
| Minecraft | 26.2 (Fabric) |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.161.0 |
| Voxy | **0.2.19-beta** (exact; the mod hooks Voxy internals and refuses to load on other versions) |
| Sodium | 0.9.1 (required by Voxy) |
| Continuity | 3.0.1 (needed for connected textures; the pane trimming works without it) |

Client-side only. Do not install it on a server.

## Install

Put the jar in your `mods` folder next to Voxy, Sodium, Fabric API and Continuity.

## Configuration

On first run the mod creates `config/voxy-ctm-bridge.properties`. Changes apply after a resource reload (F3+T) or a restart.

| Option | Values | Default | Effect |
|---|---|---|---|
| `leaves` | `cutout`, `translucent`, `solid` | `cutout` | `cutout` keeps the see-through gaps of the leaf texture in Voxy's opaque pass, like in-game leaves. `translucent` keeps the gaps and draws leaves in the same pass as glass. `solid` is Voxy's own look (opaque leaves). |
| `trimPanes` | `true`, `false` | `true` | Make glass panes read as flat sheets by dropping their top, bottom and end caps on LODs |
| `connectedTextures` | `true`, `false` | `true` | Draw Continuity's connected-texture edges on LODs |

JVM arguments override the file: `-Dvoxyctm.leaves=cutout`, `-Dvoxyctm.trimPanes=false`, `-Dvoxyctm.connectedTextures=false`. The older `-Dvoxyctm.disable=true` (everything off) and `-Dvoxyctm.keepPaneEdges=true` still work. `-Dvoxyctm.debug=true` logs each block state treated as connectable.

## Limitations

- Edges are only drawn on Voxy's finest LOD level. Coarser levels cover several blocks per cell, so connecting has no meaning there and they keep Voxy's normal look.
- Blocks connect to any block of the same type; pack rules that connect only on exact block state may differ slightly.
- Across a 32-block section corner the diagonal neighbour is not visible, so it is assumed to connect when both neighbouring edge blocks do.
- Random and repeating tile patterns from a pack show one fixed tile at LOD distance.
- Unless `leaves=solid`, faces between neighbouring leaf blocks of the same type are culled (like glass), so you see through the gaps to the outer leaves on the far side, not through layers of interior leaves. This keeps the quad count in check.
- Translucent leaves are blended like glass and a shader pack may treat them differently from cutout leaves; try both modes.
- Because it patches Voxy internals, any Voxy update can break it until the mod is updated.

## How it works (short version)

1. Voxy bakes one texture per block state, with no knowledge of neighbours. The mod gives each *(block, face, neighbour mask)* its own synthetic block id so Voxy's own bake queue can bake it, using a fake neighbourhood and the Fabric Rendering API `emitQuads` path that Continuity hooks. Identical-looking tiles share one model.
2. While Voxy meshes a section, connected-texture voxels are tagged with their own position in spare bits of the quad word, which stops them merging and lets the mod find each quad's owning voxel when the mesher emits it.
3. At emit time the mod reads the face's eight neighbours (including across section borders), picks the variant, and swaps in its model id. Voxy's face culling is untouched because it only compares the original model ids. If a variant isn't baked yet, the section is retried through Voxy's normal missing-model path.

## Building

Needs JDK 25. There is no Gradle project yet; the build is plain `javac`.

1. Create `classpath.txt` in the repo root: a single line with these jars separated by `:` (`;` on Windows): the Minecraft 26.2 client jar, the Minecraft libraries (JOML, LWJGL, fastutil, Guava, SLF4J, ...), Sponge Mixin, Fabric Loader, Voxy 0.2.19-beta, Sodium 0.9.1 and the Fabric API modules inside Fabric API and Sodium (`META-INF/jars/` in those jars), plus Continuity.
2. `./build.sh` produces `build/voxy-ctm-bridge-<version>.jar`.

## License

GPL-3.0-only, see `LICENSE`. Anyone who distributes a modified version must also publish its source under the same license.

Voxy is licensed All Rights Reserved by its author; this project only references it at compile time and through mixins and contains none of its code. Continuity (LGPL-3.0), Sodium and Fabric API are likewise only referenced, not included.

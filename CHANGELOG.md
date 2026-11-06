# Changelog

## 0.3.2
- Licensed under GPL-3.0-only; mod metadata now lists the author and repository links.
- Pane trimming no longer removes the faces that close corners and junctions. Only a pane's top, bottom and end caps (and the faces pointing along a sheet) are dropped; faces in the plane of the glass are kept, which fixes gaps where panes meet.

## 0.3.1
- `leaves` now defaults to `cutout`. Set `leaves=solid` in the config to restore Voxy's own opaque leaves. (An existing config file keeps whatever value it already has.)

## 0.3.0
- New config file `config/voxy-ctm-bridge.properties` (options for leaves, pane trimming and connected textures). The old JVM arguments still work.
- New `leaves` option: `solid` (Voxy default), `cutout` or `translucent`, so leaves keep their see-through gaps on LODs.

## 0.2.1
- Glass panes no longer draw their thin strip faces (top and bottom caps, end caps, centre post) on LODs, so distant panes don't show long thin lines. Disable with `-Dvoxyctm.keepPaneEdges=true`.

## 0.2.0
- Connected-texture edges on the finest LOD level: each exposed face looks at the eight blocks around it and uses the tile Continuity would pick for that neighbourhood.

## 0.1.0
- First version: baked every block as if surrounded by the same block (fully connected look). Superseded, because the centre tile of connected glass is nearly transparent.

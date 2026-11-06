// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 chippydipy

package dev.voxyctm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings from {@code config/voxy-ctm-bridge.properties} (created with defaults on first run).
 * Re-read whenever Voxy rebuilds its renderer, i.e. on a resource reload (F3+T) or on world join.
 * JVM arguments {@code -Dvoxyctm.leaves=}, {@code -Dvoxyctm.trimPanes=} and {@code -Dvoxyctm.connectedTextures=}
 * override the file; the older {@code -Dvoxyctm.disable=true} and {@code -Dvoxyctm.keepPaneEdges=true} still work.
 */
public final class Config {
    private static final Logger LOG = LoggerFactory.getLogger("voxy-ctm-bridge");
    private static final String FILE_NAME = "voxy-ctm-bridge.properties";

    public enum Leaves {
        /** Voxy's own look: leaves are opaque, gaps in the texture are filled in. */
        SOLID,
        /** Leaves keep their see-through gaps and stay in Voxy's opaque pass (alpha tested, like in-game leaves). */
        CUTOUT,
        /** Leaves keep their see-through gaps and are drawn in the same pass as glass. */
        TRANSLUCENT;

        static Leaves parse(String s, Leaves fallback) {
            if (s == null) {
                return fallback;
            }
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown leaves mode '{}', expected solid, cutout or translucent", s);
                return fallback;
            }
        }
    }

    private static volatile Leaves leaves = Leaves.CUTOUT;
    private static volatile boolean trimPanes = true;
    private static volatile boolean connectedTextures = true;

    private Config() {
    }

    public static Leaves leaves() {
        return leaves;
    }

    public static boolean trimPanes() {
        return trimPanes;
    }

    public static boolean connectedTextures() {
        return connectedTextures;
    }

    /** Reads the config file from the game directory (creating it if missing). */
    public static synchronized void reload() {
        Path dir;
        Minecraft mc = Minecraft.getInstance();
        dir = (mc != null ? mc.gameDirectory.toPath() : Path.of(".")).resolve("config");
        loadFrom(dir.resolve(FILE_NAME));
    }

    static synchronized void loadFrom(Path file) {
        Properties p = new Properties();
        try {
            if (Files.exists(file)) {
                try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    p.load(in);
                }
            } else {
                writeDefaults(file);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not read {}, using defaults", file, e);
        }
        Leaves l = Leaves.parse(p.getProperty("leaves"), Leaves.CUTOUT);
        boolean trim = bool(p.getProperty("trimPanes"), true);
        boolean ctm = bool(p.getProperty("connectedTextures"), true);

        // JVM argument overrides
        l = Leaves.parse(System.getProperty("voxyctm.leaves"), l);
        trim = bool(System.getProperty("voxyctm.trimPanes"), trim);
        ctm = bool(System.getProperty("voxyctm.connectedTextures"), ctm);
        if (Boolean.getBoolean("voxyctm.keepPaneEdges")) {
            trim = false;
        }
        if (Boolean.getBoolean("voxyctm.disable")) {
            ctm = false;
            trim = false;
            l = Leaves.SOLID;
        }
        leaves = l;
        trimPanes = trim;
        connectedTextures = ctm;
        LOG.info("Config: leaves={}, trimPanes={}, connectedTextures={}", l, trim, ctm);
    }

    private static boolean bool(String s, boolean fallback) {
        if (s == null) {
            return fallback;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.equals("true")) {
            return true;
        }
        if (t.equals("false")) {
            return false;
        }
        return fallback;
    }

    private static void writeDefaults(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                # Voxy CTM Bridge
                # Changes apply after a resource reload (F3+T) or a restart.

                # leaves: how leaves are drawn on Voxy's LODs.
                #   cutout      - (default) keeps the see-through gaps, drawn in Voxy's opaque pass (like in-game leaves)
                #   translucent - keeps the see-through gaps, drawn in the same pass as glass
                #   solid       - Voxy's own look: opaque leaves, gaps in the texture are filled in
                leaves=cutout

                # trimPanes: hide the thin strip faces of glass panes on LODs (true/false)
                trimPanes=true

                # connectedTextures: draw Continuity's connected-texture edges on LODs (true/false)
                connectedTextures=true
                """, StandardCharsets.UTF_8);
    }
}

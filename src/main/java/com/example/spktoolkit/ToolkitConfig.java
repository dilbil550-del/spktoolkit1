package com.example.spktoolkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ToolkitConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean invulnerable;
    public boolean infiniteHealth;
    public boolean autoRegen;
    public boolean infiniteHunger;
    public boolean flight;
    public boolean freeCamera;
    public boolean fullbright;
    public boolean xray;
    public boolean entityHighlight;
    public boolean freezeTime;
    public boolean alwaysDay;
    public boolean keepInventory;
    public boolean showHud = true;

    public double flightSpeedMultiplier = 1.0;
    public double speedMultiplier = 1.0;
    public double jumpMultiplier = 1.0;

    public int itemQuantity = 1;
    public boolean maxStack;
    public String selectedItem = "minecraft:stone";
    public String itemSearch = "";

    public String selectedEntity = "minecraft:zombie";
    public String entitySearch = "";

    public String xrayFilter = "";

    public String lastCategory = "Player";

    public List<Waypoint> waypoints = new ArrayList<>();

    // Runtime restore baselines. They are intentionally stored in the same JSON so a
    // relaunch while a toggle is ON can still restore the player's previous state.
    public Boolean baselineInvulnerable;
    public Boolean baselineAllowFlying;
    public Boolean baselineFlying;
    public Float baselineFlySpeed;
    public String baselineGameMode;
    public Double baselineGamma;
    public Boolean baselineDaylightCycle;
    public Boolean baselineKeepInventory;
    public String baselineWorldKey;

    public void sanitize() {
        flightSpeedMultiplier = clamp(flightSpeedMultiplier, 0.1, 16.0, 1.0);
        speedMultiplier = clamp(speedMultiplier, 0.1, 8.0, 1.0);
        jumpMultiplier = clamp(jumpMultiplier, 0.1, 8.0, 1.0);
        itemQuantity = Math.max(1, Math.min(itemQuantity, 64));
        selectedItem = cleanId(selectedItem, "minecraft:stone");
        selectedEntity = cleanId(selectedEntity, "minecraft:zombie");
        itemSearch = clampString(itemSearch, 80);
        entitySearch = clampString(entitySearch, 80);
        xrayFilter = clampString(xrayFilter, 2000);
        lastCategory = clampString(lastCategory, 32);
        if (waypoints == null) waypoints = new ArrayList<>();

        List<Waypoint> cleaned = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint == null) continue;
            waypoint.sanitize();
            if (!waypoint.name.isBlank()) cleaned.add(waypoint);
        }
        cleaned.sort(Comparator.comparing(w -> w.name.toLowerCase()));
        if (cleaned.size() > 64) cleaned = new ArrayList<>(cleaned.subList(0, 64));
        waypoints = cleaned;
    }

    private static String clampString(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String cleanId(String value, String fallback) {
        if (value == null || value.isBlank() || value.length() > 200) return fallback;
        return value;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    public static ToolkitConfig load(Path path) {
        try {
            if (!Files.exists(path)) return new ToolkitConfig();
            try (Reader reader = Files.newBufferedReader(path)) {
                ToolkitConfig config = GSON.fromJson(reader, ToolkitConfig.class);
                if (config == null) config = new ToolkitConfig();
                config.sanitize();
                return config;
            }
        } catch (IOException | RuntimeException ex) {
            try {
                Path broken = path.resolveSibling(path.getFileName() + ".broken-" + Instant.now().toEpochMilli() + ".json");
                Files.move(path, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Best-effort backup; fall back to defaults either way.
            }
            return new ToolkitConfig();
        }
    }

    public void save(Path path) {
        sanitize();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(this, writer);
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // The mod must remain usable even if config storage temporarily fails.
        }
    }

    public void clearRuntimeBaselines() {
        baselineInvulnerable = null;
        baselineAllowFlying = null;
        baselineFlying = null;
        baselineFlySpeed = null;
        baselineGameMode = null;
        baselineGamma = null;
        baselineDaylightCycle = null;
        baselineKeepInventory = null;
        baselineWorldKey = null;
    }

    public static final class Waypoint {
        public String name;
        public String dimension;
        public double x;
        public double y;
        public double z;

        public Waypoint() {
        }

        public Waypoint(String name, String dimension, double x, double y, double z) {
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void sanitize() {
            name = name == null ? "" : clampString(name.trim(), 40);
            dimension = cleanId(dimension, "minecraft:overworld");
            if (!Double.isFinite(x)) x = 0;
            if (!Double.isFinite(y)) y = 64;
            if (!Double.isFinite(z)) z = 0;
        }
    }
}

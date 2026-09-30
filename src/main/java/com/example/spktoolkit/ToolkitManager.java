package com.example.spktoolkit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ToolkitManager {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Identifier SPEED_MODIFIER_ID = Identifier.of(ToolkitMod.MOD_ID, "speed");
    private static final Identifier JUMP_MODIFIER_ID = Identifier.of(ToolkitMod.MOD_ID, "jump");

    private static ToolkitConfig config;
    private static PathHolder pathHolder;
    private static String activeWorldKey;
    private static String lastPlayerUuid;
    private static boolean runtimeWasSingleplayer;
    private static String status = "Ready";
    private static long statusUntil;

    private ToolkitManager() {
    }

    public static void init() {
        pathHolder = new PathHolder(CLIENT);
        config = ToolkitConfig.load(pathHolder.path);
    }

    public static ToolkitConfig config() {
        if (config == null) init();
        return config;
    }

    public static String status() {
        return statusUntil > System.currentTimeMillis() ? status : "";
    }

    public static boolean isSingleplayer(MinecraftClient client) {
        return client != null
                && client.isInSingleplayer()
                && client.isIntegratedServerRunning()
                && client.getServer() != null
                && client.world != null
                && client.player != null;
    }

    public static void tick(MinecraftClient client) {
        if (config == null) init();

        if (!isSingleplayer(client)) {
            if (runtimeWasSingleplayer) {
                restoreLocalRuntimeState();
                runtimeWasSingleplayer = false;
            }
            // Multiplayer receives no menu/action handling at all.
            return;
        }

        runtimeWasSingleplayer = true;
        ensureWorldBaseline(client);

        if (ToolkitMod.menuKey.wasPressed()) ToolkitMod.openMenu();
        if (ToolkitMod.flightKey.wasPressed()) toggleFlight();
        if (ToolkitMod.fullbrightKey.wasPressed()) toggleFullbright();
        if (ToolkitMod.freeCameraKey.wasPressed()) toggleFreeCamera();
        if (ToolkitMod.invulnerabilityKey.wasPressed()) toggleInvulnerability();

        applyPlayerToggles(client);
        applyWorldToggles(client);
    }

    public static void renderHud(net.minecraft.client.gui.DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (config == null || !config.showHud || !isSingleplayer(CLIENT)) return;
        TextRenderer tr = CLIENT.textRenderer;
        PlayerEntity player = CLIENT.player;
        if (player == null || CLIENT.world == null) return;

        int y = 8;
        String coords = String.format(Locale.ROOT, "XYZ %.1f / %.1f / %.1f", player.getX(), player.getY(), player.getZ());
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        String biome = CLIENT.world.getBiome(player.getBlockPos()).getKey()
                .map(key -> key.getValue().toString()).orElse("unknown");
        context.drawTextWithShadow(tr, Text.literal("Singleplayer Toolkit"), 8, y, 0xFFFFFF);
        y += 11;
        context.drawTextWithShadow(tr, Text.literal(coords), 8, y, 0xFFFFFF);
        y += 11;
        context.drawTextWithShadow(tr, Text.literal("Biome: " + biome), 8, y, 0xFFFFFF);
        y += 11;
        context.drawTextWithShadow(tr, Text.literal("Dim: " + dimension), 8, y, 0xFFFFFF);
        y += 11;
        context.drawTextWithShadow(tr, Text.literal("FPS: " + CLIENT.getCurrentFps()), 8, y, 0xFFFFFF);
        if (!status().isBlank()) {
            y += 13;
            context.drawTextWithShadow(tr, Text.literal(status()), 8, y, 0xFFFF55);
        }
    }

    private static void ensureWorldBaseline(MinecraftClient client) {
        String worldKey = worldKey(client);
        String playerUuid = client.player.getUuidAsString();
        if (!Objects.equals(activeWorldKey, worldKey) || !Objects.equals(lastPlayerUuid, playerUuid)) {
            activeWorldKey = worldKey;
            lastPlayerUuid = playerUuid;

            // A stored baseline only belongs to the world/player it was captured from.
            if (!Objects.equals(config.baselineWorldKey, worldKey)) {
                config.baselineInvulnerable = null;
                config.baselineAllowFlying = null;
                config.baselineFlying = null;
                config.baselineFlySpeed = null;
                config.baselineGameMode = null;
                config.baselineDaylightCycle = null;
                config.baselineKeepInventory = null;
                config.baselineWorldKey = worldKey;
            }
            if (config.flight) captureFlightBaseline(client.player);
            if (config.invulnerable) captureInvulnerabilityBaseline(client.player);
            if (config.freeCamera) captureGameModeBaseline(client.player);
            if (config.freezeTime || config.alwaysDay) captureDaylightCycleBaseline(client);
            if (config.keepInventory) captureKeepInventoryBaseline(client);
            if (config.fullbright) captureGammaBaseline(client);
            config.save(pathHolder.path);
        }
    }

    private static void applyPlayerToggles(MinecraftClient client) {
        ServerPlayerEntity serverPlayer = serverPlayer(client);
        if (serverPlayer == null) return;

        if (config.fullbright) {
            captureGammaBaseline(client);
            client.options.getGamma().setValue(1.0);
        } else {
            restoreGamma(client);
        }

        if (config.invulnerable) {
            captureInvulnerabilityBaseline(serverPlayer);
            serverPlayer.getAbilities().invulnerable = true;
        } else {
            restoreInvulnerability(serverPlayer);
        }

        if (config.flight) {
            captureFlightBaseline(serverPlayer);
            serverPlayer.getAbilities().allowFlying = true;
            serverPlayer.getAbilities().flying = true;
            serverPlayer.getAbilities().flySpeed = (float) (0.05 * config.flightSpeedMultiplier);
            serverPlayer.sendAbilitiesUpdate();
        } else {
            restoreFlight(serverPlayer);
        }

        if (config.infiniteHealth && serverPlayer.getHealth() < serverPlayer.getMaxHealth()) {
            serverPlayer.setHealth(serverPlayer.getMaxHealth());
        }
        if (config.autoRegen && !serverPlayer.isDead()) {
            serverPlayer.heal(Math.max(0.1f, serverPlayer.getMaxHealth() * 0.01f));
        }
        if (config.infiniteHunger) {
            serverPlayer.getHungerManager().setFoodLevel(20);
            serverPlayer.getHungerManager().setSaturationLevel(20.0f);
        }

        updateAttributeModifier(serverPlayer, "MOVEMENT_SPEED", SPEED_MODIFIER_ID, config.speedMultiplier, config.speedMultiplier != 1.0);
        updateAttributeModifier(serverPlayer, "JUMP_STRENGTH", JUMP_MODIFIER_ID, config.jumpMultiplier, config.jumpMultiplier != 1.0);
    }

    private static void applyWorldToggles(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        ServerWorld world = server == null ? null : server.getWorld(client.world.getRegistryKey());
        if (world == null) return;

        if (config.freezeTime || config.alwaysDay) {
            captureDaylightCycleBaseline(client);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        } else {
            restoreDaylightCycle(client);
        }

        if (config.alwaysDay) world.getLevelProperties().setTimeOfDay(1000L);

        if (config.keepInventory) {
            captureKeepInventoryBaseline(client);
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
        } else {
            restoreKeepInventory(client);
        }
    }

    public static void toggleFlight() {
        if (!isSingleplayer(CLIENT)) return;
        config.flight = !config.flight;
        if (config.flight) setStatus("Flight ON"); else setStatus("Flight OFF");
        save();
    }

    public static void toggleInvulnerability() {
        if (!isSingleplayer(CLIENT)) return;
        config.invulnerable = !config.invulnerable;
        setStatus("Invulnerability " + onOff(config.invulnerable));
        save();
    }

    public static void toggleFullbright() {
        if (!isSingleplayer(CLIENT)) return;
        config.fullbright = !config.fullbright;
        if (!config.fullbright) restoreGamma(CLIENT);
        setStatus("Fullbright " + onOff(config.fullbright));
        save();
    }

    public static void toggleFreeCamera() {
        if (!isSingleplayer(CLIENT)) return;
        config.freeCamera = !config.freeCamera;
        if (config.freeCamera) {
            captureGameModeBaseline(CLIENT.player);
            if (!setGameMode(CLIENT.player, "SPECTATOR")) {
                config.freeCamera = false;
                setStatus("Free camera unavailable: spectator mode API not found");
            } else {
                setStatus("Free camera = spectator equivalent");
            }
        } else {
            restoreGameMode(CLIENT.player);
            setStatus("Free camera OFF");
        }
        save();
    }

    public static void restoreEverything() {
        if (isSingleplayer(CLIENT)) {
            config.invulnerable = false;
            config.infiniteHealth = false;
            config.autoRegen = false;
            config.infiniteHunger = false;
            config.flight = false;
            config.freeCamera = false;
            config.fullbright = false;
            config.xray = false;
            config.entityHighlight = false;
            config.freezeTime = false;
            config.alwaysDay = false;
            config.keepInventory = false;
            config.speedMultiplier = 1.0;
            config.jumpMultiplier = 1.0;
            config.flightSpeedMultiplier = 1.0;
            restoreLocalRuntimeState();
        }
        config.clearRuntimeBaselines();
        save();
        setStatus("Everything OFF; saved settings restored");
    }

    private static void restoreLocalRuntimeState() {
        ServerPlayerEntity player = serverPlayer(CLIENT);
        if (player != null) {
            restoreInvulnerability(player);
            restoreFlight(player);
            removeAttributeModifier(player, "MOVEMENT_SPEED", SPEED_MODIFIER_ID);
            removeAttributeModifier(player, "JUMP_STRENGTH", JUMP_MODIFIER_ID);
            if (config.baselineGameMode != null) restoreGameMode(player);
        }
        // Gamma is client-side and can be restored even after the world has closed.
        restoreGamma(CLIENT);
        if (isSingleplayer(CLIENT)) {
            restoreDaylightCycle(CLIENT);
            restoreKeepInventory(CLIENT);
        }
        config.invulnerable = false;
        config.infiniteHealth = false;
        config.autoRegen = false;
        config.infiniteHunger = false;
        config.flight = false;
        config.freeCamera = false;
        config.fullbright = false;
        config.xray = false;
        config.entityHighlight = false;
        config.freezeTime = false;
        config.alwaysDay = false;
        config.keepInventory = false;
        config.speedMultiplier = 1.0;
        config.jumpMultiplier = 1.0;
        config.flightSpeedMultiplier = 1.0;
        activeWorldKey = null;
        lastPlayerUuid = null;
    }

    private static void captureInvulnerabilityBaseline(PlayerEntity player) {
        if (config.baselineInvulnerable == null) config.baselineInvulnerable = player.getAbilities().invulnerable;
    }

    private static void restoreInvulnerability(PlayerEntity player) {
        if (config.baselineInvulnerable != null) {
            player.getAbilities().invulnerable = config.baselineInvulnerable;
            if (player instanceof ServerPlayerEntity serverPlayer) serverPlayer.sendAbilitiesUpdate();
            config.baselineInvulnerable = null;
        }
    }

    private static void captureFlightBaseline(PlayerEntity player) {
        if (config.baselineAllowFlying == null) config.baselineAllowFlying = player.getAbilities().allowFlying;
        if (config.baselineFlying == null) config.baselineFlying = player.getAbilities().flying;
        if (config.baselineFlySpeed == null) config.baselineFlySpeed = player.getAbilities().flySpeed;
    }

    private static void restoreFlight(PlayerEntity player) {
        boolean changed = false;
        if (config.baselineAllowFlying != null) {
            player.getAbilities().allowFlying = config.baselineAllowFlying;
            config.baselineAllowFlying = null;
            changed = true;
        }
        if (config.baselineFlying != null) {
            player.getAbilities().flying = config.baselineFlying;
            config.baselineFlying = null;
            changed = true;
        }
        if (config.baselineFlySpeed != null) {
            player.getAbilities().flySpeed = config.baselineFlySpeed;
            config.baselineFlySpeed = null;
            changed = true;
        }
        if (changed && player instanceof ServerPlayerEntity serverPlayer) serverPlayer.sendAbilitiesUpdate();
    }

    private static void captureGameModeBaseline(ServerPlayerEntity player) {
        if (config.baselineGameMode == null) {
            String current = getGameModeName(player);
            if (current != null) config.baselineGameMode = current;
        }
    }

    private static void restoreGameMode(ServerPlayerEntity player) {
        if (config.baselineGameMode != null) {
            setGameMode(player, config.baselineGameMode);
            config.baselineGameMode = null;
        }
    }

    private static void captureGammaBaseline(MinecraftClient client) {
        if (config.baselineGamma == null) config.baselineGamma = client.options.getGamma().getValue();
    }

    private static void restoreGamma(MinecraftClient client) {
        if (config.baselineGamma != null) {
            client.options.getGamma().setValue(config.baselineGamma);
            config.baselineGamma = null;
        }
    }

    private static void captureDaylightCycleBaseline(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null) return;
        ServerWorld world = server.getWorld(client.world.getRegistryKey());
        if (world == null) return;
        if (config.baselineDaylightCycle == null) {
            config.baselineDaylightCycle = world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).get();
        }
        config.baselineWorldKey = worldKey(client);
    }

    private static void restoreDaylightCycle(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        if (server == null || config.baselineDaylightCycle == null || client.world == null) return;
        ServerWorld world = server.getWorld(client.world.getRegistryKey());
        if (world != null) world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(config.baselineDaylightCycle, server);
        config.baselineDaylightCycle = null;
    }

    private static void captureKeepInventoryBaseline(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null) return;
        ServerWorld world = server.getWorld(client.world.getRegistryKey());
        if (world == null) return;
        if (config.baselineKeepInventory == null) {
            config.baselineKeepInventory = world.getGameRules().get(GameRules.KEEP_INVENTORY).get();
        }
        config.baselineWorldKey = worldKey(client);
    }

    private static void restoreKeepInventory(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        if (server == null || config.baselineKeepInventory == null || client.world == null) return;
        ServerWorld world = server.getWorld(client.world.getRegistryKey());
        if (world != null) world.getGameRules().get(GameRules.KEEP_INVENTORY).set(config.baselineKeepInventory, server);
        config.baselineKeepInventory = null;
    }

    public static void setToggle(String name, boolean value) {
        if (!isSingleplayer(CLIENT)) return;
        switch (name) {
            case "invulnerability" -> config.invulnerable = value;
            case "infiniteHealth" -> config.infiniteHealth = value;
            case "autoRegen" -> config.autoRegen = value;
            case "infiniteHunger" -> config.infiniteHunger = value;
            case "flight" -> config.flight = value;
            case "freeCamera" -> {
                if (value != config.freeCamera) toggleFreeCamera();
                return;
            }
            case "fullbright" -> {
                if (value != config.fullbright) toggleFullbright();
                return;
            }
            case "xray" -> config.xray = value;
            case "entityHighlight" -> config.entityHighlight = value;
            case "freezeTime" -> config.freezeTime = value;
            case "alwaysDay" -> config.alwaysDay = value;
            case "keepInventory" -> config.keepInventory = value;
            case "showHud" -> config.showHud = value;
            default -> { return; }
        }
        save();
    }

    public static void adjustMultiplier(String key, double delta) {
        switch (key) {
            case "flight" -> config.flightSpeedMultiplier = clamp(config.flightSpeedMultiplier + delta, 0.1, 16);
            case "speed" -> config.speedMultiplier = clamp(config.speedMultiplier + delta, 0.1, 8);
            case "jump" -> config.jumpMultiplier = clamp(config.jumpMultiplier + delta, 0.1, 8);
            default -> { return; }
        }
        save();
    }

    public static void setItemQuantity(String value) {
        try {
            config.itemQuantity = Math.max(1, Math.min(64, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            config.itemQuantity = 1;
        }
        save();
    }

    public static int maxStackForSelectedItem() {
        try {
            Item item = Registries.ITEM.get(Identifier.of(config.selectedItem));
            return Math.max(1, item.getDefaultStack().getMaxCount());
        } catch (RuntimeException ex) {
            return 64;
        }
    }

    public static void giveSelectedItem() {
        if (!isSingleplayer(CLIENT)) return;
        ServerPlayerEntity player = serverPlayer(CLIENT);
        if (player == null) return;
        Item item;
        try {
            item = Registries.ITEM.get(Identifier.of(config.selectedItem));
        } catch (RuntimeException ex) {
            setStatus("Unknown item");
            return;
        }
        int count = config.maxStack ? maxStackForSelectedItem() : config.itemQuantity;
        ItemStack stack = item.getDefaultStack();
        stack.setCount(Math.max(1, Math.min(count, stack.getMaxCount())));
        player.giveItemStack(stack);
        setStatus("Gave " + stack.getCount() + " x " + config.selectedItem);
    }

    public static List<Identifier> filteredItemIds(String search, int page, int pageSize) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Identifier> all = Registries.ITEM.getIds().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .filter(id -> q.isBlank() || id.toString().toLowerCase(Locale.ROOT).contains(q))
                .collect(Collectors.toCollection(ArrayList::new));
        int from = Math.min(all.size(), Math.max(0, page) * pageSize);
        return all.subList(from, Math.min(all.size(), from + pageSize));
    }

    public static List<Identifier> filteredEntityIds(String search, int page, int pageSize) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Identifier> all = Registries.ENTITY_TYPE.getIds().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .filter(id -> q.isBlank() || id.toString().toLowerCase(Locale.ROOT).contains(q))
                .filter(id -> {
                    try {
                        return MobEntity.class.isAssignableFrom(Registries.ENTITY_TYPE.get(id).getBaseClass());
                    } catch (RuntimeException ex) {
                        return false;
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));
        int from = Math.min(all.size(), Math.max(0, page) * pageSize);
        return all.subList(from, Math.min(all.size(), from + pageSize));
    }

    public static void spawnSelectedEntity() {
        if (!isSingleplayer(CLIENT)) return;
        ServerPlayerEntity player = serverPlayer(CLIENT);
        if (player == null || CLIENT.getServer() == null) return;
        ServerWorld world = player.getServerWorld();
        try {
            EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(config.selectedEntity));
            Entity entity = type.create(world, null, player.getBlockPos(), net.minecraft.entity.SpawnReason.COMMAND, true, false);
            if (entity == null) {
                setStatus("Cannot spawn " + config.selectedEntity);
                return;
            }
            world.spawnEntity(entity);
            setStatus("Spawned " + config.selectedEntity);
        } catch (RuntimeException ex) {
            setStatus("Spawn failed: " + config.selectedEntity);
        }
    }

    public static void clearNearbyHostiles() {
        if (!isSingleplayer(CLIENT)) return;
        ServerPlayerEntity player = serverPlayer(CLIENT);
        if (player == null) return;
        ServerWorld world = player.getServerWorld();
        double radius = 32.0;
        List<HostileEntity> mobs = world.getEntitiesByClass(HostileEntity.class,
                player.getBoundingBox().expand(radius), Entity::isAlive);
        int removed = 0;
        for (HostileEntity mob : mobs) {
            if (mob != player) {
                mob.discard();
                removed++;
            }
        }
        setStatus("Removed " + removed + " nearby hostile mobs");
    }

    public static void setTime(long time) {
        if (!isSingleplayer(CLIENT) || CLIENT.world == null) return;
        MinecraftServer server = CLIENT.getServer();
        ServerWorld world = server.getWorld(CLIENT.world.getRegistryKey());
        if (world != null) world.getLevelProperties().setTimeOfDay(time);
        setStatus("Time set to " + time);
    }

    public static void setWeather(String weather) {
        if (!isSingleplayer(CLIENT) || CLIENT.world == null) return;
        ServerWorld world = CLIENT.getServer().getWorld(CLIENT.world.getRegistryKey());
        if (world == null) return;
        switch (weather) {
            case "clear" -> world.setWeather(6000, 0, false, false);
            case "rain" -> world.setWeather(0, 6000, true, false);
            case "thunder" -> world.setWeather(0, 6000, true, true);
            default -> { return; }
        }
        setStatus("Weather: " + weather);
    }

    public static boolean teleport(String xs, String ys, String zs) {
        if (!isSingleplayer(CLIENT) || CLIENT.player == null) return false;
        try {
            double x = parseCoord(xs, "X");
            double y = parseCoord(ys, "Y");
            double z = parseCoord(zs, "Z");
            if (y < -2048 || y > 2048) throw new IllegalArgumentException("Y out of safe range");
            ServerPlayerEntity serverPlayer = serverPlayer(CLIENT);
            if (serverPlayer == null) throw new IllegalStateException("server player unavailable");
            serverPlayer.requestTeleport(x, y, z);
            setStatus(String.format(Locale.ROOT, "Teleported to %.2f, %.2f, %.2f", x, y, z));
            return true;
        } catch (RuntimeException ex) {
            setStatus("Teleport rejected: use finite numeric X/Y/Z");
            return false;
        }
    }

    private static double parseCoord(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " missing");
        double d = Double.parseDouble(value.trim());
        if (!Double.isFinite(d)) throw new IllegalArgumentException(label + " not finite");
        if (Math.abs(d) > 30_000_000) throw new IllegalArgumentException(label + " outside world border coordinate range");
        return d;
    }

    public static void saveWaypoint(String name) {
        if (!isSingleplayer(CLIENT) || CLIENT.player == null) return;
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isBlank()) { setStatus("Waypoint needs a name"); return; }
        cleanName = cleanName.length() > 40 ? cleanName.substring(0, 40) : cleanName;
        String dimension = CLIENT.player.getWorld().getRegistryKey().getValue().toString();
        ToolkitConfig.Waypoint existing = config.waypoints.stream()
                .filter(w -> w.name.equalsIgnoreCase(cleanName)).findFirst().orElse(null);
        if (existing == null) {
            config.waypoints.add(new ToolkitConfig.Waypoint(cleanName, dimension,
                    CLIENT.player.getX(), CLIENT.player.getY(), CLIENT.player.getZ()));
        } else {
            existing.dimension = dimension;
            existing.x = CLIENT.player.getX();
            existing.y = CLIENT.player.getY();
            existing.z = CLIENT.player.getZ();
        }
        save();
        setStatus("Waypoint saved: " + cleanName);
    }

    public static void renameWaypoint(String oldName, String newName) {
        ToolkitConfig.Waypoint waypoint = findWaypoint(oldName);
        String clean = newName == null ? "" : newName.trim();
        if (waypoint == null || clean.isBlank()) return;
        if (clean.length() > 40) clean = clean.substring(0, 40);
        waypoint.name = clean;
        save();
        setStatus("Waypoint renamed");
    }

    public static void deleteWaypoint(String name) {
        ToolkitConfig.Waypoint waypoint = findWaypoint(name);
        if (waypoint == null) return;
        config.waypoints.remove(waypoint);
        save();
        setStatus("Waypoint deleted");
    }

    public static boolean teleportWaypoint(ToolkitConfig.Waypoint waypoint) {
        if (!isSingleplayer(CLIENT) || waypoint == null || CLIENT.player == null) return false;
        String dimension = CLIENT.player.getWorld().getRegistryKey().getValue().toString();
        if (!Objects.equals(dimension, waypoint.dimension)) {
            setStatus("Waypoint is in another dimension");
            return false;
        }
        ServerPlayerEntity serverPlayer = serverPlayer(CLIENT);
        if (serverPlayer == null) return false;
        serverPlayer.requestTeleport(waypoint.x, waypoint.y, waypoint.z);
        setStatus("Teleported to " + waypoint.name);
        return true;
    }

    public static ToolkitConfig.Waypoint findWaypoint(String name) {
        if (name == null) return null;
        return config.waypoints.stream().filter(w -> w.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static void teleportToSpawn() {
        if (!isSingleplayer(CLIENT) || CLIENT.player == null || CLIENT.world == null) return;
        BlockPos spawn = CLIENT.world.getSpawnPos();
        ServerPlayerEntity serverPlayer = serverPlayer(CLIENT);
        if (serverPlayer == null) return;
        serverPlayer.requestTeleport(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        setStatus("Teleported to world spawn");
    }

    public static void save() {
        if (config != null && pathHolder != null) config.save(pathHolder.path);
    }

    public static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    public static String worldKey(MinecraftClient client) {
        if (client.world == null) return "unknown";
        String dim = client.world.getRegistryKey().getValue().toString();
        String level = client.world.getLevelProperties().getLevelName();
        return dim + "|" + level;
    }

    private static ServerPlayerEntity serverPlayer(MinecraftClient client) {
        if (client.getServer() == null || client.player == null) return null;
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void setStatus(String message) {
        status = message;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    private static void updateAttributeModifier(ServerPlayerEntity player, String fieldName, Identifier id, double multiplier, boolean enabled) {
        EntityAttributeInstance instance = attribute(player, fieldName);
        if (instance == null) return;
        if (!enabled || Math.abs(multiplier - 1.0) < 0.0001) {
            removeAttributeModifier(player, fieldName, id);
            return;
        }
        removeAttributeModifier(player, fieldName, id);
        try {
            Class<?> modifierClass = Class.forName("net.minecraft.entity.attribute.EntityAttributeModifier");
            Class<?> operationClass = Class.forName("net.minecraft.entity.attribute.EntityAttributeModifier$Operation");
            @SuppressWarnings("unchecked") Object op = Enum.valueOf((Class<? extends Enum>) operationClass.asSubclass(Enum.class), "ADD_MULTIPLIED_BASE");
            Object modifier = constructModifier(modifierClass, id, multiplier - 1.0, op);
            if (modifier == null) return;
            Method add = instance.getClass().getMethod("addTemporaryModifier", modifierClass);
            add.invoke(instance, modifier);
        } catch (ReflectiveOperationException ignored) {
            setStatus("Speed/jump modifier unavailable in this Yarn build");
        }
    }

    private static void removeAttributeModifier(ServerPlayerEntity player, String fieldName, Identifier id) {
        EntityAttributeInstance instance = attribute(player, fieldName);
        if (instance == null) return;
        try {
            for (Method method : instance.getClass().getMethods()) {
                if (!method.getName().equals("removeModifier") || method.getParameterCount() != 1) continue;
                Class<?> p = method.getParameterTypes()[0];
                if (p.isAssignableFrom(Identifier.class)) {
                    method.invoke(instance, id);
                    return;
                }
                if (p.getName().equals("net.minecraft.entity.attribute.EntityAttributeModifier")) {
                    try {
                        Method get = instance.getClass().getMethod("getModifier", Identifier.class);
                        Object modifier = get.invoke(instance, id);
                        if (modifier != null) method.invoke(instance, modifier);
                    } catch (ReflectiveOperationException ignored) {
                        // No-op; absent modifier is harmless.
                    }
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Best-effort only.
        }
    }

    private static Object constructModifier(Class<?> modifierClass, Identifier id, double amount, Object op) {
        for (Constructor<?> constructor : modifierClass.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            try {
                if (types.length == 3 && types[0].isAssignableFrom(Identifier.class)
                        && (types[1] == double.class || types[1] == Double.class)
                        && types[2].isInstance(op)) {
                    return constructor.newInstance(id, amount, op);
                }
                if (types.length == 4 && (types[0] == java.util.UUID.class || types[0].getName().equals("java.util.UUID"))
                        && types[2] == double.class && types[3].isInstance(op)) {
                    return constructor.newInstance(java.util.UUID.nameUUIDFromBytes(id.toString().getBytes()), "spktoolkit", amount, op);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next constructor shape.
            }
        }
        return null;
    }

    private static EntityAttributeInstance attribute(ServerPlayerEntity player, String fieldName) {
        try {
            Class<?> holder = Class.forName("net.minecraft.entity.attribute.EntityAttributes");
            Field field = holder.getField(fieldName);
            Object attribute = field.get(null);
            if (attribute instanceof EntityAttribute entityAttribute) {
                return player.getAttributeInstance(entityAttribute);
            }
        } catch (ReflectiveOperationException ignored) {
            // Try registry-key form below.
        }
        try {
            Identifier id = switch (fieldName) {
                case "MOVEMENT_SPEED" -> Identifier.of("minecraft", "movement_speed");
                case "JUMP_STRENGTH" -> Identifier.of("minecraft", "jump_strength");
                default -> null;
            };
            if (id == null) return null;
            EntityAttribute entityAttribute = Registries.ATTRIBUTE.get(id);
            return player.getAttributeInstance(entityAttribute);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String getGameModeName(ServerPlayerEntity player) {
        try {
            Object manager = findInteractionManager(player);
            if (manager == null) return null;
            for (Method method : manager.getClass().getMethods()) {
                if (method.getName().equals("getGameMode") && method.getParameterCount() == 0) {
                    Object result = method.invoke(manager);
                    return result instanceof Enum<?> e ? e.name() : null;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static boolean setGameMode(ServerPlayerEntity player, String name) {
        try {
            Object manager = findInteractionManager(player);
            if (manager == null) return false;
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("changeGameMode") || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (!parameter.isEnum()) continue;
                @SuppressWarnings("unchecked") Object value = Enum.valueOf((Class<? extends Enum>) parameter.asSubclass(Enum.class), name);
                Object result = method.invoke(manager, value);
                return !(result instanceof Boolean b) || b;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
        return false;
    }

    private static Object findInteractionManager(ServerPlayerEntity player) {
        for (Field field : player.getClass().getFields()) {
            if (field.getType().getName().contains("ServerPlayerInteractionManager")) {
                try {
                    return field.get(player);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        for (Field field : player.getClass().getDeclaredFields()) {
            if (field.getType().getName().contains("ServerPlayerInteractionManager")) {
                try {
                    field.setAccessible(true);
                    return field.get(player);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    private static final class PathHolder {
        final java.nio.file.Path path;
        PathHolder(MinecraftClient client) {
            this.path = client.runDirectory.toPath().resolve("config").resolve("spktoolkit.json");
        }
    }
}

package com.example.spktoolkit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ToolkitScreen extends Screen {
    private static final String[] CATEGORIES = {
            "Player", "Movement", "World", "Visual", "Items", "Teleport", "Utilities", "Settings"
    };
    private static final int ROWS = 7;
    private static final int ITEM_PAGE_SIZE = ROWS;
    private static final int ENTITY_PAGE_SIZE = ROWS;

    private String category;
    private int page;
    private TextFieldWidget itemSearch;
    private TextFieldWidget entitySearch;
    private TextFieldWidget quantity;
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;
    private TextFieldWidget waypointName;
    private TextFieldWidget waypointRename;
    private String selectedWaypoint;

    public ToolkitScreen() {
        super(Text.literal(ToolkitMod.MOD_NAME));
        this.category = ToolkitManager.config().lastCategory;
        if (!isValidCategory(category)) category = "Player";
    }

    @Override
    protected void init() {
        clearChildren();
        page = 0;
        int left = Math.max(8, (width - 780) / 2);
        int top = 48;

        int catWidth = Math.max(74, Math.min(92, (width - 24) / CATEGORIES.length));
        for (int i = 0; i < CATEGORIES.length; i++) {
            final String name = CATEGORIES[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> selectCategory(name))
                    .dimensions(left + i * catWidth, 20, catWidth - 4, 20).build());
        }

        int panelWidth = Math.min(780, width - 16);
        int contentLeft = Math.max(8, (width - panelWidth) / 2);
        int contentRight = contentLeft + panelWidth;

        switch (category) {
            case "Player" -> initPlayer(contentLeft, top, panelWidth);
            case "Movement" -> initMovement(contentLeft, top, panelWidth);
            case "World" -> initWorld(contentLeft, top, panelWidth);
            case "Visual" -> initVisual(contentLeft, top, panelWidth);
            case "Items" -> initItems(contentLeft, top, panelWidth);
            case "Teleport" -> initTeleport(contentLeft, top, panelWidth);
            case "Utilities" -> initUtilities(contentLeft, top, panelWidth);
            case "Settings" -> initSettings(contentLeft, top, panelWidth);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(contentRight - 88, height - 28, 88, 20).build());
    }

    private void initPlayer(int x, int y, int w) {
        y = toggleButton(x, y, w, "Invulnerability", "invulnerability");
        y = toggleButton(x, y, w, "Infinite health", "infiniteHealth");
        y = toggleButton(x, y, w, "Auto-regeneration", "autoRegen");
        y = toggleButton(x, y, w, "Infinite hunger", "infiniteHunger");
        y = button(x, y, w, "Fire/lava + drowning + fall/suffocation: covered by invulnerability", null, false);
    }

    private void initMovement(int x, int y, int w) {
        y = toggleButton(x, y, w, "Flight", "flight");
        y = controlRow(x, y, w, "Flight speed ×" + fmt(ToolkitManager.config().flightSpeedMultiplier), "flight", 0.25);
        y = controlRow(x, y, w, "Move speed ×" + fmt(ToolkitManager.config().speedMultiplier), "speed", 0.10);
        y = controlRow(x, y, w, "Jump strength ×" + fmt(ToolkitManager.config().jumpMultiplier), "jump", 0.10);
        y = toggleButton(x, y, w, "Free camera / spectator equivalent", "freeCamera");
    }

    private void initWorld(int x, int y, int w) {
        y = labelBlock(x, y, w, "Time presets");
        int bw = (w - 12) / 3;
        addButton(x, y, bw, "Dawn", b -> ToolkitManager.setTime(0));
        addButton(x + bw + 6, y, bw, "Day", b -> ToolkitManager.setTime(1000));
        addButton(x + (bw + 6) * 2, y, bw, "Noon", b -> ToolkitManager.setTime(6000));
        y += 24;
        addButton(x, y, bw, "Sunset", b -> ToolkitManager.setTime(12000));
        addButton(x + bw + 6, y, bw, "Night", b -> ToolkitManager.setTime(13000));
        addButton(x + (bw + 6) * 2, y, bw, "Midnight", b -> ToolkitManager.setTime(18000));
        y += 30;

        y = labelBlock(x, y, w, "Weather");
        addButton(x, y, bw, "Clear", b -> ToolkitManager.setWeather("clear"));
        addButton(x + bw + 6, y, bw, "Rain", b -> ToolkitManager.setWeather("rain"));
        addButton(x + (bw + 6) * 2, y, bw, "Thunder", b -> ToolkitManager.setWeather("thunder"));
        y += 30;

        y = toggleButton(x, y, w, "Freeze time", "freezeTime");
        y = toggleButton(x, y, w, "Always day", "alwaysDay");
        y = toggleButton(x, y, w, "Keep inventory", "keepInventory");
    }

    private void initVisual(int x, int y, int w) {
        y = toggleButton(x, y, w, "Fullbright", "fullbright");
        y = button(x, y, w, "X-ray: not included (renderer mixin deliberately omitted)", null, false);
        y = button(x, y, w, "Entity highlight: not included (renderer mixin deliberately omitted)", null, false);
        y = toggleButton(x, y, w, "Free camera / spectator equivalent", "freeCamera");
        y = button(x, y, w, "X-ray visible-block filter: unsupported until renderer-safe mixins are added", null, false);
    }

    private void initItems(int x, int y, int w) {
        itemSearch = new TextFieldWidget(textRenderer, x, y, w - 112, 20, Text.literal("Search items"));
        itemSearch.setText(ToolkitManager.config().itemSearch);
        addDrawableChild(itemSearch);
        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), b -> {
            ToolkitManager.config().itemSearch = itemSearch.getText();
            page = 0;
            ToolkitManager.save();
            rebuild();
        }).dimensions(x + w - 104, y, 96, 20).build());
        y += 24;

        quantity = new TextFieldWidget(textRenderer, x, y, 100, 20, Text.literal("Qty"));
        quantity.setText(Integer.toString(ToolkitManager.config().itemQuantity));
        addDrawableChild(quantity);
        addDrawableChild(ButtonWidget.builder(Text.literal("Set quantity"), b -> {
            ToolkitManager.setItemQuantity(quantity.getText());
            setStatusFromManager();
            rebuild();
        }).dimensions(x + 106, y, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Max stack: " + ToolkitManager.onOff(ToolkitManager.config().maxStack)), b -> {
            ToolkitManager.config().maxStack = !ToolkitManager.config().maxStack;
            ToolkitManager.save();
            rebuild();
        }).dimensions(x + 232, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Give selected"), b -> ToolkitManager.giveSelectedItem())
                .dimensions(x + 388, y, 140, 20).build());
        y += 27;

        List<Identifier> ids = ToolkitManager.filteredItemIds(ToolkitManager.config().itemSearch, page, ITEM_PAGE_SIZE);
        for (Identifier id : ids) {
            String label = id.toString().equals(ToolkitManager.config().selectedItem) ? "[SELECTED] " : "";
            label += id.toString();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                ToolkitManager.config().selectedItem = id.toString();
                ToolkitManager.save();
                rebuild();
            }).dimensions(x, y, w - 80, 20).build());
            y += 25;
        }
        addPageControls(x, y, w, "items");
    }

    private void initTeleport(int x, int y, int w) {
        xField = field(x, y, 128, "X");
        yField = field(x + 134, y, 128, "Y");
        zField = field(x + 268, y, 128, "Z");
        addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"), b -> {
            ToolkitManager.teleport(xField.getText(), yField.getText(), zField.getText());
            rebuild();
        }).dimensions(x + 402, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("World spawn"), b -> {
            ToolkitManager.teleportToSpawn();
            rebuild();
        }).dimensions(x + 508, y, 100, 20).build());
        y += 27;

        waypointName = new TextFieldWidget(textRenderer, x, y, 220, 20, Text.literal("Waypoint name"));
        addDrawableChild(waypointName);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save current"), b -> {
            ToolkitManager.saveWaypoint(waypointName.getText());
            rebuild();
        }).dimensions(x + 226, y, 120, 20).build());
        waypointRename = new TextFieldWidget(textRenderer, x + 352, y, 190, 20, Text.literal("Rename selected"));
        addDrawableChild(waypointRename);
        addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), b -> {
            ToolkitManager.renameWaypoint(selectedWaypoint, waypointRename.getText());
            selectedWaypoint = waypointRename.getText();
            rebuild();
        }).dimensions(x + 548, y, 90, 20).build());
        y += 27;

        List<ToolkitConfig.Waypoint> waypoints = new ArrayList<>(ToolkitManager.config().waypoints);
        for (ToolkitConfig.Waypoint wp : waypoints) {
            String mark = wp.name.equalsIgnoreCase(selectedWaypoint == null ? "" : selectedWaypoint) ? "* " : "";
            String text = String.format(Locale.ROOT, "%s%s  %.1f, %.1f, %.1f (%s)", mark, wp.name, wp.x, wp.y, wp.z, wp.dimension);
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {
                selectedWaypoint = wp.name;
                if (waypointName != null) waypointName.setText(wp.name);
                if (waypointRename != null) waypointRename.setText(wp.name);
                rebuild();
            }).dimensions(x, y, w - 210, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("TP"), b -> {
                ToolkitManager.teleportWaypoint(wp);
                rebuild();
            }).dimensions(x + w - 202, y, 60, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> confirmDeleteWaypoint(wp.name))
                    .dimensions(x + w - 136, y, 70, 20).build());
            y += 24;
            if (y > height - 55) break;
        }
    }

    private void initUtilities(int x, int y, int w) {
        y = toggleButton(x, y, w, "HUD (FPS / coords / biome / dimension)", "showHud");
        y = button(x, y, w, "Clear nearby hostile mobs (32 blocks)", b -> confirmClearHostiles());
        y = labelBlock(x, y, w, "Spawn mobs from the real entity registry");

        entitySearch = new TextFieldWidget(textRenderer, x, y, w - 112, 20, Text.literal("Search mobs"));
        entitySearch.setText(ToolkitManager.config().entitySearch);
        addDrawableChild(entitySearch);
        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), b -> {
            ToolkitManager.config().entitySearch = entitySearch.getText();
            page = 0;
            ToolkitManager.save();
            rebuild();
        }).dimensions(x + w - 104, y, 96, 20).build());
        y += 24;

        List<Identifier> ids = ToolkitManager.filteredEntityIds(ToolkitManager.config().entitySearch, page, ENTITY_PAGE_SIZE);
        for (Identifier id : ids) {
            String label = id.toString().equals(ToolkitManager.config().selectedEntity) ? "[SELECTED] " : "";
            label += id.toString();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                ToolkitManager.config().selectedEntity = id.toString();
                ToolkitManager.save();
                rebuild();
            }).dimensions(x, y, w - 120, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Spawn"), b -> ToolkitManager.spawnSelectedEntity())
                    .dimensions(x + w - 114, y, 108, 20).build());
            y += 25;
        }
        addPageControls(x, y, w, "entities");
        y += 38;
        button(x, y, w, "Pause world: not included (screen stays non-pausing by design)", null, false);
        y += 25;
        button(x, y, w, "Instant crop growth: not included (avoids mass world edits)", null, false);
    }

    private void initSettings(int x, int y, int w) {
        y = button(x, y, w, "Turn everything OFF (restore normal)", b -> confirmRestoreEverything());
        y = button(x, y, w, "Reset config to defaults", b -> confirmResetConfig());
        y = button(x, y, w, "Config file: .minecraft/config/spktoolkit.json", null, false);
        y = button(x, y, w, "Controls: Options → Controls → Singleplayer Toolkit", null, false);
        y = button(x, y, w, "Multiplayer safety: every action is blocked outside an integrated singleplayer world", null, false);
        y = labelBlock(x, y, w, "Unsupported on purpose: X-ray and true detached free-camera renderer effects require renderer mixins. Free camera uses spectator mode instead.");
    }

    private TextFieldWidget field(int x, int y, int width, String label) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.literal(label));
        addDrawableChild(field);
        return field;
    }

    private int toggleButton(int x, int y, int w, String label, String key) {
        boolean value = switch (key) {
            case "invulnerability" -> ToolkitManager.config().invulnerable;
            case "infiniteHealth" -> ToolkitManager.config().infiniteHealth;
            case "autoRegen" -> ToolkitManager.config().autoRegen;
            case "infiniteHunger" -> ToolkitManager.config().infiniteHunger;
            case "flight" -> ToolkitManager.config().flight;
            case "freeCamera" -> ToolkitManager.config().freeCamera;
            case "fullbright" -> ToolkitManager.config().fullbright;
            case "xray" -> ToolkitManager.config().xray;
            case "entityHighlight" -> ToolkitManager.config().entityHighlight;
            case "freezeTime" -> ToolkitManager.config().freezeTime;
            case "alwaysDay" -> ToolkitManager.config().alwaysDay;
            case "keepInventory" -> ToolkitManager.config().keepInventory;
            case "showHud" -> ToolkitManager.config().showHud;
            default -> false;
        };
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + ToolkitManager.onOff(value)), b -> {
            ToolkitManager.setToggle(key, !value);
            rebuild();
        }).dimensions(x, y, w - 8, 20).build());
        return y + 25;
    }

    private int button(int x, int y, int w, String label, ButtonWidget.PressAction action) {
        return button(x, y, w, label, action, true);
    }

    private int button(int x, int y, int w, String label, ButtonWidget.PressAction action, boolean enabled) {
        ButtonWidget.PressAction press = action == null ? btn -> {} : action;
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), press)
                .dimensions(x, y, w - 8, 20).build();
        b.active = enabled;
        addDrawableChild(b);
        return y + 25;
    }

    private int controlRow(int x, int y, int w, String label, String key, double delta) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> rebuild())
                .dimensions(x, y, w - 170, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
            ToolkitManager.adjustMultiplier(key, -delta);
            rebuild();
        }).dimensions(x + w - 164, y, 48, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            ToolkitManager.adjustMultiplier(key, delta);
            rebuild();
        }).dimensions(x + w - 110, y, 48, 20).build());
        return y + 25;
    }

    private int labelBlock(int x, int y, int w, String message) {
        addDrawableChild(ButtonWidget.builder(Text.literal(message), b -> {})
                .dimensions(x, y, w - 8, 20).build()).active = false;
        return y + 25;
    }

    private void addButton(int x, int y, int width, String label, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, width, 20).build());
    }

    private void addPageControls(int x, int y, int w, String type) {
        addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
            if (page > 0) { page--; rebuild(); }
        }).dimensions(x, Math.min(y, height - 55), 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> { page++; rebuild(); })
                .dimensions(x + 86, Math.min(y, height - 55), 80, 20).build());
    }

    private void selectCategory(String name) {
        category = name;
        ToolkitManager.config().lastCategory = name;
        ToolkitManager.save();
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    private void setStatusFromManager() {
        // Manager status is rendered by the HUD; keeping this hook makes the UI action explicit.
    }

    private void confirmClearHostiles() {
        MinecraftClient.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) ToolkitManager.clearNearbyHostiles();
            MinecraftClient.getInstance().setScreen(new ToolkitScreen());
        }, Text.literal("Clear nearby hostile mobs?"), Text.literal("This removes hostile mobs within 32 blocks. Other entities are untouched.")));
    }

    private void confirmDeleteWaypoint(String name) {
        MinecraftClient.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) ToolkitManager.deleteWaypoint(name);
            MinecraftClient.getInstance().setScreen(new ToolkitScreen());
        }, Text.literal("Delete waypoint?"), Text.literal(name)));
    }

    private void confirmRestoreEverything() {
        MinecraftClient.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) ToolkitManager.restoreEverything();
            MinecraftClient.getInstance().setScreen(new ToolkitScreen());
        }, Text.literal("Restore normal settings?"), Text.literal("All toolkit toggles will be turned OFF and saved baselines restored where possible.")));
    }

    private void confirmResetConfig() {
        MinecraftClient.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ToolkitManager.restoreEverything();
                ToolkitManager.config().waypoints.clear();
                ToolkitManager.config().itemSearch = "";
                ToolkitManager.config().entitySearch = "";
                ToolkitManager.config().xrayFilter = "";
                ToolkitManager.config().maxStack = false;
                ToolkitManager.config().itemQuantity = 1;
                ToolkitManager.config().showHud = true;
                ToolkitManager.config().selectedItem = "minecraft:stone";
                ToolkitManager.config().selectedEntity = "minecraft:zombie";
                ToolkitManager.config().lastCategory = "Player";
                ToolkitManager.save();
            }
            MinecraftClient.getInstance().setScreen(new ToolkitScreen());
        }, Text.literal("Reset config?"), Text.literal("This clears toolkit settings and saved waypoints.")));
    }

    @Override
    public void close() {
        ToolkitManager.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, "Singleplayer Toolkit — SINGLEPLAYER ONLY", 10, 6, 0xFF5555);
        String status = ToolkitManager.status();
        if (!status.isBlank()) context.drawTextWithShadow(textRenderer, status, 10, height - 44, 0xFFFF55);
        if (MinecraftClient.getInstance().player != null) {
            String pos = String.format(Locale.ROOT, "Live XYZ: %.1f / %.1f / %.1f",
                    MinecraftClient.getInstance().player.getX(), MinecraftClient.getInstance().player.getY(), MinecraftClient.getInstance().player.getZ());
            context.drawTextWithShadow(textRenderer, pos, width - 245, height - 44, 0xFFFFFF);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static boolean isValidCategory(String value) {
        for (String s : CATEGORIES) if (s.equals(value)) return true;
        return false;
    }
}

package com.example.spktoolkit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ToolkitMod implements ClientModInitializer {
    public static final String MOD_ID = "spktoolkit";
    public static final String MOD_NAME = "Singleplayer Toolkit";

    public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "category"));

    public static KeyBinding menuKey;
    public static KeyBinding flightKey;
    public static KeyBinding fullbrightKey;
    public static KeyBinding freeCameraKey;
    public static KeyBinding invulnerabilityKey;

    @Override
    public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spktoolkit.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KEY_CATEGORY));
        flightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spktoolkit.flight", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, KEY_CATEGORY));
        fullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spktoolkit.fullbright", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, KEY_CATEGORY));
        freeCameraKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spktoolkit.free_camera", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, KEY_CATEGORY));
        invulnerabilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spktoolkit.invulnerability", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F10, KEY_CATEGORY));

        ToolkitManager.init();
        ClientTickEvents.END_CLIENT_TICK.register(ToolkitManager::tick);
        HudRenderCallback.EVENT.register(ToolkitManager::renderHud);
    }

    public static void openMenu() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ToolkitManager.isSingleplayer(client)) {
            client.setScreen(new ToolkitScreen());
        }
    }
}

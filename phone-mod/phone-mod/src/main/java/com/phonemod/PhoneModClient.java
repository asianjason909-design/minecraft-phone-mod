package com.phonemod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class PhoneModClient implements ClientModInitializer {
    public static KeyMapping OPEN_PHONE, ACTION, FLIP;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("phonemod", "main"));
        OPEN_PHONE = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.phonemod.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, category));
        ACTION = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.phonemod.action", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        FLIP = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.phonemod.flip", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

        PhoneNet.init();

        // the docked phone and other players' phones are drawn on the HUD
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("phonemod", "phones"), (graphics, tickCounter) -> {
            PhoneScreen.hud(graphics);
            HandPhones.render(graphics);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_PHONE.consumeClick()) {
                if (mc.player != null) PhoneScreen.get().show();
            }
            while (ACTION.consumeClick()) PhoneScreen.action();
            while (FLIP.consumeClick()) PhoneScreen.flipKey();
            Capture.tick();
            Presence.tick();
        });
    }
}

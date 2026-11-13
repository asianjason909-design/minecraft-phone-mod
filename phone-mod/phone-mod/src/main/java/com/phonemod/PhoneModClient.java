package com.phonemod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class PhoneModClient implements ClientModInitializer {
    public static KeyMapping OPEN_PHONE;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("phonemod", "main"));
        OPEN_PHONE = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.phonemod.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_PHONE.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    mc.setScreenAndShow(new PhoneScreen());
                }
            }
        });
    }
}

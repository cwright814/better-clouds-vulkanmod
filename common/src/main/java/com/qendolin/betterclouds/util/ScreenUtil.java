package com.qendolin.betterclouds.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ScreenUtil {
    public static Screen getCurrentScreen(Minecraft client) {
        if (client == null) return null;
        try {
            return client.gui.screen();
        } catch (NoSuchMethodError e) {
            try {
                return (Screen) Minecraft.class.getField("screen").get(client);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static void setScreen(Minecraft client, Screen screen) {
        if (client == null) return;
        try {
            client.gui.setScreen(screen);
        } catch (NoSuchMethodError e) {
            try {
                Minecraft.class.getMethod("setScreen", Screen.class).invoke(client, screen);
            } catch (Exception ex) {
            }
        }
    }
}

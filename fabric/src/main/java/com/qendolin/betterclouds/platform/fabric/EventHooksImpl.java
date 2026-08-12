package com.qendolin.betterclouds.platform.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.qendolin.betterclouds.BetterCloudsStatic;
import com.qendolin.betterclouds.config.PresetLoader;
import com.qendolin.betterclouds.platform.EventHooks;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class EventHooksImpl extends EventHooks {
    @Override
    public void onClientStarted(Consumer<Minecraft> callback) {
        ClientLifecycleEvents.CLIENT_STARTED.register(callback::accept);
    }

    @Override
    public void onWorldJoin(Consumer<Minecraft> callback) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> callback.accept(client));
    }

    @Override
    public void onClientResourcesReload(Supplier<PreparableReloadListener> supplier) {
        PreparableReloadListener reloader = supplier.get();
        Identifier id = reloader instanceof PresetLoader
                ? ((PresetLoader) reloader).id
                : Identifier.fromNamespaceAndPath(BetterCloudsStatic.MODID, "resource_reloader");

        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(id, reloader);
    }

    @Override
    public void onClientTick(Consumer<Minecraft> callback) {
        ClientTickEvents.END_CLIENT_TICK.register(callback::accept);
    }

    @Override
    public void onClientCommandRegistration(Consumer<CommandDispatcher<?>> callback) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> callback.accept(dispatcher));
    }
}

package com.reign.customblocksounds;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class CustomBlockSoundsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				Identifier.fromNamespaceAndPath("customblocksounds", "blockmap"),
				(ResourceManagerReloadListener) SoundMapManager.INSTANCE::load);

		ClientTickEvents.END_CLIENT_TICK.register(AmbientSoundTicker::tick);
	}
}

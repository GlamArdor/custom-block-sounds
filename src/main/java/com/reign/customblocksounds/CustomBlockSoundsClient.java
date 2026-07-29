package com.reign.customblocksounds;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class CustomBlockSoundsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of("customblocksounds", "blockmap");
			}

			@Override
			public void reload(ResourceManager manager) {
				SoundMapManager.INSTANCE.load(manager);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(AmbientSoundTicker::tick);
	}
}

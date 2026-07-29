package com.reign.customblocksounds;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;

import java.lang.reflect.Method;

/**
 * Shared helper to start a client sound.
 *
 * {@code SoundManager#play(SoundInstance)} changed its return type between 1.21
 * and 1.21.11 (void -> PlayResult) while keeping its name (intermediary
 * {@code method_4873}), so a direct call would only link on one side of that
 * change. A one-time reflective lookup by name works on every version.
 */
public final class ClientSounds {
	private static Method playMethod;
	private static boolean playLookupFailed;

	private ClientSounds() {
	}

	public static void play(SoundManager manager, SoundInstance instance) {
		if (playLookupFailed) {
			return;
		}
		try {
			if (playMethod == null) {
				for (Method method : manager.getClass().getMethods()) {
					String name = method.getName();
					if (method.getParameterCount() == 1
							&& method.getParameterTypes()[0].isInstance(instance)
							&& (name.equals("play") || name.equals("method_4873"))) {
						playMethod = method;
						break;
					}
				}
				if (playMethod == null) {
					playLookupFailed = true;
					SoundMapManager.LOGGER.warn("[CustomBlockSounds] Could not find SoundManager#play, custom sound playback disabled");
					return;
				}
			}
			playMethod.invoke(manager, instance);
		} catch (ReflectiveOperationException e) {
			playLookupFailed = true;
			SoundMapManager.LOGGER.warn("[CustomBlockSounds] Failed to play sound, custom sound playback disabled", e);
		}
	}
}

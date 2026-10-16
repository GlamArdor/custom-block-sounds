package com.reign.customblocksounds;

import com.reign.customblocksounds.SoundMapManager.SoundKind;
import com.reign.customblocksounds.SoundMapManager.SoundOverride;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/**
 * Rewrites block sounds that arrive from the server (other players' footsteps,
 * block placement, fall sounds). The packet only carries a sound id and a
 * position, but the client has the world: if the id matches the vanilla
 * step/place/fall sound of a block at that position, we know both the block
 * and the sound type - and can substitute the pack's sound.
 */
public final class ServerSoundReplacer {
	private ServerSoundReplacer() {
	}

	/** Returns true when the packet was handled and vanilla processing should stop. */
	public static boolean tryReplace(Minecraft client, ClientboundSoundPacket packet) {
		SoundMapManager manager = SoundMapManager.INSTANCE;
		ClientLevel world = client.level;
		if (world == null || !manager.hasRules()) {
			return false;
		}

		Identifier soundId = packet.getSound().value().location();
		double x = packet.getX();
		double y = packet.getY();
		double z = packet.getZ();

		// Steps and falls are emitted at the entity's feet (block below),
		// placement at the center of the placed block itself.
		BlockPos feet = BlockPos.containing(x, y - 0.2, z);
		BlockPos[] candidates = {feet, BlockPos.containing(x, y, z), feet.below()};

		BlockPos previous = null;
		for (BlockPos pos : candidates) {
			if (pos.equals(previous)) {
				continue;
			}
			previous = pos;
			BlockState state = world.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}

			SoundType vanilla = vanillaGroup(state);
			SoundKind kind;
			if (idEquals(vanilla.getStepSound(), soundId)) {
				kind = SoundKind.STEP;
			} else if (idEquals(vanilla.getPlaceSound(), soundId)) {
				kind = SoundKind.PLACE;
			} else if (idEquals(vanilla.getFallSound(), soundId)) {
				kind = SoundKind.FALL;
			} else {
				continue;
			}

			SoundOverride override = manager.getOverride(state, kind);
			if (override == null) {
				return false; // identified, but this state has no rule for the kind
			}

			SoundEvent replacement = override.sound() != null ? override.sound() : packet.getSound().value();
			// The packet's volume/pitch already include the vanilla per-action scaling
			// (computed server-side from the vanilla group), so apply our values as a ratio.
			float volume = packet.getVolume();
			float pitch = packet.getPitch();
			if (override.volume() != null && vanilla.getVolume() > 0.0f) {
				volume *= override.volume() / vanilla.getVolume();
			}
			if (override.pitch() != null && vanilla.getPitch() > 0.0f) {
				pitch *= override.pitch() / vanilla.getPitch();
			}

			client.getSoundManager().play(new SimpleSoundInstance(
					replacement, packet.getSource(), volume, pitch,
					RandomSource.create(packet.getSeed()), x, y, z));
			return true;
		}
		return false;
	}

	private static boolean idEquals(SoundEvent event, Identifier id) {
		return event != null && event.location().equals(id);
	}

	private static SoundType vanillaGroup(BlockState state) {
		SoundMapManager.BYPASS.set(Boolean.TRUE);
		try {
			return state.getSoundType();
		} finally {
			SoundMapManager.BYPASS.set(Boolean.FALSE);
		}
	}
}

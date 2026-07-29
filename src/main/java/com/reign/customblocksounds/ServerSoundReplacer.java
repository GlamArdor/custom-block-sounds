package com.reign.customblocksounds;

import com.reign.customblocksounds.SoundMapManager.SoundKind;
import com.reign.customblocksounds.SoundMapManager.SoundOverride;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

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
	public static boolean tryReplace(MinecraftClient client, PlaySoundS2CPacket packet) {
		SoundMapManager manager = SoundMapManager.INSTANCE;
		ClientWorld world = client.world;
		if (world == null || !manager.hasRules()) {
			return false;
		}

		Identifier soundId = packet.getSound().value().id();
		double x = packet.getX();
		double y = packet.getY();
		double z = packet.getZ();

		// Steps and falls are emitted at the entity's feet (block below),
		// placement at the center of the placed block itself.
		BlockPos feet = BlockPos.ofFloored(x, y - 0.2, z);
		BlockPos[] candidates = {feet, BlockPos.ofFloored(x, y, z), feet.down()};

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

			BlockSoundGroup vanilla = vanillaGroup(state);
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

			ClientSounds.play(client.getSoundManager(), new PositionedSoundInstance(
					replacement, packet.getCategory(), volume, pitch,
					Random.create(packet.getSeed()), x, y, z));
			return true;
		}
		return false;
	}

	private static boolean idEquals(SoundEvent event, Identifier id) {
		return event != null && event.id().equals(id);
	}

	private static BlockSoundGroup vanillaGroup(BlockState state) {
		SoundMapManager.BYPASS.set(Boolean.TRUE);
		try {
			return state.getSoundGroup();
		} finally {
			SoundMapManager.BYPASS.set(Boolean.FALSE);
		}
	}
}

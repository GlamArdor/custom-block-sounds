package com.reign.customblocksounds;

import com.reign.customblocksounds.SoundMapManager.AmbientConfig;
import com.reign.customblocksounds.SoundMapManager.IdleSettings;
import com.reign.customblocksounds.SoundMapManager.SoundOverride;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Plays "idle" (ambient) sounds: every client tick a few random positions around
 * the player are sampled, and blocks with an idle sound occasionally emit it.
 * Sampling density and per-rule behaviour are configured in blockmap.json
 * (see the idle_* parameters and the "customblocksounds:ambient" entry).
 */
public final class AmbientSoundTicker {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final float DEFAULT_CHANCE = 0.002f;
	private static final int CLUSTER_RADIUS_XZ = 8;
	private static final int CLUSTER_RADIUS_Y = 2;
	/** Ticks where day turns to night, roughly when the sun touches the horizon. */
	private static final long DAY_LENGTH = 12000L;

	private static long windowStartNanos;
	private static int playedInWindow;

	private AmbientSoundTicker() {
	}

	public static void tick(Minecraft client) {
		if (client.level == null || client.player == null || client.isPaused()) {
			return;
		}
		SoundMapManager manager = SoundMapManager.INSTANCE;
		if (!manager.hasIdleSounds()) {
			return;
		}

		Level world = client.level;
		AmbientConfig config = manager.getAmbientConfig();

		long now = System.nanoTime();
		if (now - windowStartNanos >= 1_000_000_000L) {
			windowStartNanos = now;
			playedInWindow = 0;
		}

		BlockPos center = client.player.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 0; i < config.samples(); i++) {
			if (playedInWindow >= config.maxPerSecond()) {
				return;
			}
			cursor.set(
					center.getX() + RANDOM.nextIntBetweenInclusive(-config.radiusXz(), config.radiusXz()),
					center.getY() + RANDOM.nextIntBetweenInclusive(-config.radiusY(), config.radiusY()),
					center.getZ() + RANDOM.nextIntBetweenInclusive(-config.radiusXz(), config.radiusXz()));
			BlockState state = world.getBlockState(cursor);
			if (state.isAir()) {
				continue;
			}
			SoundOverride idle = manager.getIdleOverride(state);
			if (idle == null || idle.sound() == null) {
				continue;
			}
			IdleSettings settings = idle.idle();
			float chance = settings != null && settings.chance != null ? settings.chance : DEFAULT_CHANCE;
			if (RANDOM.nextFloat() >= chance) {
				continue;
			}
			BlockPos pos = cursor.immutable();
			if (settings != null && !conditionsPass(world, pos, state, settings, now)) {
				continue;
			}

			float volume = idle.volume() != null ? idle.volume() : 1.0f;
			float pitch = pickPitch(idle, settings);
			SoundSource category = settings != null && settings.category != null ? settings.category : SoundSource.BLOCKS;
			client.getSoundManager().play(new SimpleSoundInstance(
					idle.sound(), category, volume, pitch, RANDOM,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
			if (settings != null) {
				settings.lastPlayedNanos = now;
			}
			playedInWindow++;
		}
	}

	private static boolean conditionsPass(Level world, BlockPos pos, BlockState state, IdleSettings settings, long now) {
		if (settings.cooldownSeconds != null && settings.lastPlayedNanos != 0
				&& now - settings.lastPlayedNanos < (long) (settings.cooldownSeconds * 1.0e9)) {
			return false;
		}
		if (settings.airAbove && !world.getBlockState(pos.above()).isAir()) {
			return false;
		}
		if (settings.sky && !world.canSeeSky(pos.above())) {
			return false;
		}
		if (settings.dayOnly != null) {
			boolean day = world.getOverworldClockTime() % 24000L < DAY_LENGTH;
			if (day != settings.dayOnly) {
				return false;
			}
		}
		if (settings.rainOnly != null && world.isRaining() != settings.rainOnly) {
			return false;
		}
		if (settings.cluster != null && !clusterSatisfied(world, pos, state.getBlock(), settings.cluster)) {
			return false;
		}
		return true;
	}

	/** Requires at least {@code required} more blocks of the same type in a flat box around the position. */
	private static boolean clusterSatisfied(Level world, BlockPos pos, Block block, int required) {
		int found = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dy = -CLUSTER_RADIUS_Y; dy <= CLUSTER_RADIUS_Y; dy++) {
			for (int dx = -CLUSTER_RADIUS_XZ; dx <= CLUSTER_RADIUS_XZ; dx++) {
				for (int dz = -CLUSTER_RADIUS_XZ; dz <= CLUSTER_RADIUS_XZ; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (world.getBlockState(cursor).getBlock() == block && ++found >= required) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static float pickPitch(SoundOverride idle, IdleSettings settings) {
		if (idle.pitch() != null) {
			return idle.pitch();
		}
		if (settings != null && (settings.pitchMin != null || settings.pitchMax != null)) {
			float min = settings.pitchMin != null ? settings.pitchMin : settings.pitchMax;
			float max = settings.pitchMax != null ? settings.pitchMax : settings.pitchMin;
			return min + RANDOM.nextFloat() * (max - min);
		}
		return 0.9f + RANDOM.nextFloat() * 0.2f;
	}
}

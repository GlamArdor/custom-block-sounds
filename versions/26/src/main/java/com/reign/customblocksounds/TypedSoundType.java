package com.reign.customblocksounds;

import com.reign.customblocksounds.SoundMapManager.SoundKind;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

import java.util.EnumMap;

/**
 * A sound group with optional per-type volume/pitch. Vanilla call sites always
 * fetch the sound first ({@code getBreakSound()}, ...) and then query
 * {@code getVolume()}/{@code getPitch()} in the same expression, so remembering
 * the last requested sound type (per thread) lets us answer with that type's values.
 */
public final class TypedSoundType extends SoundType {
	private static final ThreadLocal<SoundKind> LAST_KIND = new ThreadLocal<>();

	private final EnumMap<SoundKind, Float> volumes;
	private final EnumMap<SoundKind, Float> pitches;

	public TypedSoundType(float volume, float pitch,
			SoundEvent breakSound, SoundEvent stepSound, SoundEvent placeSound,
			SoundEvent hitSound, SoundEvent fallSound,
			EnumMap<SoundKind, Float> volumes, EnumMap<SoundKind, Float> pitches) {
		super(volume, pitch, breakSound, stepSound, placeSound, hitSound, fallSound);
		this.volumes = volumes;
		this.pitches = pitches;
	}

	@Override
	public SoundEvent getBreakSound() {
		LAST_KIND.set(SoundKind.BREAK);
		return super.getBreakSound();
	}

	@Override
	public SoundEvent getStepSound() {
		LAST_KIND.set(SoundKind.STEP);
		return super.getStepSound();
	}

	@Override
	public SoundEvent getPlaceSound() {
		LAST_KIND.set(SoundKind.PLACE);
		return super.getPlaceSound();
	}

	@Override
	public SoundEvent getHitSound() {
		LAST_KIND.set(SoundKind.HIT);
		return super.getHitSound();
	}

	@Override
	public SoundEvent getFallSound() {
		LAST_KIND.set(SoundKind.FALL);
		return super.getFallSound();
	}

	@Override
	public float getVolume() {
		SoundKind kind = LAST_KIND.get();
		Float volume = kind == null ? null : volumes.get(kind);
		return volume != null ? volume : super.getVolume();
	}

	@Override
	public float getPitch() {
		SoundKind kind = LAST_KIND.get();
		Float pitch = kind == null ? null : pitches.get(kind);
		return pitch != null ? pitch : super.getPitch();
	}
}

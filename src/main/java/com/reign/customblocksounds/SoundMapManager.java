package com.reign.customblocksounds;

import com.google.gson.stream.JsonReader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Loads {@code assets/customblocksounds/config/blockmap.json} from every enabled
 * resource pack and resolves per-blockstate sound overrides.
 */
public final class SoundMapManager {
	public static final SoundMapManager INSTANCE = new SoundMapManager();
	public static final Logger LOGGER = LoggerFactory.getLogger("customblocksounds");
	/** Re-entrancy guard: when set, the mixins step aside so vanilla logic runs. */
	public static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

	public static final Identifier BLOCKMAP_PATH = Identifier.of("customblocksounds", "config/blockmap.json");
	/** Reserved blockmap key holding the global idle scanner settings. */
	public static final String AMBIENT_CONFIG_KEY = "customblocksounds:ambient";

	private static final float MIN_VOLUME_PITCH = 0.0f;
	private static final float MAX_VOLUME_PITCH = 16.0f;

	public enum SoundKind {
		BREAK, FALL, HIT, IDLE, PLACE, STEP
	}

	/** Optional per-rule idle behaviour parsed from the {@code idle_*} parameters. */
	public static final class IdleSettings {
		public final Float chance;          // idle_chance      0..1
		public final Float cooldownSeconds; // idle_cooldown    seconds between plays of this rule
		public final Float pitchMin;        // idle_pitch_min
		public final Float pitchMax;        // idle_pitch_max
		public final SoundCategory category;// idle_category    volume slider used
		public final boolean airAbove;      // idle_air_above   requires air above the block
		public final boolean sky;           // idle_sky         requires open sky above
		public final Integer cluster;       // idle_cluster     requires N same blocks nearby
		public final Boolean dayOnly;       // idle_time        true=day, false=night, null=any
		public final Boolean rainOnly;      // idle_weather     true=rain, false=clear, null=any

		/** Runtime state for idle_cooldown; reset on resource reload. */
		public volatile long lastPlayedNanos;

		IdleSettings(Float chance, Float cooldownSeconds, Float pitchMin, Float pitchMax,
				SoundCategory category, boolean airAbove, boolean sky, Integer cluster,
				Boolean dayOnly, Boolean rainOnly) {
			this.chance = chance;
			this.cooldownSeconds = cooldownSeconds;
			this.pitchMin = pitchMin;
			this.pitchMax = pitchMax;
			this.category = category;
			this.airAbove = airAbove;
			this.sky = sky;
			this.cluster = cluster;
			this.dayOnly = dayOnly;
			this.rainOnly = rainOnly;
		}
	}

	/** Global idle scanner settings ({@link #AMBIENT_CONFIG_KEY} entry, top pack wins). */
	public record AmbientConfig(int samples, int radiusXz, int radiusY, int maxPerSecond) {
		public static final AmbientConfig DEFAULT = new AmbientConfig(6, 16, 8, Integer.MAX_VALUE);
	}

	/**
	 * One config line: an optional replacement sound plus optional volume/pitch,
	 * and (for idle) the idle_* behaviour settings.
	 * {@code null} sound means "keep the vanilla sound, only adjust volume/pitch".
	 */
	public record SoundOverride(SoundEvent sound, Float volume, Float pitch, IdleSettings idle) {
	}

	/** One config entry: a property filter plus the sound overrides it assigns. */
	private record Rule(Map<Property<?>, Comparable<?>> props, EnumMap<SoundKind, SoundOverride> sounds) {
	}

	private static final EnumMap<SoundKind, SoundOverride> EMPTY = new EnumMap<>(SoundKind.class);
	private static final SoundKind[] GROUP_KINDS = {
			SoundKind.BREAK, SoundKind.STEP, SoundKind.PLACE, SoundKind.HIT, SoundKind.FALL
	};

	private volatile Map<Block, List<Rule>> rules = Map.of();
	private volatile ConcurrentHashMap<BlockState, EnumMap<SoundKind, SoundOverride>> resolvedCache = new ConcurrentHashMap<>();
	private volatile ConcurrentHashMap<BlockState, BlockSoundGroup> groupCache = new ConcurrentHashMap<>();
	private volatile boolean hasRules = false;
	private volatile boolean hasIdleSounds = false;
	private volatile AmbientConfig ambientConfig = AmbientConfig.DEFAULT;

	/** Scratch value while a reload is being parsed (render thread only). */
	private AmbientConfig pendingAmbient = AmbientConfig.DEFAULT;

	private SoundMapManager() {
	}

	// ------------------------------------------------------------------ loading

	public void load(ResourceManager manager) {
		Map<Block, List<Rule>> newRules = new HashMap<>();
		pendingAmbient = AmbientConfig.DEFAULT;
		int entries = 0;
		int files = 0;
		// getAllResources returns packs bottom-to-top, so later files override earlier ones.
		for (Resource resource : manager.getAllResources(BLOCKMAP_PATH)) {
			String source = resource.getPackId();
			try (BufferedReader reader = resource.getReader()) {
				entries += parse(reader, source, newRules);
				files++;
			} catch (Exception e) {
				LOGGER.error("[CustomBlockSounds] Failed to read blockmap.json from pack '{}'", source, e);
			}
		}

		boolean idle = false;
		for (List<Rule> list : newRules.values()) {
			for (Rule rule : list) {
				SoundOverride override = rule.sounds().get(SoundKind.IDLE);
				if (override != null && override.sound() != null) {
					idle = true;
				}
			}
		}

		this.rules = newRules;
		this.resolvedCache = new ConcurrentHashMap<>();
		this.groupCache = new ConcurrentHashMap<>();
		this.hasRules = !newRules.isEmpty();
		this.hasIdleSounds = idle;
		this.ambientConfig = pendingAmbient;

		if (files > 0) {
			LOGGER.info("[CustomBlockSounds] Loaded {} sound rule(s) from {} blockmap.json file(s)", entries, files);
		}
	}

	/**
	 * Streaming parse via JsonReader: the format intentionally allows duplicate keys
	 * (one line per sound type), which Gson's tree/map parsing would collapse.
	 */
	private int parse(BufferedReader reader, String source, Map<Block, List<Rule>> out) throws IOException {
		int count = 0;
		try (JsonReader json = new JsonReader(reader)) {
			json.setLenient(true);
			json.beginObject();
			while (json.hasNext()) {
				String key = json.nextName();
				String value = json.nextString();
				if (AMBIENT_CONFIG_KEY.equalsIgnoreCase(key.trim())) {
					parseAmbientConfig(value, source);
					continue;
				}
				if (addEntry(key, value, source, out)) {
					count++;
				}
			}
			json.endObject();
		}
		return count;
	}

	private void parseAmbientConfig(String value, String source) {
		int samples = pendingAmbient.samples();
		int radiusXz = pendingAmbient.radiusXz();
		int maxPerSecond = pendingAmbient.maxPerSecond();
		for (String token : value.split(",")) {
			String[] kv = token.split("=", 2);
			if (kv.length != 2) {
				continue;
			}
			String param = kv[0].trim().toLowerCase(Locale.ROOT);
			String raw = kv[1].trim();
			switch (param) {
				case "idle_samples" -> {
					Integer parsed = parseIntIn(raw, 1, 700);
					if (parsed == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_samples '{}' (expected 1..700)", source, raw);
					} else {
						samples = parsed;
					}
				}
				case "idle_radius" -> {
					Integer parsed = parseIntIn(raw, 1, 32);
					if (parsed == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_radius '{}' (expected 1..32)", source, raw);
					} else {
						radiusXz = parsed;
					}
				}
				case "idle_max_per_second" -> {
					Integer parsed = parseIntIn(raw, 1, 1000);
					if (parsed == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_max_per_second '{}' (expected 1..1000)", source, raw);
					} else {
						maxPerSecond = parsed;
					}
				}
				default -> LOGGER.warn("[CustomBlockSounds] ({}) Unknown ambient parameter '{}'", source, kv[0]);
			}
		}
		int radiusY = Math.max(4, radiusXz / 2);
		pendingAmbient = new AmbientConfig(samples, radiusXz, radiusY, maxPerSecond);
		LOGGER.info("[CustomBlockSounds] ({}) Ambient scanner: samples={}, radius={}, maxPerSecond={}",
				source, samples, radiusXz, maxPerSecond == Integer.MAX_VALUE ? "unlimited" : String.valueOf(maxPerSecond));
	}

	private boolean addEntry(String key, String value, String source, Map<Block, List<Rule>> out) {
		// --- key: "namespace:block_id[prop=val,prop=val]" -------------------
		String blockPart = key.trim();
		String propsPart = null;
		int bracket = blockPart.indexOf('[');
		if (bracket >= 0) {
			if (!blockPart.endsWith("]")) {
				LOGGER.warn("[CustomBlockSounds] ({}) Malformed blockstate key '{}': missing ']'", source, key);
				return false;
			}
			propsPart = blockPart.substring(bracket + 1, blockPart.length() - 1);
			blockPart = blockPart.substring(0, bracket);
		}

		Identifier blockId;
		try {
			blockId = Identifier.of(blockPart.toLowerCase(Locale.ROOT));
		} catch (Exception e) {
			LOGGER.warn("[CustomBlockSounds] ({}) Invalid block id '{}'", source, blockPart);
			return false;
		}
		// getOptionalValue (intermediary method_17966) is stable across 1.21..1.21.11,
		// unlike get(Identifier), which was replaced in 1.21.2.
		Block block = Registries.BLOCK.getOptionalValue(blockId).orElse(null);
		if (block == null) {
			LOGGER.warn("[CustomBlockSounds] ({}) Unknown block '{}', entry skipped", source, blockId);
			return false;
		}

		Map<Property<?>, Comparable<?>> props = new HashMap<>();
		if (propsPart != null && !propsPart.isBlank()) {
			for (String pair : propsPart.split(",")) {
				String[] kv = pair.split("=", 2);
				if (kv.length != 2) {
					LOGGER.warn("[CustomBlockSounds] ({}) Malformed property '{}' in '{}'", source, pair, key);
					return false;
				}
				String propName = kv[0].trim();
				Property<?> property = block.getStateManager().getProperty(propName);
				if (property == null) {
					LOGGER.warn("[CustomBlockSounds] ({}) Block '{}' has no property '{}', entry skipped", source, blockId, propName);
					return false;
				}
				if (!putProperty(props, property, kv[1].trim())) {
					LOGGER.warn("[CustomBlockSounds] ({}) Invalid value '{}' for property '{}' of '{}', entry skipped",
							source, kv[1].trim(), propName, blockId);
					return false;
				}
			}
		}

		// --- value: "sound=ns:id,type=break,volume=0.8,pitch=1.2,idle_*=..." ---
		String soundRaw = null;
		String typeRaw = null;
		Float volume = null;
		Float pitch = null;
		Float idleChance = null;
		Float idleCooldown = null;
		Float idlePitchMin = null;
		Float idlePitchMax = null;
		SoundCategory idleCategory = null;
		boolean idleAirAbove = false;
		boolean idleSky = false;
		Integer idleCluster = null;
		Boolean idleDayOnly = null;
		Boolean idleRainOnly = null;
		boolean anyIdleParam = false;

		for (String token : value.split(",")) {
			String[] kv = token.split("=", 2);
			if (kv.length != 2) {
				continue;
			}
			String param = kv[0].trim().toLowerCase(Locale.ROOT);
			String paramValue = kv[1].trim();
			switch (param) {
				case "sound" -> soundRaw = paramValue;
				case "type" -> typeRaw = paramValue;
				case "volume", "pitch" -> {
					Float parsed = parseFloatIn(paramValue, MIN_VOLUME_PITCH, MAX_VOLUME_PITCH);
					if (parsed == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid {} '{}' in '{}' (expected a number {}..{}), entry skipped",
								source, param, paramValue, value, MIN_VOLUME_PITCH, MAX_VOLUME_PITCH);
						return false;
					}
					if (param.equals("volume")) {
						volume = parsed;
					} else {
						pitch = parsed;
					}
				}
				case "idle_chance" -> {
					anyIdleParam = true;
					idleChance = parseFloatIn(paramValue, 0.0f, 1.0f);
					if (idleChance == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_chance '{}' (expected 0..1), entry skipped", source, paramValue);
						return false;
					}
				}
				case "idle_cooldown" -> {
					anyIdleParam = true;
					idleCooldown = parseFloatIn(paramValue, 0.0f, 3600.0f);
					if (idleCooldown == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_cooldown '{}' (expected seconds 0..3600), entry skipped", source, paramValue);
						return false;
					}
				}
				case "idle_pitch_min", "idle_pitch_max" -> {
					anyIdleParam = true;
					Float parsed = parseFloatIn(paramValue, MIN_VOLUME_PITCH, MAX_VOLUME_PITCH);
					if (parsed == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid {} '{}' (expected {}..{}), entry skipped",
								source, param, paramValue, MIN_VOLUME_PITCH, MAX_VOLUME_PITCH);
						return false;
					}
					if (param.equals("idle_pitch_min")) {
						idlePitchMin = parsed;
					} else {
						idlePitchMax = parsed;
					}
				}
				case "idle_category" -> {
					anyIdleParam = true;
					idleCategory = parseCategory(paramValue);
					if (idleCategory == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Unknown idle_category '{}' (e.g. blocks/ambient/weather/players), entry skipped", source, paramValue);
						return false;
					}
				}
				case "idle_air_above", "idle_sky" -> {
					anyIdleParam = true;
					Boolean parsed = parseBool(paramValue);
					if (parsed == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid {} '{}' (expected true/false), entry skipped", source, param, paramValue);
						return false;
					}
					if (param.equals("idle_air_above")) {
						idleAirAbove = parsed;
					} else {
						idleSky = parsed;
					}
				}
				case "idle_cluster" -> {
					anyIdleParam = true;
					idleCluster = parseIntIn(paramValue, 1, 64);
					if (idleCluster == null) {
						LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_cluster '{}' (expected 1..64), entry skipped", source, paramValue);
						return false;
					}
				}
				case "idle_time" -> {
					anyIdleParam = true;
					switch (paramValue.toLowerCase(Locale.ROOT)) {
						case "day" -> idleDayOnly = Boolean.TRUE;
						case "night" -> idleDayOnly = Boolean.FALSE;
						default -> {
							LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_time '{}' (expected day/night), entry skipped", source, paramValue);
							return false;
						}
					}
				}
				case "idle_weather" -> {
					anyIdleParam = true;
					switch (paramValue.toLowerCase(Locale.ROOT)) {
						case "rain" -> idleRainOnly = Boolean.TRUE;
						case "clear" -> idleRainOnly = Boolean.FALSE;
						default -> {
							LOGGER.warn("[CustomBlockSounds] ({}) Invalid idle_weather '{}' (expected rain/clear), entry skipped", source, paramValue);
							return false;
						}
					}
				}
				default -> LOGGER.debug("[CustomBlockSounds] ({}) Ignoring unknown parameter '{}' in '{}'", source, kv[0], value);
			}
		}
		if (typeRaw == null) {
			LOGGER.warn("[CustomBlockSounds] ({}) Entry for '{}' needs a 'type=' parameter: '{}'", source, key, value);
			return false;
		}
		if (soundRaw == null && volume == null && pitch == null) {
			LOGGER.warn("[CustomBlockSounds] ({}) Entry for '{}' needs at least one of 'sound=', 'volume=', 'pitch=': '{}'", source, key, value);
			return false;
		}

		SoundKind kind = parseKind(typeRaw);
		if (kind == null) {
			LOGGER.warn("[CustomBlockSounds] ({}) Unknown sound type '{}' (expected break/fall/hit/idle/place/step)", source, typeRaw);
			return false;
		}
		if (anyIdleParam && kind != SoundKind.IDLE) {
			LOGGER.warn("[CustomBlockSounds] ({}) idle_* parameters are only valid with type=idle, ignoring them in '{}'", source, value);
			anyIdleParam = false;
		}
		if (idlePitchMin != null && idlePitchMax != null && idlePitchMin > idlePitchMax) {
			LOGGER.warn("[CustomBlockSounds] ({}) idle_pitch_min > idle_pitch_max in '{}', entry skipped", source, value);
			return false;
		}

		SoundEvent sound = null;
		if (soundRaw != null) {
			sound = parseSound(soundRaw, source);
			if (sound == null) {
				return false;
			}
		} else if (kind == SoundKind.IDLE) {
			LOGGER.warn("[CustomBlockSounds] ({}) type=idle requires a 'sound=' parameter (vanilla has no idle sound to adjust): '{}'", source, value);
			return false;
		}

		IdleSettings idleSettings = null;
		if (kind == SoundKind.IDLE && anyIdleParam) {
			idleSettings = new IdleSettings(idleChance, idleCooldown, idlePitchMin, idlePitchMax,
					idleCategory, idleAirAbove, idleSky, idleCluster, idleDayOnly, idleRainOnly);
		}

		// Merge into an existing rule with the same property filter so that
		// several lines for one blockstate form a single rule; later lines win.
		SoundOverride override = new SoundOverride(sound, volume, pitch, idleSettings);
		List<Rule> list = out.computeIfAbsent(block, b -> new ArrayList<>());
		for (Rule rule : list) {
			if (rule.props().equals(props)) {
				rule.sounds().put(kind, override);
				return true;
			}
		}
		EnumMap<SoundKind, SoundOverride> sounds = new EnumMap<>(SoundKind.class);
		sounds.put(kind, override);
		list.add(new Rule(props, sounds));
		return true;
	}

	private static <T extends Comparable<T>> boolean putProperty(Map<Property<?>, Comparable<?>> map, Property<T> property, String value) {
		Optional<T> parsed = property.parse(value);
		if (parsed.isEmpty()) {
			return false;
		}
		map.put(property, parsed.get());
		return true;
	}

	private static SoundKind parseKind(String raw) {
		return switch (raw.toLowerCase(Locale.ROOT)) {
			case "break" -> SoundKind.BREAK;
			case "fall" -> SoundKind.FALL;
			case "hit" -> SoundKind.HIT;
			case "idle", "ambient" -> SoundKind.IDLE;
			case "place" -> SoundKind.PLACE;
			case "step", "walk" -> SoundKind.STEP;
			default -> null;
		};
	}

	private static SoundCategory parseCategory(String raw) {
		for (SoundCategory category : SoundCategory.values()) {
			if (category.getName().equalsIgnoreCase(raw)) {
				return category;
			}
		}
		return null;
	}

	private static Boolean parseBool(String raw) {
		return switch (raw.toLowerCase(Locale.ROOT)) {
			case "true", "1", "yes" -> Boolean.TRUE;
			case "false", "0", "no" -> Boolean.FALSE;
			default -> null;
		};
	}

	private static Float parseFloatIn(String raw, float min, float max) {
		try {
			float parsed = Float.parseFloat(raw);
			if (Float.isNaN(parsed) || parsed < min || parsed > max) {
				return null;
			}
			return parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer parseIntIn(String raw, int min, int max) {
		try {
			int parsed = Integer.parseInt(raw);
			if (parsed < min || parsed > max) {
				return null;
			}
			return parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private SoundEvent parseSound(String raw, String source) {
		String id = raw.toLowerCase(Locale.ROOT);
		// Be lenient about "minecraft:reign:glass_walk" style mistakes: keep the last two segments.
		String[] parts = id.split(":");
		if (parts.length > 2) {
			id = parts[parts.length - 2] + ":" + parts[parts.length - 1];
		}
		try {
			return SoundEvent.of(Identifier.of(id));
		} catch (Exception e) {
			LOGGER.warn("[CustomBlockSounds] ({}) Invalid sound id '{}'", source, raw);
			return null;
		}
	}

	// ------------------------------------------------------------------ queries

	/**
	 * Returns the overridden sound group for the state, or {@code null} to keep vanilla.
	 * {@code vanilla} supplies the original group (evaluated once per state, then cached).
	 */
	public BlockSoundGroup getGroupOverride(BlockState state, Supplier<BlockSoundGroup> vanilla) {
		if (!hasRules) {
			return null;
		}
		EnumMap<SoundKind, SoundOverride> sounds = resolve(state);
		if (sounds.isEmpty()) {
			return null;
		}
		boolean groupRelevant = false;
		for (SoundKind kind : GROUP_KINDS) {
			if (sounds.containsKey(kind)) {
				groupRelevant = true;
				break;
			}
		}
		if (!groupRelevant) {
			return null; // idle-only rule: nothing to override in the sound group
		}
		return groupCache.computeIfAbsent(state, s -> {
			BlockSoundGroup base = vanilla.get();
			EnumMap<SoundKind, Float> volumes = new EnumMap<>(SoundKind.class);
			EnumMap<SoundKind, Float> pitches = new EnumMap<>(SoundKind.class);
			for (SoundKind kind : GROUP_KINDS) {
				SoundOverride override = sounds.get(kind);
				if (override != null) {
					if (override.volume() != null) {
						volumes.put(kind, override.volume());
					}
					if (override.pitch() != null) {
						pitches.put(kind, override.pitch());
					}
				}
			}
			return new TypedBlockSoundGroup(
					base.getVolume(), base.getPitch(),
					soundOrDefault(sounds, SoundKind.BREAK, base.getBreakSound()),
					soundOrDefault(sounds, SoundKind.STEP, base.getStepSound()),
					soundOrDefault(sounds, SoundKind.PLACE, base.getPlaceSound()),
					soundOrDefault(sounds, SoundKind.HIT, base.getHitSound()),
					soundOrDefault(sounds, SoundKind.FALL, base.getFallSound()),
					volumes, pitches);
		});
	}

	private static SoundEvent soundOrDefault(EnumMap<SoundKind, SoundOverride> sounds, SoundKind kind, SoundEvent fallback) {
		SoundOverride override = sounds.get(kind);
		return override != null && override.sound() != null ? override.sound() : fallback;
	}

	public SoundOverride getIdleOverride(BlockState state) {
		if (!hasIdleSounds) {
			return null;
		}
		return resolve(state).get(SoundKind.IDLE);
	}

	public boolean hasIdleSounds() {
		return hasIdleSounds;
	}

	public boolean hasRules() {
		return hasRules;
	}

	/** Direct override lookup for one sound kind, or {@code null} when the state has no rule for it. */
	public SoundOverride getOverride(BlockState state, SoundKind kind) {
		if (!hasRules) {
			return null;
		}
		return resolve(state).get(kind);
	}

	public AmbientConfig getAmbientConfig() {
		return ambientConfig;
	}

	private EnumMap<SoundKind, SoundOverride> resolve(BlockState state) {
		return resolvedCache.computeIfAbsent(state, s -> {
			List<Rule> list = rules.get(s.getBlock());
			if (list == null) {
				return EMPTY;
			}
			List<Rule> matching = new ArrayList<>();
			for (Rule rule : list) {
				if (matches(s, rule)) {
					matching.add(rule);
				}
			}
			if (matching.isEmpty()) {
				return EMPTY;
			}
			// Less specific rules first (stable sort), so rules with more properties win.
			matching.sort(Comparator.comparingInt(r -> r.props().size()));
			EnumMap<SoundKind, SoundOverride> resolved = new EnumMap<>(SoundKind.class);
			for (Rule rule : matching) {
				resolved.putAll(rule.sounds());
			}
			return resolved;
		});
	}

	private static boolean matches(BlockState state, Rule rule) {
		for (Map.Entry<Property<?>, Comparable<?>> entry : rule.props().entrySet()) {
			if (!state.get(entry.getKey()).equals(entry.getValue())) {
				return false;
			}
		}
		return true;
	}
}

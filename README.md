<!--suppress HtmlDeprecatedTag, XmlDeprecatedElement -->
<center>
<b>Custom Block Sounds</b><br/>
Resource packs decide what a block sounds like – broken, stepped on, placed, struck, landed on, or just sitting there.<br/>
<b>Client-side. Fabric, Minecraft 1.21 – 1.21.11, one jar.</b><br/>
</center>

---

Vanilla ties a sound to a material. Every stone block is stone, every wood block is wood, and a resource pack that turns brown mushroom blocks into stained glass still leaves them thudding like fungus. Replacing the vanilla `.ogg` files isn't a way out either, because that changes the sound everywhere at once.

This reads one file out of your resource pack and matches sounds to **blockstates**, so one mushroom block variant can ring like glass while the next one over stays exactly as it was.

**Client-side.** The server needs nothing, which is the point: this was written for servers running plugin cores you can't add mods to.

## The file

Every enabled resource pack is searched for:

```
assets/customblocksounds/config/blockmap.json
```

Packs are merged, and a pack sitting **higher** in the list overrules the ones below it.

```json
{
  "minecraft:brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=false]": "sound=reign:glass_walk,type=step,volume=0.8",
  "minecraft:brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=false]": "sound=reign:glass_break,type=break",
  "minecraft:sand": "type=step,volume=1.5,pitch=1.2"
}
```

**The key** is a block id with optional blockstate properties in `[...]`. Name only the properties you care about and the rule covers every state that matches them; the rule naming more properties wins. Duplicate keys are allowed and expected – one line per sound type.

**The value** is a comma-separated list:

| Field | |
|---|---|
| `sound=<namespace>:<id>` | a sound event – vanilla (`minecraft:block.sand.break`) or one declared in the same pack's `sounds.json` (`reign:glass_walk`) |
| `type=<type>` | which sound is being replaced, see below |
| `volume=<n>` | optional, 0–16, default 1.0 |
| `pitch=<n>` | optional, 0–16, default 1.0 |

| `type` | Plays when |
|---|---|
| `break` | the block is broken |
| `fall` | something lands on it |
| `hit` | it's struck while being mined |
| `idle` (or `ambient`) | on its own, near the player – see below |
| `place` | it's put down |
| `step` (or `walk`) | it's walked on |

`volume` and `pitch` set the sound group's base values, the same numbers vanilla blocks carry, and the engine still scales from there per action – footsteps are quieter than breaking. Give `type` with `volume`/`pitch` and **no** `sound=` to keep the vanilla sound and only change how it's played. `idle` is the exception: it has no vanilla sound to inherit, so it always needs `sound=`.

## Idle sounds

`type=idle` invents a sound the block never had – a hum, a creak, wind through a grate. Each tick the mod samples a handful of block positions around you, and a rule whose block is found there rolls its chance.

| Parameter | | Default |
|---|---|---|
| `idle_chance` | odds of playing when the scanner lands on the block, 0–1 | 0.002 |
| `idle_cooldown` | seconds this rule must wait before playing again | none |
| `idle_pitch_min` / `idle_pitch_max` | pitch is picked at random in this range | 0.9 – 1.1 |
| `idle_category` | which volume slider it answers to: `blocks`, `ambient`, `weather`, `players`, `music`, `hostile`, `neutral`, `record`, `voice`, `master` | `blocks` |
| `idle_air_above` | `true` = only with air directly above | off |
| `idle_sky` | `true` = only under open sky | off |
| `idle_cluster` | only where N more of the same block are within ±8 across and ±2 up | off |
| `idle_time` | `day` or `night` | any |
| `idle_weather` | `rain` or `clear` | any |

```json
"minecraft:brown_mushroom_block[...]": "sound=minecraft:block.amethyst_block.chime,type=idle,volume=0.7,idle_chance=0.5,idle_cooldown=4,idle_pitch_min=0.7,idle_pitch_max=1.4,idle_category=ambient"
```

The scanner itself is tuned once for the whole pack, under a reserved key. Set in several packs, the topmost wins:

```json
"customblocksounds:ambient": "idle_samples=64,idle_radius=24,idle_max_per_second=4"
```

| Parameter | | Default | Max |
|---|---|---|---|
| `idle_samples` | positions checked per tick | 6 | 700 |
| `idle_radius` | horizontal reach around the player, vertical is half that and never under 4 | 16 | 32 |
| `idle_max_per_second` | ceiling on idle sounds per second, all rules together | none | 1000 |

A single block at the defaults speaks about once every minute and a half. That is deliberate – it's meant to be scenery, not a metronome. To hear it while testing, push `idle_samples=700`, `idle_radius=8`, `idle_chance=1`.

## Sounds of your own

Declared the ordinary way, in `assets/<namespace>/sounds.json` next to the `.ogg` files:

```
assets/reign/sounds.json
assets/reign/sounds/custom/glass_walk.ogg
```

and then written into the blockmap as `sound=reign:glass_walk`.

A working blockmap covering every feature above is in [docs/example-blockmap.json](docs/example-blockmap.json).

## What it does and doesn't reach

**Your own footsteps, mining, placing and breaking** are replaced always, whatever the server is.

**Other players and mobs are replaced too,** even though the mod is client-side. Sounds arriving from the server are matched against the vanilla sound of the block at the position the packet names, and swapped when they agree. What stays vanilla is anything the server already replaced itself, through a plugin or a datapack.

Server sounds with no block behind them – damage, mob voices, records – are left alone.

## Notes

Vanilla sound ids are singular: `minecraft:block.sand.break`, not `blocks.sand.break`. An id that doesn't exist simply doesn't play, with a warning from the sound engine.

A line naming a block, property or type that doesn't exist is skipped, and says so in the log under `[CustomBlockSounds]`.

**F3+T** re-reads the blockmap, so a pack kept as a folder rather than a zip can be edited with the game running.

## Building

```
gradlew build
```

The jar lands in `build/libs/`. Built against 1.21.8 with Gradle 8.14.3 and Loom 1.11.8, Java 21, and the same jar runs on 1.21 through 1.21.11 – every intermediary name it touches was checked against all three mappings, and two that moved between versions are worked around: the registry lookup goes through `getOrEmpty`, and `SoundManager#play`, whose return type changed, is called through a one-time reflective lookup.

## Licence

MIT. Do what you like with it.

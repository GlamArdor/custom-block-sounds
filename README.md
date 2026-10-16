# Custom Block Sounds

**Let resource packs redefine block sounds - no coding, no datapacks, just one JSON file.**

Custom Block Sounds is a lightweight **client-side** Fabric mod that lets resource pack authors override the interaction sounds of any block - even of a single blockstate - and add **ambient (idle) sounds** to blocks that never had them.

## Features

- Override **break / fall / hit / place / step** sounds per block or per blockstate
- Add **idle (ambient) sounds** to any block - with chance, cooldown, day/night, weather, open-sky, air-above and cluster conditions
- Use **vanilla sounds** or **custom sounds** shipped in the same resource pack
- Per-sound **volume** and **pitch** control
- Other players' and mobs' footsteps, block placement and fall sounds are replaced too - server-sent sounds are matched against the block at their position
- Multiple resource packs merge naturally - the pack higher in the list wins
- Invalid entries never crash the game - they are skipped with a clear warning in the log

Works on **Minecraft 1.21 - 1.21.11** with one jar and on **Minecraft 26.2 - 26.3** with a second one (the `+26` file). Requires **Fabric API**. Fully client-side - safe to join any server.

---

# Setting up your resource pack

Create one file in your resource pack:

```
assets/customblocksounds/config/blockmap.json
```

Each line is one rule: the **key** is a block (optionally with blockstate properties), the **value** is a list of parameters. Duplicate keys are allowed - write one line per sound type.

```json
{
	"minecraft:brown_mushroom_block[down=false,east=false,north=false,south=false,up=false,west=true]": "sound=minecraft:block.sand.break,type=break,volume=1.0,pitch=0.8",
	"minecraft:brown_mushroom_block[down=false,east=false,north=false,south=false,up=false,west=true]": "sound=minecraft:block.sand.step,type=step",
	"minecraft:stone": "sound=mypack:custom_stone_step,type=step"
}
```

- Blockstate properties in `[...]` are optional and may be partial - a rule matches every state that has the listed values. A more specific rule (more properties) wins.
- `sound=` accepts any vanilla sound event (e.g. `minecraft:block.sand.break` - full list in vanilla `sounds.json`) or a sound declared in **your own pack's** `assets/<namespace>/sounds.json` (declare it in
  `sounds.json` as usual and put the `.ogg` next to it).

> **Tip:** press **F3+T** after editing blockmap.json - the config reloads without restarting the game.

## Parameters

| Parameter | Description |
|---|---|
| `type` | which sound to replace: `break`, `fall`, `hit`, `place`, `step` (alias `walk`), `idle` (alias `ambient`) |
| `sound` | sound event id; optional for non-idle types (omit it to keep the vanilla sound and only change volume/pitch) |
| `volume` | 0-16, default 1.0 |
| `pitch` | 0-16, default 1.0 |

## Idle (ambient) sounds

`type=idle` gives a block a looping ambience, like desert sand in vanilla 1.21.5+. Extra parameters:

| Parameter | Description | Default |
|---|---|---|
| `idle_chance` | chance to play when the scanner hits the block, 0-1 | 0.002 |
| `idle_cooldown` | minimum seconds between plays of this rule | 0 |
| `idle_pitch_min` / `idle_pitch_max` | random pitch range | 0.9-1.1 |
| `idle_category` | volume slider used: `blocks`, `ambient`, `weather`, `players`, `music`... | `blocks` |
| `idle_air_above` | `true` = only when the block above is air | off |
| `idle_sky` | `true` = only under open sky | off |
| `idle_cluster` | only when N same blocks are nearby (±8 horizontally, ±2 vertically) | off |
| `idle_time` | `day` or `night` | any |
| `idle_weather` | `rain` or `clear` | any |

Example - sand-like desert ambience:

```json
"minecraft:brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=false]": "sound=minecraft:block.sand.idle,type=idle,idle_chance=1.0,idle_cooldown=5,idle_air_above=true,idle_sky=true,idle_cluster=3,idle_time=day,idle_weather=clear"
```

## Global idle scanner settings

One reserved key per pack (the highest enabled pack wins):

```json
"customblocksounds:ambient": "idle_samples=64,idle_radius=24,idle_max_per_second=4"
```

| Parameter | Description | Default | Max |
|---|---|---|---|
| `idle_samples` | positions checked around the player per tick | 6 | 700 |
| `idle_radius` | horizontal scan radius (vertical = half) | 16 | 32 |
| `idle_max_per_second` | global cap on idle sounds per second | unlimited | 1000 |

> A single block is found rarely with default settings - that's vanilla-like desert density, tuned for areas of blocks. For clearly audible single blocks raise `idle_chance` and `idle_samples`.

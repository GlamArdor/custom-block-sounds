# Custom Block Sounds (Fabric, Minecraft 1.21–1.21.11)

Автор: **Glam_Ardor**

*English version: [README.md](../README.md).*

Клиентский мод, позволяющий ресурспакам подменять звуки взаимодействия с блоками
(**break / fall / hit / idle / place / step**) без изменения ванильных файлов звуков.

Один и тот же jar работает на Minecraft **с 1.21 по 1.21.11** (все 63
используемые intermediary-ссылки присутствуют в маппингах 1.21, 1.21.8 и
1.21.11; запуск проверен на 1.21, 1.21.8 и 1.21.11). Два нестабильных между
версиями вызова обойдены: поиск блока в реестре идёт через
`getOrEmpty`/`getOptionalValue` (intermediary `method_17966`), а
`SoundManager#play` (сменивший возвращаемый тип в новых версиях) вызывается
через одноразовый рефлексивный lookup.

## Как это работает

Мод ищет в каждом включённом ресурспаке файл:

```
assets/customblocksounds/config/blockmap.json
```

Если файл найден — правила из него применяются. Файлы из нескольких паков
объединяются: пак, стоящий **выше** в списке ресурспаков, перекрывает нижние.

## Формат blockmap.json

```json
{
	"minecraft:brown_mushroom_block[down=false,east=false,north=false,south=false,up=false,west=true]": "sound=minecraft:block.sand.break,type=break,volume=1.0,pitch=0.8",
	"minecraft:brown_mushroom_block[down=false,east=false,north=false,south=false,up=false,west=true]": "sound=minecraft:block.sand.step,type=step,volume=1.5,pitch=1.2",
	"minecraft:brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=false]": "sound=reign:glass_walk,type=walk,volume=0.8",
	"minecraft:brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=false]": "sound=reign:glass_break,type=break"
}
```

* **Ключ** — id блока с (опционально) свойствами blockstate в `[...]`.
  Можно указывать не все свойства: правило подойдёт ко всем состояниям,
  у которых совпадают перечисленные. Более конкретное правило (больше свойств)
  имеет приоритет. Дублирующиеся ключи разрешены — по одной строке на тип звука.
* **Значение** — параметры через запятую:
  * `sound=<namespace>:<sound_id>` — id звукового события: ванильного
    (например `minecraft:block.sand.break`) или объявленного в `sounds.json`
    этого же ресурспака (например `reign:glass_walk`).
  * `type=<тип>` — какой звук подменяется:

    | type | Когда играет |
    |---|---|
    | `break` | разрушение блока |
    | `fall` | падение сущности на блок |
    | `hit` | удары по блоку при добыче |
    | `idle` (или `ambient`) | случайный «фоновый» звук блока рядом с игроком |
    | `place` | установка блока |
    | `step` (или `walk`) | шаги по блоку |

  * `volume=<число>` *(опционально, 0–16, по умолчанию 1.0)* — громкость.
  * `pitch=<число>` *(опционально, 0–16, по умолчанию 1.0)* — высота тона.

  `volume`/`pitch` задают базовые значения звуковой группы для этого типа —
  так же, как у ванильных блоков (стандарт 1.0). Финальную громкость движок
  ещё масштабирует по действию: например, шаги играются тише разрушения.
  Можно указать `type` + `volume`/`pitch` **без** `sound=` — тогда ванильный
  звук останется, изменится только громкость/тон (кроме `idle`, которому
  `sound=` обязателен).

## Настройки idle-звуков (`idle_*`)

Дополнительные параметры в строках с `type=idle`:

| Параметр | Что делает | По умолчанию |
|---|---|---|
| `idle_chance` | шанс сыграть при попадании сканера в блок, 0–1 | 0.002 |
| `idle_cooldown` | минимальная пауза между срабатываниями правила, сек | 0 |
| `idle_pitch_min` / `idle_pitch_max` | случайный тон в диапазоне | 0.9–1.1 |
| `idle_category` | ползунок громкости: `blocks`, `ambient`, `weather`, `players`, `music`, `hostile`, `neutral`, `record`, `voice`, `master` | `blocks` |
| `idle_air_above` | `true` = только если над блоком воздух | выкл |
| `idle_sky` | `true` = только под открытым небом | выкл |
| `idle_cluster` | только если рядом (±8 по горизонтали, ±2 по высоте) есть N таких же блоков | выкл |
| `idle_time` | `day` / `night` | всегда |
| `idle_weather` | `rain` / `clear` | всегда |

Глобальные настройки сканера — отдельной строкой с зарезервированным ключом
(на весь пак; если ключ задан в нескольких паках, побеждает верхний):

```json
"customblocksounds:ambient": "idle_samples=64,idle_radius=24,idle_max_per_second=4"
```

| Параметр | Что делает | Дефолт | Максимум |
|---|---|---|---|
| `idle_samples` | проверяемых позиций за тик | 6 | 700 |
| `idle_radius` | радиус вокруг игрока по горизонтали (по вертикали — половина, минимум 4) | 16 | 32 |
| `idle_max_per_second` | общий лимит idle-звуков в секунду | без лимита | 1000 |

Пример полной idle-строки:

```json
"minecraft:brown_mushroom_block[...]": "sound=minecraft:block.amethyst_block.chime,type=idle,volume=0.7,idle_chance=0.5,idle_cooldown=4,idle_pitch_min=0.7,idle_pitch_max=1.4,idle_category=ambient"
```

Одиночный блок при настройках по умолчанию звучит примерно раз в полторы
минуты — это сделано намеренно, звук задуман фоном, а не метрономом. Чтобы
услышать его при проверке, выкрутите `idle_samples=700`, `idle_radius=8`,
`idle_chance=1`.

## Кастомные звуки в ресурспаке

Объявляются как обычно, в `assets/<namespace>/sounds.json` + `.ogg`-файлы, например:

```
assets/reign/sounds.json
assets/reign/sounds/custom/glass_walk.ogg
```

и затем используются в blockmap как `sound=reign:glass_walk`.

Готовый blockmap, где задействованы все возможности мода, лежит в
[example-blockmap.json](example-blockmap.json).

## Замечания и ограничения

* id ванильных звуков начинаются с `block.` (единственное число):
  `minecraft:block.sand.break`, а не `blocks.sand.break`. Полный список — в
  ванильном `sounds.json`. Неизвестный id просто не проиграется (в логе будет
  предупреждение от звукового движка).
* Мод **клиентский**, но перехватывает и серверные звуки: шаги/установка/падение
  других игроков и мобов подменяются сопоставлением звука из пакета с ванильным
  звуком блока в его позиции (`ClientPlayNetworkHandler#onPlaySound`). Ванильными
  остаются только звуки, уже заменённые на стороне сервера плагином/модом.
* Свои шаги/установка/добыча/разрушение блоков подменяются всегда, независимо
  от сервера. Серверные звуки, не привязанные к блокам (урон, крики мобов и
  т.п.), мод не трогает.
* Ошибочные строки (неизвестный блок, свойство, тип) пропускаются с
  предупреждением в логе `[CustomBlockSounds]`.
* Перезагрузка ресурсов (**F3+T**) перечитывает blockmap.json.

## Сборка

```
gradlew build
```

Готовый jar: `build/libs/customblocksounds-1.0.0.jar` (кладётся в папку `mods`
вместе с Fabric API, подходящим под версию игры). Один jar подходит для
1.21–1.21.11.

Проект собирается против 1.21.8 (Gradle 8.14.3 + Loom 1.11.8). Для сборки
против 1.21.11 нужно переключить в `gradle.properties`:
`minecraft_version=1.21.11`, `yarn_mappings=1.21.11+build.6`,
`fabric_api_version=0.141.4+1.21.11`, `loom_version=1.15.5` и Gradle 9.5.1
в `gradle/wrapper/gradle-wrapper.properties` (Loom 1.15 требует Gradle 9).

## Лицензия

MIT.

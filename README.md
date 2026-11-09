# Oritech Extensions

Small add-ons for [Oritech](https://github.com/Rearth/Oritech) on **NeoForge 1.21.1** (built for All the Mods 10).

## Hangar Door Extension

Oritech's Industrial Hangar Door is 3 blocks long. The **Industrial Hangar Door Extension** makes it longer.

- Place an extension on the tip of a hangar door. Each extension adds 1 block. You can keep adding extensions on the end of the last one.
- The door is **16 blocks long at most by default** (Oritech's 3 + 13 extensions). You can change this in the config.
- Extensions copy the door's direction, rotation and open/closed state, and they open and close with it. That includes doors linked to their neighbours.
- When the door opens, the extensions slide back into the door's housing, timed to Oritech's own animation. When it closes, they slide back out.
- They use Oritech's hangar door texture and match the door's painted colour.
- Break the door, or any extension, and every extension past it drops as an item.

**Recipe:** 1 Industrial Hangar Door → 3 Hangar Door Extensions (shapeless).

### Config

`config/oritech_extensions-common.toml`, or Mods → Oritech Extensions → Config in game:

| Key | Default | Range | Meaning |
|---|---|---|---|
| `maxHangarDoorLength` | 16 | 4–32 | Total door length in blocks, including Oritech's 3 |

A ceiling door and a floor door that meet in the middle cover twice the configured length. For example, set 20 to close a 40-block opening.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.200+
- Oritech 1.2.x (NeoForge)
- GeckoLib 4.6+

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`. Every push also builds on GitHub Actions. Download the jar from the run's **oritech-extensions-jar** artifact.

## Layout

```
src/main/java/dev/garciajg/oritechextensions/
  OritechExtensions.java        mod entry point
  Config.java                   common config
  ModContent.java               registry (blocks, items, block entities)
  hangar/                       hangar door extension: block + block entity
  client/                       renderer, GeckoLib model, client setup
```

New add-ons go in their own package next to `hangar/`.

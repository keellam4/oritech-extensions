# Oritech Extensions

[![Build](https://github.com/keellam4/oritech-extensions/actions/workflows/build.yml/badge.svg)](https://github.com/keellam4/oritech-extensions/actions/workflows/build.yml)
[![Modrinth](https://img.shields.io/badge/Modrinth-oritech--extensions-1bd96a)](https://modrinth.com/mod/oritech-extensions)
[![CurseForge](https://img.shields.io/badge/CurseForge-oritech--extensions-f16436)](https://www.curseforge.com/minecraft/mc-mods/oritech-extensions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Small add-ons for [Oritech](https://github.com/Rearth/Oritech) on **NeoForge 1.21.1** (built for All the Mods 10).

Found a bug or have an idea? [Open an issue](https://github.com/keellam4/oritech-extensions/issues/new/choose). Want to help? See [CONTRIBUTING.md](CONTRIBUTING.md).

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

## Paintable machine addons

Oritech's machine paints (Color Cartridges) normally only work on its big machines. With this mod they also work on **machine addon blocks**: Oritech's own addons and those from mods built on them, such as **Oritech Things'** tiered addons.

- Right-click or shift-right-click an addon with a paint. It uses one cartridge (none in creative), the same as painting a machine.
- Orange Machine Paint returns the block to its normal look.
- A painted addon keeps its colour when you break it and place it again. The item tooltip shows the paint.
- Painted textures are generated from the original textures when the game loads, using the colour swap Oritech uses for its own paints. Nothing from the other mods is copied into this one.

| Addon | Paints cleanly? |
|---|---|
| Oritech: Speed, Efficiency, Ultimate, Processing, Capacitor, Fluid, Yield, Hunter, Silk Touch, Burst, Crop Filter, Quarry, Steam Boiler, Inventory Proxy, Capacitor Addon Extender | Yes |
| Oritech: Extender, Acceptor | Partly. They keep some of their own orange shades |
| Oritech Things: Speed, Efficiency, Efficient Speed, Processing, Capacitor (all tiers), Cross Dimensional | Yes |
| Oritech Things: Acceptor (all tiers) | Barely changes. It uses its own oranges |

The list of recoloured textures is in `src/main/resources/assets/minecraft/atlases/blocks.json`. The palettes come from `tools/paint/generate_palettes.py`.

Oritech Things is © its authors, licensed CC-BY 4.0. This mod only reads its textures at runtime to recolour them.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.200+
- Oritech 1.2.x (NeoForge)
- GeckoLib 4.6+
- Optional: Oritech Things (its addons become paintable)

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`. Every push also builds on GitHub Actions. Download the jar from the run's **oritech-extensions-jar** artifact.

CI also runs:
- `runGameTestServer`: server-side game tests against Oritech 1.2.13 and Oritech Things 0.0.48 (hangar doors, painting).
- `runClientSmokeTest`: starts the real client headless and checks that the painted textures exist and the addon models get wrapped.

## Releasing

Releases are published to Modrinth, CurseForge and GitHub Releases by `.github/workflows/release.yml`:

1. Set `mod_version` (and `release_type`: `alpha`, `beta` or `release`) in `gradle.properties`.
2. Add a `## [<version>]` section to `CHANGELOG.md`. It becomes the release notes.
3. Merge to `main`, then tag: `git tag v<version> && git push origin v<version>`.

One-time setup, in **Settings → Secrets and variables → Actions**:
- Variables: `MODRINTH_PROJECT_ID`, `CURSEFORGE_PROJECT_ID`
- Secrets: `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`

A platform without its ID or token is skipped. The icon is drawn by `tools/branding/icon.py`.

## License

MIT. See `LICENSE`.

## Layout

```
src/main/java/dev/garciajg/oritechextensions/
  OritechExtensions.java        mod entry point
  Config.java                   common config
  ModContent.java               registry (blocks, items, block entities)
  hangar/                       hangar door extension: block + block entity
  paint/                        addon painting: cartridge handling, storage, sync
  client/                       renderers, GeckoLib model, painted addon models, client setup
  test/                         game tests
```

New add-ons go in their own package next to `hangar/`.

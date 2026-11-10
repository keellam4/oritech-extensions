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

## Paintable standard pipes

Oritech's Color Cartridges can paint **standard item pipes** and **standard fluid pipes** (including their connection segments).

- Right-click a pipe with a cartridge. The whole connected network of that type is painted.
- Orange Machine Paint clears the network back to the default look.
- Colour is stored in the world only. Broken pipes always drop unpainted.
- A newly placed standard pipe inherits colour if it touches a same-type painted neighbour of one consistent colour. Conflicting neighbours leave it unpainted.
- **Not painted in this version:** framed, duct, transparent item pipes, or energy pipes. Item filters and smart splitters keep Oritech's own paint behaviour.

### Pipe paint config

| Key | Default | Meaning |
|---|---|---|
| `pipePaintCostMode` | `NETWORK` | `NETWORK` = one cartridge for the whole network; `PER_BLOCK` = one cartridge per pipe block (spreads until the stack runs out) |
| `pipePaintIsolation` | `false` | Reserved for a future release (same-colour connection gating). Unused today. |

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

## In-game wiki (Oracle Index)

Documentation lives in `wiki/` and is copied into the jar for [Oracle Index](https://modrinth.com/mod/oracle-index) (optional). With Oracle Index installed, open the wiki (default **H**) and pick **Oritech Extensions**. Pages cover hangar doors, addon paint, and pipe paint.

Edit files under `wiki/`; `./gradlew build` (or any task that runs `processResources`) syncs them into `assets/oracle_index/books/oritech_extensions/`.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.200+
- Oritech 1.2.x (NeoForge)
- GeckoLib 4.6+
- Optional: Oritech Things (its addons become paintable)
- Optional: Oracle Index (in-game wiki viewer)

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`. Every push also builds on GitHub Actions. Download the jar from the run's **oritech-extensions-jar** artifact.

CI also runs:
- `runGameTestServer`: server-side game tests against Oritech 1.2.13 and Oritech Things 0.0.48 (hangar doors, painting).
- `runClientSmokeTest`: starts the real client headless and checks that the painted textures exist and the addon models get wrapped.

## Releasing

Pushing to `main` only builds and tests. Players get an update when you publish a GitHub Release:

1. **Release PR.** Bump `mod_version` (and `release_type`: `alpha`, `beta` or `release`) in `gradle.properties`. In `CHANGELOG.md`, rename `## [Unreleased]` to `## [<version>] - Beta` and add a new empty `## [Unreleased]` above it. Merge once CI passes.
2. **Publish the release.** Go to **Releases → Draft a new release → Choose a tag**, type `v<version>`, pick **Create new tag on publish** (target `main`), and click **Publish release**. You can leave the notes empty; they're filled in from the changelog.
3. **Done.** The **Release** workflow builds, tests, attaches the jar to the GitHub release, and uploads it to Modrinth and CurseForge. Beta and alpha versions are marked as pre-releases.

One-time setup in **Settings → Secrets and variables → Actions**:
- Variables: `MODRINTH_PROJECT_ID`, `CURSEFORGE_PROJECT_ID`
- Secrets: `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`

A platform whose project ID variable isn't set is skipped.

## Workflows

| Workflow | When | What |
|---|---|---|
| Build | Pull requests, pushes to `main` | Compile, game tests, headless client smoke test, upload jar (required check) |
| Changelog | Pull requests | Reminds you to update `CHANGELOG.md` (skip with the `skip-changelog` label) |
| Release | A GitHub Release is published | Build, test, publish to GitHub, Modrinth, CurseForge |
| Labels | `.github/labels.json` changes on `main` | Creates or updates issue labels |

The icon is drawn by `tools/branding/icon.py`.

## License

MIT. See `LICENSE`.

## Layout

```
src/main/java/dev/garciajg/oritechextensions/
  OritechExtensions.java        mod entry point
  Config.java                   common config
  ModContent.java               registry (blocks, items, block entities)
  hangar/                       hangar door extension: block + block entity
  paint/                        addon + pipe painting: cartridge handling, storage, sync
  client/                       renderers, GeckoLib model, painted models, client setup
  test/                         game tests
```

New add-ons go in their own package next to `hangar/`.

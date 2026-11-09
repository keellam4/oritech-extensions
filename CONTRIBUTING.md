# Contributing

Bug reports, ideas and pull requests are welcome.

## Reporting a bug
Open a [bug report](https://github.com/keellam4/oritech-extensions/issues/new/choose) and include your mod, Oritech and NeoForge versions. For visual problems, a screenshot is the most useful thing you can send. For crashes, attach `logs/latest.log` or the crash report.

## Building
You need Java 21.

```sh
./gradlew build              # jar ends up in build/libs/
./gradlew runGameTestServer  # server-side game tests against Oritech + Oritech Things
./gradlew runClient          # dev client (needs -Pathena_version_id=<Modrinth id of Athena>)
```

Every pull request runs the build, the game tests, and a headless client smoke test in GitHub Actions. All of them have to pass before merging.

## Project layout
| Path | What |
|---|---|
| `src/main/java/.../hangar/` | Hangar Door Extension block and block entity |
| `src/main/java/.../paint/` | Painting machine addons: cartridge handling, storage, sync |
| `src/main/java/.../client/` | Renderers, models, client setup |
| `src/main/java/.../test/` | Game tests |
| `src/main/resources/assets/minecraft/atlases/blocks.json` | Which textures get painted versions |
| `tools/paint/generate_palettes.py` | Rebuilds the paint palettes from an Oritech jar |

## Making another addon texture paintable
1. Check that the texture uses Oritech's orange accent shades (`src/main/resources/assets/oritech_extensions/textures/colormap/paint/key.png`).
2. Add the texture to the `textures` list in `assets/minecraft/atlases/blocks.json`.
3. If the block isn't built on Oritech's `MachineAddonBlock`, it also needs code changes. Open an issue first.

## Pull requests
- Keep each PR to one change, and add a line to `CHANGELOG.md`.
- Only include textures or code you made yourself or that are under a compatible license. Oritech is CC0; Oritech Things is CC-BY 4.0 and is only read at runtime, never copied into this repo.
- By contributing, you agree your contribution is licensed under the project's MIT license.

## Releases
Releases are made by the maintainer by pushing a `v<version>` tag. See "Releasing" in the README.

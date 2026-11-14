# Contributing

Bug reports, ideas and pull requests are welcome. Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

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
| `src/main/java/.../client/` | Renderers, models, client setup (plus `IconExporter`, which renders item icons for the docs site) |
| `src/main/java/.../guide/` | The guide book item (opens the wiki in Oracle Index) |
| `src/main/java/.../test/` | Game tests |
| `src/main/resources/assets/minecraft/atlases/blocks.json` | Which textures get painted versions |
| `tools/paint/generate_palettes.py` | Rebuilds the paint palettes from an Oritech jar |
| `wiki/` | Documentation: in-game guide, website and GitHub. See [`wiki/README.md`](wiki/README.md) |
| `tools/wiki/` | Builds the GitHub Pages site from `wiki/` (incl. the 3D paint viewer in `site/javascripts/`) |

## Making another addon texture paintable
1. Check that the texture uses Oritech's orange accent shades (`src/main/resources/assets/oritech_extensions/textures/colormap/paint/key.png`).
2. Add the texture to the `textures` list in `assets/minecraft/atlases/blocks.json`.
3. If the block isn't built on Oritech's `MachineAddonBlock`, it also needs code changes. Open an issue first.

## Pull requests
- Keep each PR to one change.
- New item, block or feature? Add or update its page in `wiki/` in the same PR ([how](wiki/README.md#adding-a-page)).
- Add a line to `CHANGELOG.md` under `## [Unreleased]` for anything players would notice. A maintainer adds the `skip-changelog` label for docs and CI changes.
- Only include textures or code you made yourself or that are under a compatible license. Oritech is CC0; Oritech Things is CC-BY 4.0 and is only read at runtime, never copied into this repo.
- By contributing, you agree your contribution is licensed under the project's MIT license.

## Releases
The maintainer publishes releases from GitHub Releases. See "Releasing" in the README.

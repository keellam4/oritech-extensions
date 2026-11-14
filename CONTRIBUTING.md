# Contributing

Bug reports, ideas and pull requests are welcome. Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting a bug
Open a [bug report](https://github.com/keellam4/oritech-extensions/issues/new/choose) and include your mod, Oritech and NeoForge versions. For visual problems, a screenshot is the most useful thing you can send. For crashes, attach `logs/latest.log` or the crash report.

## Building and code
Everything technical is in the [developer docs](https://keellam4.github.io/oritech-extensions/developers/overview/) (source: [`wiki/developers/`](wiki/developers/)):

- [Building and Testing](https://keellam4.github.io/oritech-extensions/developers/building/): requirements, Gradle commands, tests, CI
- [Minecraft Versions](https://keellam4.github.io/oritech-extensions/developers/versions/): which branch targets which Minecraft and Oritech version
- [Project Layout](https://keellam4.github.io/oritech-extensions/developers/project_layout/)
- [The Paint System](https://keellam4.github.io/oritech-extensions/developers/paint_system/): including how to make another block paintable

## Pull requests
- Keep each PR to one change.
- New item, block or feature? Add or update its player page in `wiki/docs/` or `wiki/content/` in the same PR ([how](wiki/README.md#adding-a-page)). Technical notes go in `wiki/developers/`.
- Add a line to `CHANGELOG.md` under `## [Unreleased]` for anything players would notice. A maintainer adds the `skip-changelog` label for docs and CI changes.
- Only include textures or code you made yourself or that are under a compatible license. Oritech is CC0; Oritech Things is CC-BY 4.0 and is only read at runtime, never copied into this repo.
- By contributing, you agree your contribution is licensed under the project's MIT license.

## Releases
The maintainer publishes releases from GitHub Releases. See [Releasing](https://keellam4.github.io/oritech-extensions/developers/releasing/).

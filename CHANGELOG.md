# Changelog

Add a line under **[Unreleased]** in every pull request that players would notice. The release PR renames it to the new version.

## [Unreleased]

## [0.2.0] - Beta

### Added
- **Paintable standard item and fluid pipes.** Oritech Color Cartridges paint a whole connected standard pipe network (item or fluid). Orange clears the colour. Colour is world-only (broken pipes drop unpainted). New pipes inherit when they touch a same-type neighbour of one colour. Cost mode is configurable (`network` = one cartridge per network, or `per_block`). Transparent item pipes paint together with standard item pipes. Framed, duct and energy pipes are not painted yet.
- **Oritech Extensions Guide.** A craftable book (Book + any nickel ingot, so AllTheOres nickel works in ATM10) that opens this mod's wiki in-game through Oracle Index. Without Oracle Index it posts a link to the online docs.
- **Accent trim on painted extenders.** The Machine Addon Extender and Capacitor Addon Extender swap their red trim for a matching accent (for example gold on netherite, charcoal on industrial).
- **Documentation.** A wiki covering hangar doors, addon paint, and pipe paint. Read it in-game with Oracle Index (key H or the guide book), or online at https://keellam4.github.io/oritech-extensions/.

### Changed
- The in-game mod list now links to the issue tracker and source code.

## [0.1.0] - Beta

First public beta, for Minecraft 1.21.1 (NeoForge) and Oritech 1.2.x.

### Added
- **Industrial Hangar Door Extension.** Place it on the tip of an Oritech hangar door to make the door 1 block longer, and keep stacking up to a 16-block door (Oritech's 3 + 13 extensions). The limit is configurable from 4 to 32.
  - Opens and closes with the door, including linked doors, and slides into the door's housing in time with Oritech's animation.
  - Matches the door's paint colour.
  - Breaking the door drops every extension.
  - Recipe: 1 Industrial Hangar Door → 3 extensions.
- **Paintable machine addons.** Oritech's machine paints now work on machine addon blocks: Oritech's own (Speed, Efficiency, Capacitor, Processing, and more, including the Capacitor Addon Extender) and Oritech Things' tiered addons.
  - Uses one cartridge per coat. Orange removes the paint.
  - Painted addons keep their colour when broken and placed again.

### Known limitations
- Oritech's Extender and Acceptor, and Oritech Things' Acceptor, use some orange shades of their own, so they only partly change colour.

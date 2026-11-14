# Changelog

Add a line under **[Unreleased]** in every pull request that players would notice. The release PR renames it to the new version.

## [Unreleased]

## [0.4.0+mc26.1] - Beta (Minecraft 26.1.2)

### Added
- **Colour-isolated pipes.** Painted item and fluid pipes only connect to pipes of the same colour, so two lines can run side by side without merging. Unpainted pipes still connect to every colour. Painting a line no longer spreads into touching pipes of another colour. Controlled by `pipePaintIsolation` (on by default for new configs; existing config files keep `false` until you change it).

## [0.3.0+mc26.1] - Beta (Minecraft 26.1.2)

### Added
- **Minecraft 26.1 build** (NeoForge 26.1.2, Oritech 2.0, GeckoLib 5), for packs like FTB Evolution 2. It has the hangar door extension, the guide, and paint for pipes, Industrial Glass, reactors and particle accelerators. Machine addon and extender paint isn't included there because Oritech 2.0 paints addons itself.

## [0.3.0] - Beta

### Added
- **Paintable Industrial Glass, reactors and particle accelerators.** Color Cartridges paint a whole wall of Industrial Glass, a whole nuclear reactor (walls, controller, rods, vents, heat pipes and ports) or a whole particle accelerator (controller, rings, motors, sensors, collectors) in one click. Status lights keep their colours. Like pipes, the colour is world-only and new blocks take the colour of the build they join. New config option `maxPaintGroupSize` (default 4096).

### Changed
- Painting now goes through one shared system for every paintable block, ready for the Detailing Tool. Clicking a hangar door extension with a cartridge now paints the whole door.

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

# Changelog

Add a line under **[Unreleased]** in every pull request that players would notice. The release PR renames it to the new version.

## [Unreleased]

The storage and wireless update: a connected storage wall with crafting, a multiblock fluid silo, wireless charging, and new ways to paint your factory. Everything here is for Minecraft 1.21.1 on NeoForge and Fabric; the 26.1.2 version doesn't have it yet.

### Storage
- **Cargo Vaults.** Bulk storage for one item type, with the item and its count shown in the glass. Dual, Quad and Grid Vaults hold 2, 4 or 16 item types; Compacting Vaults convert between nuggets, ingots and blocks. A broken vault keeps its contents.
- **Fluid Vault.** A 256-bucket tank in vault form.
- **Vault upgrades.** Capacity (×2 to ×32), Void, Pull, Push and Redstone. Lock a vault to keep its item type, even while it's empty. Empty vaults stack to 64.
- **Cargo Controller.** Joins every touching vault into one inventory, with a searchable terminal and one pipe connection for the whole wall.
- **Crafting Terminal.** The terminal with a crafting grid that refills from the wall, and JEI's "+" button. Uses 100 RF per craft.
- **Fluid Silo.** A 3 × 3 multiblock tank, 3 to 9 blocks tall, holding up to 81,000 buckets. Partition upgrades let it hold several fluids, each with its own limit, reserve and void.

### Wireless
- **Induction Pylon.** Charges the energy items you carry within 8 blocks, including Curios slots on NeoForge. Three styles.
- **Cargo Link.** Opens your wall's terminal from 32 blocks away, further with an Uplink Mast and Antenna Segments, and anywhere with the Infinite Range or Dimensional upgrade. Each transfer uses a little battery.
- **Network Anchor.** Keeps a Cargo Wall chunk-loaded.

### Painting
- **Detailing Tool.** Holds Color Cartridges and paints a group, a single block or a whole area, including Oritech's own machines.
- **Rainbow Color Cartridge.** Paint that keeps cycling through the colours.
- **Tinted glass.** Paint Industrial Glass its own colour to tint the pane.
- **More paintable blocks:** Metal Beams and Girders, Pipe Boosters, pipe ducts, framed pipes, energy pipes and superconductors. Modpacks can make more blocks paintable with block tags.

### Also new
- **Transparent Fluid Pipe**, which shows the fluid flowing through it.
- A creative tab, and in-game wiki pages for every block and item.

### Fixed
- The config screen showed a raw key instead of a name for the colour-isolated pipes option.

## [0.4.1+fabric] - Beta (Fabric)

First release for **Fabric** (Minecraft 1.21.1). Needs Oritech 1.2.x for Fabric, Fabric API, Architectury API, Forge Config API Port and GeckoLib (Oritech needs all of them too). Quilt isn't tested.

Same features as the NeoForge version:
- Industrial Hangar Door Extensions (up to 16 blocks by default, configurable).
- Color Cartridges paint machine addons and extenders, item and fluid pipe networks (with colour-isolated pipes), Industrial Glass, whole nuclear reactors and whole particle accelerators.
- Paintable block tags for modpacks (`oritech_extensions:paintable` and `oritech_extensions:paintable_connected`).
- The Oritech Extensions Guide, with the in-game wiki through Oracle Index.

Not on Fabric:
- Oritech Things addon painting, because Oritech Things has no Fabric build.
- An in-game config screen. Edit `config/oritech_extensions-common.toml` instead; the options are the same as on NeoForge.

## [0.4.0] - Beta

### Added
- **Colour-isolated pipes.** Painted item and fluid pipes only connect to pipes of the same colour, so two lines can run side by side without merging. Unpainted pipes still connect to every colour. Painting a line no longer spreads into touching pipes of another colour. Controlled by `pipePaintIsolation` (on by default for new configs; existing config files keep `false` until you change it).

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

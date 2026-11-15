# Changelog

Add a line under **[Unreleased]** in every pull request that players would notice. The release PR renames it to the new version.

## [Unreleased]

### Added
- **Dual, Quad and Grid Vaults.** Two, four or sixteen glass windows, one item type each, sharing the vault's space. Click the window you want to put items in or take them out. Pipes see one slot per window.
- **Compacting Vault.** Stores one item in up to three forms found from crafting recipes (nuggets, ingots and blocks; clay balls and clay; and so on). Put in or take out any form and it converts. The **Dual Compacting Vault** does the same with two forms, for items that only compact once.
- **Vault upgrades.** Right-click a vault's side or top to open its upgrade screen with four slots. Capacity upgrades (Steel ×2, Electrum ×4, Energite ×8, Duratium ×16, Prometheum ×32) add up; Void deletes overflow of items the vault holds; Pull and Push move items with the inventory behind the vault; Redstone makes the vault give off a signal as strong as it is full, and a redstone signal pauses Pull and Push. Upgrades stay in a broken vault.
- **Vault readout.** Aim at any vault's window to see its item and how many it holds out of how many fit.
- **Cargo Vault.** Bulk storage for one item type (64 stacks by default, `cargoVaultStacks`). The item floats in the vault's glass front with its count, and the neon strip above it fills up as the vault does. Right-click to put items in (twice quickly, or with an empty hand, for every matching item), left-click the front to take a stack (sneak: one), sneak + right-click with an empty hand to lock the item type. Pipes and comparators work with it. Breaking it keeps its items and paint. Touching vaults paint together.
- **Detailing Tool.** A paintbrush that holds Color Cartridges: right-click them onto it in your inventory (up to 64 of each colour, Rainbow included). Sneak + scroll switches colour, sneak + right-click a block picks its colour, sneak + right-click the air switches between Group, Single block and Area mode. Area mode paints everything in a box between two clicked corners (up to 32 blocks a side, `detailingToolAreaSize`). Paints everything cartridges paint, plus Oritech's own machines, at the same cost. Crafted from an adamant, a steel and a nickel ingot.
- **Rainbow Color Cartridge.** Paints like a Color Cartridge, but the block keeps blending through red, yellow, green, cyan, blue and pink, in step with every other rainbow block. Works on everything this mod paints (not hangar doors or Oritech's own machines). Crafted from one each of the redstone, industrial, camo, adamant, sculk and fluxite cartridges, giving 6. Rainbow pipes only connect to rainbow pipes.
- **More paintable blocks.** Color Cartridges now also paint Metal Beams and Metal Girders (a touching support structure paints in one click), Pipe Boosters (one at a time), item and fluid pipe ducts and framed pipes (with their pipe network), and energy pipes and superconductors with their framed and duct versions (their orange connectors, light duct rings and teal parts take the colour). Colour-isolated pipes work for all of them, including ducts placed side by side.
- **Paintable block tags for modpacks.** Add blocks to `oritech_extensions:paintable` (one block per click) or `oritech_extensions:paintable_connected` (touching blocks paint together) with a datapack, and list their textures in the painted-texture atlas with a resource pack. See "For Modpack Makers" on the website.

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

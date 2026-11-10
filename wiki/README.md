# Oritech Extensions wiki

This folder is the documentation for the mod. Write a page once here and it shows up in three places:

| Where | How it gets there |
|---|---|
| **In-game** (craftable *Oritech Extensions Guide*, or the **H** key) | `./gradlew build` copies this folder into the jar for [Oracle Index](https://modrinth.com/mod/oracle-index) |
| **Website**: <https://keellam4.github.io/oritech-extensions/> | `.github/workflows/docs.yml` builds it with `tools/wiki/build_site.py` on every push to `main` |
| **Here on GitHub** | Browse the `.mdx` files below |

It uses the [moddedmc.wiki](https://moddedmc.wiki) layout (`sinytra-wiki.json`), so it can also be listed there later.

## Pages

**Guide** (`docs/`): how things work

- [Introduction](docs/intro.mdx): home page, in-game and on the website
- [Getting Started](docs/getting_started.mdx): requirements, the guide book, config
- Features
  - [Paintable Machine Addons](docs/features/paintable_addons.mdx)
  - [Paintable Pipes](docs/features/paintable_pipes.mdx)

**Items & Blocks** (`content/`): one page per item or block

- [Oritech Extensions Guide](content/guide.mdx)
- [Hangar Door Extension](content/hangar_door_extension.mdx)

## Adding a page

1. **Pick the section.**
   - A new item or block → `content/<item_name>.mdx`. Set `id:` to the item's registry id. Oracle Index then lets players hold **Alt** on that item's tooltip to open the page.
   - A mechanic, feature, or how-to → `docs/` (group related pages in a folder, like `docs/features/`).
2. **Start with frontmatter:**
   ```mdx
   ---
   title: Paintable Pipes
   icon: oritech:item_pipe                   # item shown next to the page title in-game
   id: oritech_extensions:my_block           # content/ pages only
   related_items: ["oritech:item_pipe"]      # optional, linked in-game
   ---
   ```
3. **List it in the folder's `_meta.json`.** Order there is the order in the sidebar, in-game and on the website. A page missing from `_meta.json` is hidden in-game, so the check below fails if you forget.
4. **Check it:** `python3 tools/wiki/build_site.py --check`. CI runs the same check on pull requests.
5. Update the page list above.

### What you can use

Plain Markdown (headings, lists, tables, links, code blocks) works everywhere. Oracle Index's in-game renderer is simpler than a browser, so stick to these extras:

| Syntax | Use |
|---|---|
| `<Callout variant="tip" title="...">…</Callout>` | Note / tip / warning / danger box (`collapsible`, `collapsed` optional) |
| `> [!WARNING]` | Same, GitHub-alert style |
| `[](@oritech_extensions:guide)` | Link to an item's page (an empty label uses the page/item name) |
| `[text](getting_started)` | Link to another page, relative, no `.mdx` |
| `![Caption](@oritech_extensions:pipes/layout)` | Image from `wiki/assets/oritech_extensions/pipes/layout.png` |
| `<CraftingRecipe slots={[...9 ids...]} result="..." count={1} />` | Crafting-table recipe with item icons (see below) |
| `<VideoEmbed id="youtubeId" />` | YouTube video (a button in-game) |
| `?[word](hover text)` | Hover tooltip |

The website build fails on broken links, missing images, and pages not in `_meta.json`.

### Recipes and item icons

`<CraftingRecipe>` shows as a crafting grid in-game, and on the website as a Minecraft-style panel with item icons and a large picture of the result. For this mod's own items, the build also compares the recipe on the page with the real one in `src/main/resources/data/oritech_extensions/recipe/` and fails if the ingredients or count don't match.

Icons are the real inventory icons. CI starts the game client headless (`./gradlew runClientIconExport`) and renders every item the pages use, including 3D and GeckoLib models like the hangar door. If that step can't run (for example in a local preview without Java), icons are drawn from textures instead: flat items from the vanilla/Oritech jars (`tools/wiki/fetch_assets.py`, never committed) and full blocks as an isometric cube. Items with custom models then show their name. To replace any icon by hand, add a PNG at `wiki/assets/items/<namespace>/<item>.png`.

### 3D paint viewer (website)

Add `paint_viewer: ["oritech:machine_speed_addon", "oritech:item_pipe"]` to a page's frontmatter to put a rotating 3D model of those blocks at the top of the page, with buttons to switch block and paint colour. The game ignores this key, so it only shows on the website.

It draws the blocks from their model files: a pipe's straight piece, an addon's "on" model, otherwise the item's block model. The painted versions use the same palettes as the game (`assets/minecraft/atlases/blocks.json`), so what you see matches in-game. Blocks drawn in code (GeckoLib, like the hangar door) can't be shown this way, and the build tells you if a block can't be drawn. Local previews need `npm install --prefix build/npm three@0.170.0` once.

### Previewing

- **Website:** `pip install -r tools/wiki/requirements.txt`, then `python3 tools/wiki/fetch_assets.py` once (for icons; for real rendered icons also run `python3 tools/wiki/build_site.py --list-items && ./gradlew runClientIconExport`), then `python3 tools/wiki/build_site.py && mkdocs serve -f build/wiki-site/mkdocs.yml` (re-run the script after editing).
- **In-game:** `./gradlew runClient` with Oracle Index in your dev mods. Oracle Index has no hot reload, so restart to see changes.

### Translations

Copy a page to `translated/<locale>/docs/...` (for example `translated/de_de/docs/intro.mdx`). Oracle Index uses it automatically for that game language.

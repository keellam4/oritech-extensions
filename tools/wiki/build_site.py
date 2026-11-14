#!/usr/bin/env python3
"""Turn wiki/ (moddedmc.wiki / Oracle Index format) into a MkDocs Material site for GitHub Pages.

wiki/ is the single source for the docs. The same files are:
  * shipped in the jar for Oracle Index (the in-game guide book), via the syncOracleWiki Gradle task,
  * readable on GitHub as-is, and
  * built into the website by this script.

Usage:
  python3 tools/wiki/build_site.py --check        # validate wiki/ only (fast, no MkDocs needed)
  python3 tools/wiki/build_site.py                # validate, then write build/wiki-site/ (mkdocs.yml + docs/)
  mkdocs build -f build/wiki-site/mkdocs.yml      # render HTML into build/wiki-site/site/
  mkdocs serve -f build/wiki-site/mkdocs.yml      # live preview (re-run this script after editing wiki/)
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import shutil
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WIKI = ROOT / "wiki"
OUT = ROOT / "build" / "wiki-site"
BASE_CONFIG = Path(__file__).with_name("mkdocs.base.yml")
LANG = ROOT / "src/main/resources/assets/oritech_extensions/lang/en_us.json"
# "developers" is website-only: build.gradle leaves it out of the in-game (Oracle Index) book.
SECTIONS = {"docs": "Guide", "content": "Items & Blocks", "developers": "Developers"}
THREE_JS = ROOT / "build/npm/node_modules/three/build/three.module.min.js"  # npm install --prefix build/npm three@0.170.0

ADMONITION = {"note": "note", "info": "info", "default": "note", "tip": "tip", "important": "important",
              "warning": "warning", "caution": "danger", "danger": "danger", "error": "danger"}


@dataclass
class Page:
    src: Path                     # wiki/docs/intro.mdx
    section: str                  # "docs", "content" or "developers"
    rel: str                      # path inside the section without extension: "intro", "pipes/paint"
    meta: dict = field(default_factory=dict)
    body: str = ""

    @property
    def out(self) -> str:         # path inside the generated docs/ folder
        if self.section == "docs" and self.rel == "intro":
            return "index.md"
        return f"{self.section}/{self.rel}.md"

    @property
    def title(self) -> str:
        return self.meta.get("title") or self.rel.rsplit("/", 1)[-1].replace("_", " ").title()


# ---------------------------------------------------------------- reading

def parse_frontmatter(text: str) -> tuple[dict, str]:
    """Small YAML subset: `key: value`, quoted strings and JSON-style arrays. Enough for wiki frontmatter."""
    if not text.startswith("---"):
        return {}, text
    end = text.find("\n---", 3)
    if end < 0:
        return {}, text
    meta = {}
    for line in text[3:end].strip().splitlines():
        if not line.strip() or line.lstrip().startswith("#") or ":" not in line:
            continue
        key, value = line.split(":", 1)
        value = value.strip()
        if value.startswith("["):
            try:
                value = json.loads(value)
            except json.JSONDecodeError:
                value = [v.strip(" '\"") for v in value.strip("[]").split(",") if v.strip()]
        elif len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        meta[key.strip()] = value
    return meta, text[end + 4:].lstrip("\n")


def load_meta(folder: Path) -> dict[str, str]:
    """_meta.json maps file/folder names to nav titles. Values are a string or {"name": ...}."""
    path = folder / "_meta.json"
    if not path.exists():
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    return {k: (v.get("name") if isinstance(v, dict) else v) or k for k, v in raw.items()}


def collect(section: str, errors: list[str]) -> tuple[list[Page], list]:
    """Read one section in _meta.json order. Returns the pages and a MkDocs nav list."""
    pages, nav = [], []
    root = WIKI / section
    if not root.exists():
        return pages, nav

    def walk(folder: Path, prefix: str, nav_out: list):
        meta = load_meta(folder)
        entries = sorted(p for p in folder.iterdir() if p.suffix == ".mdx" or (p.is_dir() and not p.name.startswith(".")))
        listed = [name for name in meta if (folder / name).exists()]
        for name in meta:
            if not (folder / name).exists():
                errors.append(f"{rel(folder / '_meta.json')}: lists '{name}', but there is no such file or folder")
        for p in entries:
            if p.name not in meta:
                errors.append(f"{rel(p)}: not listed in {rel(folder / '_meta.json')} (Oracle Index hides unlisted pages)")
                listed.append(p.name)
        for name in listed:
            p = folder / name
            title = meta.get(name)
            if p.is_dir():
                children: list = []
                walk(p, f"{prefix}{name}/", children)
                if children:
                    nav_out.append({title or name.replace("_", " ").title(): children})
            else:
                page = Page(p, section, prefix + p.stem)
                page.meta, page.body = parse_frontmatter(p.read_text(encoding="utf-8"))
                if not page.meta.get("title"):
                    errors.append(f"{rel(p)}: frontmatter needs a title")
                if section == "content" and not page.meta.get("id"):
                    errors.append(f"{rel(p)}: content pages need an `id` (the item id, e.g. oritech_extensions:guide)")
                for key in ("related_items",):
                    if key in page.meta and not isinstance(page.meta[key], list):
                        errors.append(f"{rel(p)}: `{key}` must be a list like [\"oritech:item_pipe\"]")
                pages.append(page)
                nav_out.append({title or page.title: page.out})

    walk(root, "", nav)
    return pages, nav


def rel(p: Path) -> str:
    return str(p.relative_to(ROOT))


# ---------------------------------------------------------------- converting

def item_name(item_id: str, lang: dict) -> str:
    ns, _, path = item_id.partition(":")
    for kind in ("item", "block"):
        key = f"{kind}.{ns}.{path}"
        if key in lang:
            return lang[key]
    return path.replace("_", " ").title()


def attrs(tag_body: str) -> dict:
    """Attributes of an MDX tag: key="v", key='v', key={expr}, bare flags."""
    out = {}
    for m in re.finditer(r'(\w+)(?:=(?:"([^"]*)"|\'([^\']*)\'|\{([^}]*)\}))?', tag_body):
        key, dq, sq, expr = m.groups()
        out[key] = dq if dq is not None else sq if sq is not None else expr if expr is not None else True
    return out


class Recorder:
    """Stands in for Icons with --list-items: notes every item a page wants an icon for."""
    def __init__(self):
        self.seen: set[str] = set()

    def get(self, item_id: str):
        if ":" in item_id:
            self.seen.add(item_id)
        return None


def load_recipes() -> dict[str, dict]:
    """Crafting recipes in this mod's data/, keyed by result id (used to check the docs match the game)."""
    out = {}
    for f in sorted((ROOT / "src/main/resources/data").glob("*/recipe/**/*.json")):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        kind = data.get("type", "")
        if kind not in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
            continue
        if kind.endswith("shapeless"):
            ingredients = data.get("ingredients", [])
        else:
            pattern, key = data.get("pattern", []), data.get("key", {})
            ingredients = [key[c] for row in pattern for c in row if c != " " and c in key]
        items = []
        for ing in ingredients:
            ing = ing[0] if isinstance(ing, list) and len(ing) == 1 else ing
            if not isinstance(ing, dict) or "item" not in ing:  # tags / alternatives: can't compare
                items = None
                break
            items.append(ing["item"])
        result = data.get("result", {})
        out[result.get("id", "")] = {"file": rel(f), "items": sorted(items) if items is not None else None,
                                     "count": result.get("count", 1), "shapeless": kind.endswith("shapeless")}
    return out


class Converter:
    def __init__(self, pages: list[Page], lang: dict, errors: list[str], icons=None, viewer=None, door=None):
        self.lang, self.errors, self.icons, self.viewer, self.door = lang, errors, icons, viewer, door
        self.used_icons: dict[str, object] = {}   # output path inside docs/ -> PIL image
        self.recipes = load_recipes()
        self.by_id = {p.meta["id"]: p for p in pages if p.meta.get("id")}
        self.by_ref = {p.meta["ref"]: p for p in pages if p.meta.get("ref")}
        self.by_slug = {p.rel: p for p in pages if p.section == "docs"}
        self.pages = pages

    def link_to(self, page: Page, target: Page) -> str:
        here = Path(page.out).parent
        return Path(*([".."] * len(here.parts)), target.out).as_posix() if here.parts else target.out

    def asset(self, page: Page, location: str, ext: str = ".png") -> str:
        ns, _, path = location.lstrip("@").partition(":")
        if not Path(path).suffix:
            path += ext
        if not (WIKI / "assets" / ns / path).exists():
            self.errors.append(f"{rel(page.src)}: image/sound '{location}' not found at wiki/assets/{ns}/{path}")
        depth = len(Path(page.out).parent.parts)
        return "../" * depth + f"assets/{ns}/{path}"

    def convert(self, page: Page) -> str:
        text = page.body
        fences: list[str] = []

        def stash(m):  # keep code blocks untouched
            fences.append(m.group(0))
            return f"\x00{len(fences) - 1}\x00"

        text = self.code_tabs(text)
        text = re.sub(r"^(```|~~~).*?^\1\s*$", stash, text, flags=re.S | re.M)

        text = self.callouts(text)
        text = self.alerts(text)
        text = re.sub(r"<CraftingRecipe\b(.*?)/>", lambda m: self.recipe(page, attrs(m.group(1))), text, flags=re.S)
        text = re.sub(r"<(?:Mod)?Asset\b(.*?)/>", lambda m: self.asset_tag(page, attrs(m.group(1))), text, flags=re.S)
        text = re.sub(r"<VideoEmbed\b(.*?)/>", lambda m: self.video(attrs(m.group(1))), text, flags=re.S)
        text = re.sub(r"<Audio\b(.*?)/>", lambda m: f"[Sound: {attrs(m.group(1)).get('src', '')}]"
                      f"({self.asset(page, attrs(m.group(1)).get('src', ''), '.ogg')})", text, flags=re.S)
        text = re.sub(r'<img\s+src="(@?[\w.-]+:[^"]+)"',
                      lambda m: f'<img src="{self.html_root(page)}{self.asset(page, m.group(1)).lstrip("./")}"', text)
        text = re.sub(r"\?\[([^\]]+)\]\(([^)]*)\)", r'<abbr title="\2">\1</abbr>', text)
        text = re.sub(r"(!?)\[([^\]]*)\]\(([^)\s]+)\)(\{[^}]*\})?", lambda m: self.link(page, *m.groups()), text)

        text = re.sub(r"\x00(\d+)\x00", lambda m: fences[int(m.group(1))], text)
        head_icon = self.icon(page, page.meta.get("icon", ""), "mc-inline mc-title-icon")
        if head_icon:
            text = re.sub(r"^# ", "# " + head_icon, text, count=1, flags=re.M)
        related = self.related(page) + self.paint_viewer(page) + self.door_viewer(page)
        if related:  # just below the page's H1, or at the top if it has none
            h1 = re.search(r"^# .*\n", text, flags=re.M)
            at = h1.end() if h1 else 0
            text = text[:at] + "\n" + related + text[at:]
        return f"---\ntitle: {json.dumps(page.title)}\n---\n\n" + text.rstrip() + "\n"

    @staticmethod
    def html_root(page: Page) -> str:
        """Prefix from a page's URL back to the site root, for raw HTML (MkDocs only rewrites Markdown links).
        Pages are served as folders (content/guide.md -> /content/guide/), so it's one level deeper than the file."""
        return "" if page.out == "index.md" else "../" * len(Path(page.out).with_suffix("").parts)

    def icon(self, page: Page, item_id: str, cls: str = "mc-icon") -> str:
        """<img> for an item's icon, or "" when there isn't one."""
        found = self.icons.get(item_id) if self.icons and item_id else None
        if not found:
            return ""
        kind, img = found
        ns, _, path = item_id.partition(":")
        out = f"assets/items/{ns}/{path}.png"
        self.used_icons[out] = img
        src = self.html_root(page) + out
        name = item_name(item_id, self.lang)
        return f'<img class="{cls} {kind}" src="{src}" alt="{name}" title="{name}">'

    def paint_viewer(self, page: Page) -> str:
        """3D viewer for the blocks in `paint_viewer:` frontmatter (website only; Oracle Index ignores the key)."""
        ids = page.meta.get("paint_viewer")
        if not ids:
            return ""
        if not isinstance(ids, list):
            self.errors.append(f"{rel(page.src)}: `paint_viewer` must be a list like [\"oritech:item_pipe\"]")
            return ""
        for item_id in ids:
            self.icon(page, item_id)  # copies the icon (and lists it for --list-items)
        if not self.viewer:
            return ""
        blocks = []
        for item_id in ids:
            block = self.viewer.block(item_id, self.errors, rel(page.src))
            if block:
                ns, _, path = item_id.partition(":")
                if f"assets/items/{ns}/{path}.png" in self.used_icons:
                    block["icon"] = f"assets/items/{ns}/{path}.png"
                blocks.append(block)
        if not blocks:
            return ""
        attr = lambda v: html.escape(json.dumps(v, separators=(",", ":")), quote=True)
        return (f'<div class="paint-viewer" data-root="{self.html_root(page)}" data-blocks="{attr(blocks)}" '
                f'data-swatches="{attr(self.viewer.swatches)}" data-names="{attr(self.viewer.paint_names)}"></div>\n\n')

    def door_viewer(self, page: Page) -> str:
        """Animated hangar door (`door_viewer: true` frontmatter). Website only."""
        if str(page.meta.get("door_viewer", "")).lower() != "true" or not (self.door and self.viewer):
            return ""
        paints = self.door.collect(self.errors, rel(page.src))
        if paints is None:
            return ""
        attr = lambda v: html.escape(json.dumps(v, separators=(",", ":")), quote=True)
        return (f'<div class="door-viewer" data-root="{self.html_root(page)}" '
                f'data-paints="{attr(paints)}" data-swatches="{attr(self.viewer.swatches)}" '
                f'data-names="{attr(self.viewer.paint_names)}"></div>\n\n')

    def related(self, page: Page) -> str:
        items = page.meta.get("related_items") or []
        if not items:
            return ""
        names = ", ".join(f"{self.icon(page, i, 'mc-inline')}{item_name(i, self.lang)} (`{i}`)" for i in items)
        return f'!!! abstract "Related items"\n    {names}\n\n'

    def link(self, page: Page, bang: str, label: str, target: str, attr_block: str | None) -> str:
        if bang:  # image
            if attr_block and "item" in attr_block and target.startswith("@"):
                return f"{self.icon(page, target[1:], 'mc-inline')}**{label or item_name(target[1:], self.lang)}**"
            src = self.asset(page, target) if re.match(r"@?[\w.-]+:[^/]", target) and "://" not in target else target
            if attr_block and "item" in attr_block:
                return f'![{label}]({src}){{ width="32" }}'
            width = re.search(r'width="?(\d+%?)', attr_block or "")
            return f"![{label}]({src})" + (f'{{ width="{width.group(1)}" }}' if width else "")
        if "://" in target or target.startswith(("mailto:", "#")):
            return f"[{label}]({target})"
        found = None
        if target.startswith("@"):
            found = self.by_id.get(target[1:])
            if not found:  # vanilla/Oritech item with no page here: show its name
                return f"**{label or item_name(target[1:], self.lang)}**"
        elif target.startswith("+"):
            found = self.by_ref.get(target[1:])
        elif target.startswith("$"):
            found = self.by_slug.get(target[1:])
        else:  # relative link between pages in the same section
            path, _, anchor = target.partition("#")
            resolved = (Path(page.section) / Path(page.rel).parent / path).as_posix()
            resolved = re.sub(r"[^/]+/\.\./", "", resolved).removesuffix(".mdx")
            section, _, slug = resolved.partition("/")
            found = next((p for p in self.pages if p.section == section and p.rel == slug), None)
            if found and anchor:
                return f"[{label or found.title}]({self.link_to(page, found)}#{anchor})"
        if not found:
            self.errors.append(f"{rel(page.src)}: link target '{target}' doesn't match any page")
            return label or target
        return f"[{label or found.title}]({self.link_to(page, found)})"

    def callouts(self, text: str) -> str:
        def repl(m):
            a = attrs(m.group(1))
            kind = ADMONITION.get(str(a.get("variant", "note")).lower(), "note")
            marker = "???" if a.get("collapsed") else "???+" if a.get("collapsible") else "!!!"
            title = f' "{a["title"]}"' if isinstance(a.get("title"), str) else ""
            body = "\n".join("    " + line.strip() if line.strip() else "" for line in m.group(2).strip("\n").splitlines())
            return f"{marker} {kind}{title}\n{body}\n"
        return re.sub(r"<Callout\b([^>]*)>(.*?)</Callout>", repl, text, flags=re.S)

    def alerts(self, text: str) -> str:
        def repl(m):
            kind, fold, title = m.group(1).lower(), m.group(2), m.group(3).strip()
            marker = {"+": "???+", "-": "???"}.get(fold, "!!!")
            body = "\n".join("    " + re.sub(r"^>\s?", "", line) for line in m.group(4).splitlines())
            return f'{marker} {ADMONITION.get(kind, "note")}' + (f' "{title}"' if title else "") + f"\n{body}\n"
        return re.sub(r"^> \[!(\w+)\]([+-]?)([^\n]*)\n((?:>[^\n]*\n?)*)", repl, text, flags=re.M)

    def code_tabs(self, text: str) -> str:
        def tab(m):
            blocks = re.findall(r"```(\w*) !!tabs ([^\n]+)\n(.*?)```", m.group(1), flags=re.S)
            return "\n".join(f'=== "{name.strip()}"\n\n    ```{lang}\n' +
                             "".join("    " + line + "\n" for line in code.rstrip("\n").splitlines()) + "    ```\n"
                             for lang, name, code in blocks)
        return re.sub(r"<CodeTabs>(.*?)</CodeTabs>", tab, text, flags=re.S)

    def recipe(self, page: Page, a: dict) -> str:
        """<CraftingRecipe> as a crafting-table panel: 3x3 grid, arrow, result, and a large result preview."""
        slots = re.findall(r"['\"]([^'\"]*)['\"]", str(a.get("slots", "")))
        result_id = str(a.get("result", ""))
        count = str(a.get("count", "1"))
        if len(slots) != 9:
            self.errors.append(f"{rel(page.src)}: <CraftingRecipe> needs exactly 9 slots, got {len(slots)}")
            return ""
        shapeless = self.check_recipe(page, slots, result_id, count)

        def slot(item_id: str, extra: str = "", badge: str = "") -> str:
            if not item_id:
                return f'<span class="mc-slot{extra}"></span>'
            name = item_name(item_id, self.lang)
            inner = self.icon(page, item_id) or f'<span class="mc-label">{name}</span>'
            return f'<span class="mc-slot{extra}" title="{name}">{inner}{badge}</span>'

        result_name = item_name(result_id, self.lang)
        badge = f'<span class="mc-count">{count}</span>' if count != "1" else ""
        table = self.icon(page, "minecraft:crafting_table", "mc-station")
        preview = self.icon(page, result_id, "mc-preview")
        tag = '<span class="mc-tag">Shapeless</span>' if shapeless else ""
        return (
            '<div class="mc-recipe"><div class="mc-panel">'
            f'<div class="mc-heading">{result_name}</div>'
            '<div class="mc-body">'
            f'<div class="mc-grid">{"".join(slot(s) for s in slots)}</div>'
            f'<div class="mc-mid">{table}<span class="mc-arrow"></span>{tag}</div>'
            f'{slot(result_id, " mc-result", badge)}'
            f'{preview}'
            '</div></div></div>\n'
        )

    def check_recipe(self, page: Page, slots: list[str], result_id: str, count: str) -> bool:
        """Compare a documented recipe with this mod's recipe JSON for the same result. Returns shapeless."""
        actual = self.recipes.get(result_id)
        if actual is None:  # another mod's recipe; nothing to compare against
            return False
        documented = sorted(s for s in slots if s)
        if actual["items"] is not None and documented != actual["items"]:
            self.errors.append(f"{rel(page.src)}: recipe for {result_id} shows {documented}, but "
                               f"{actual['file']} uses {actual['items']}")
        if count != str(actual["count"]):
            self.errors.append(f"{rel(page.src)}: recipe for {result_id} says count {count}, but "
                               f"{actual['file']} makes {actual['count']}")
        return actual["shapeless"]

    def asset_tag(self, page: Page, a: dict) -> str:
        loc = a.get("location") or a.get("src") or ""
        width = f'{{ width="{a["width"]}" }}' if isinstance(a.get("width"), str) else ""
        return f"![]({self.asset(page, loc)}){width}"

    def video(self, a: dict) -> str:
        vid = a.get("id", "")
        return (f'<div class="video"><iframe src="https://www.youtube-nocookie.com/embed/{vid}" '
                f'title="Video" allowfullscreen></iframe></div>')


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="only validate wiki/")
    ap.add_argument("--list-items", action="store_true",
                    help="write build/wiki-icons/items.txt (items the pages show) for ./gradlew runClientIconExport")
    args = ap.parse_args()

    errors: list[str] = []
    lang = json.loads(LANG.read_text(encoding="utf-8")) if LANG.exists() else {}
    pages, nav = [], []
    for section, label in SECTIONS.items():
        sec_pages, sec_nav = collect(section, errors)
        pages += sec_pages
        if sec_nav:
            nav.append({label: sec_nav})
    if not any(p.out == "index.md" for p in pages):
        errors.append("wiki/docs/intro.mdx is missing (it's the home page in-game and on the website)")

    icons = None
    if args.list_items:
        icons = Recorder()
    elif not args.check:
        try:
            from icons import Icons
            icons = Icons()
            if not icons.src.has_jars:
                print("note: no game jars in build/wiki-cache (run tools/wiki/fetch_assets.py); "
                      "vanilla/Oritech items will show names instead of icons")
        except ImportError:
            print("note: Pillow isn't installed, so recipes show item names instead of icons")
    viewer = door = None
    if icons is not None and not args.list_items and any(p.meta.get("paint_viewer") or p.meta.get("door_viewer")
                                                          for p in pages):
        if THREE_JS.exists():
            from viewer import Viewer
            from door import Door
            viewer = Viewer(lang)
            door = Door(viewer.variants)
        else:
            print("note: three.js isn't installed (npm install --prefix build/npm three@0.170.0), "
                  "so pages are built without the 3D paint viewer")
    conv = Converter(pages, lang, errors, icons, viewer, door)
    rendered = {p.out: conv.convert(p) for p in pages}

    if args.list_items:
        out = ROOT / "build" / "wiki-icons" / "items.txt"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text("\n".join(sorted(icons.seen)) + "\n", encoding="utf-8")
        print(f"Wrote {len(icons.seen)} item ids to {rel(out)}")
        return 0
    if errors:
        print("wiki/ has problems:\n  " + "\n  ".join(errors), file=sys.stderr)
        return 1
    print(f"wiki/ OK: {len(pages)} pages")
    if args.check:
        return 0

    if OUT.exists():
        shutil.rmtree(OUT)
    docs = OUT / "docs"
    for out, text in rendered.items():
        (docs / out).parent.mkdir(parents=True, exist_ok=True)
        (docs / out).write_text(text, encoding="utf-8")
    if (WIKI / "assets").exists():
        shutil.copytree(WIKI / "assets", docs / "assets", ignore=shutil.ignore_patterns("items"))
    if door and door.files:
        door.write(docs)
    if viewer:
        viewer.write(docs)
        (docs / "javascripts/vendor").mkdir(parents=True, exist_ok=True)
        shutil.copy(THREE_JS, docs / "javascripts/vendor/three.module.min.js")
    for out, img in conv.used_icons.items():
        (docs / out).parent.mkdir(parents=True, exist_ok=True)
        img.save(docs / out)
    if icons:
        print(f"icons: {len(conv.used_icons)} drawn" + (f"; no icon for {', '.join(sorted(icons.missing))} "
              f"(add wiki/assets/items/<ns>/<item>.png to supply one)" if icons.missing else ""))
    extra = Path(__file__).with_name("site")
    if extra.exists():
        shutil.copytree(extra, docs, dirs_exist_ok=True)

    config = BASE_CONFIG.read_text(encoding="utf-8")
    # Cache-buster: GitHub Pages lets browsers keep CSS/JS for 10 minutes, so after a deploy a visitor could get
    # new pages with an old stylesheet. A hash of the site's own CSS/JS in their URLs forces fresh copies.
    digest = hashlib.sha256()
    for f in sorted(extra.rglob("*")) if extra.exists() else []:
        if f.is_file():
            digest.update(f.read_bytes())
    version = digest.hexdigest()[:10]
    config = re.sub(r"(stylesheets/oritech\.css|javascripts/paint-viewer\.js)", rf"\1?v={version}", config)
    config += "\nnav:\n" + "".join(nav_yaml(nav, 1))
    (OUT / "mkdocs.yml").write_text(config, encoding="utf-8")
    print(f"Wrote {rel(OUT)}/ — now run: mkdocs build -f {rel(OUT)}/mkdocs.yml")
    return 0


def nav_yaml(nav: list, depth: int):
    pad = "  " * depth
    for entry in nav:
        for title, value in entry.items():
            if isinstance(value, list):
                yield f"{pad}- {json.dumps(title)}:\n"
                yield from nav_yaml(value, depth + 1)
            else:
                yield f"{pad}- {json.dumps(title)}: {value}\n"


if __name__ == "__main__":
    sys.exit(main())

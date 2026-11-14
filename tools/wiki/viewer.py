"""Data for the 3D paint viewer on the docs site (site/javascripts/paint-viewer.js).

For each block a page lists in its ``paint_viewer`` frontmatter, this writes:
  assets/viewer/models/<ns>/<path>.json   the block model, parents resolved, ready to draw
  assets/viewer/tex/<ns>/<tex>.png         each texture it uses, plus <tex>_paint_<colour>.png versions

Painted textures are made exactly like the game does it: the palette swaps listed in this mod's
assets/minecraft/atlases/blocks.json (paletted_permutations), applied to the original textures.
Models and textures come from common/src/main/resources and the jars in build/wiki-cache (fetch_assets.py).
"""
from __future__ import annotations

import io
import json
from pathlib import Path

from PIL import Image

from icons import ROOT, Sources, split

ATLAS = ROOT / "common/src/main/resources/assets/minecraft/atlases/blocks.json"
PALETTES = ROOT / "common/src/main/resources/assets/oritech_extensions/textures"
FACES = ("north", "south", "east", "west", "up", "down")
SWATCH_SHADE = (195, 108, 43)  # a mid orange from Oritech's machines; its painted colour is the swatch colour


def palette(ref: str) -> list[tuple]:
    ns, path = split(ref)
    return [p[:3] for p in Image.open(PALETTES / f"{path}.png").convert("RGBA").getdata()]


class Viewer:
    def __init__(self, lang: dict):
        self.src = Sources()
        self.lang = lang
        self.models: dict[str, dict] = {}      # out path -> json
        self.textures: dict[str, Image.Image] = {}
        self.groups = []                       # (set of texture ids, key, {variant: palette})
        self.variants: list[str] = []
        atlas = json.loads(ATLAS.read_text(encoding="utf-8"))
        for source in atlas["sources"]:
            if source.get("type") != "minecraft:paletted_permutations":
                continue
            key = palette(source["palette_key"])
            perms = {name.removeprefix("paint_"): palette(p) for name, p in source["permutations"].items()}
            self.groups.append((set(source["textures"]), key, perms))
            for v in perms:
                if v not in self.variants:
                    self.variants.append(v)
        main_key, main_perms = self.groups[0][1], self.groups[0][2]
        i = main_key.index(SWATCH_SHADE)
        self.swatches = {"orange": "#%02x%02x%02x" % SWATCH_SHADE}
        self.swatches.update({v: "#%02x%02x%02x" % pal[i] for v, pal in main_perms.items()})
        oritech_lang = self.src.read("assets/oritech/lang/en_us.json")
        if oritech_lang:
            self.lang = {**json.loads(oritech_lang), **lang}
        # Paint names as Oritech shows them (e.g. the "diamond" paint is called "Adamant" in game).
        self.paint_names = {"orange": "Unpainted"}
        for v in self.variants:
            item = self.lang.get(f"item.oritech.{v}_paint", "")
            self.paint_names[v] = item.removesuffix(" Machine Paint").strip() or v.title()

    # ------------------------------------------------------------------ models

    def read_model(self, ref: str) -> dict | None:
        ns, path = split(ref)
        raw = self.src.read(f"assets/{ns}/models/{path}.json")
        return json.loads(raw) if raw else None

    def resolve(self, ref: str) -> dict | None:
        """Model with parents merged: nearest `elements`, child textures win, #refs resolved."""
        textures, elements, seen = {}, None, set()
        while ref and ref not in seen:
            seen.add(ref)
            data = self.read_model(ref)
            if data is None:
                break
            for k, v in (data.get("textures") or {}).items():
                textures.setdefault(k, v)
            if elements is None and data.get("elements"):
                elements = data["elements"]
            ref = data.get("parent")
        if not elements:
            return None

        def tex(v, hops=0):
            while isinstance(v, str) and v.startswith("#") and hops < 10:
                v, hops = textures.get(v[1:]), hops + 1
            return v if isinstance(v, str) and not v.startswith("#") else None

        out_elements = []
        for el in elements:
            faces = {}
            for name, face in (el.get("faces") or {}).items():
                t = tex(face.get("texture"))
                if name in FACES and t:
                    ns, path = split(t)
                    faces[name] = {k: face[k] for k in ("uv", "rotation") if k in face} | {"texture": f"{ns}:{path}"}
            if faces:
                out_elements.append({k: el[k] for k in ("from", "to", "rotation", "shade") if k in el} | {"faces": faces})
        return {"elements": out_elements}

    def pick_model(self, item_id: str) -> tuple[str, int, int] | None:
        """Which model shows the block best: a pipe's straight piece, an addon's 'on' model, else its item model."""
        ns, path = split(item_id)
        for candidate, x in ((f"{ns}:block/{path}_straight", 0), (f"{ns}:block/{path}_on", 0), (f"{ns}:block/{path}", 0)):
            if self.read_model(candidate):
                return candidate, x, 0
        item = self.read_model(f"{ns}:item/{path}")
        return (item["parent"], 0, 0) if item and item.get("parent") else None

    # ------------------------------------------------------------------ textures

    def texture(self, tex_id: str) -> list[str]:
        """Write a texture and its painted versions. Returns the paint variants it has."""
        ns, path = split(tex_id)
        raw = self.src.read(f"assets/{ns}/textures/{path}.png")
        if raw is None:
            return []
        img = Image.open(io.BytesIO(raw)).convert("RGBA")
        if img.height > img.width:
            img = img.crop((0, 0, img.width, img.width))
        self.textures[f"{ns}/{path}.png"] = img
        for textures, key, perms in self.groups:
            if f"{ns}:{path}" not in textures:
                continue
            for variant, pal in perms.items():
                swap = dict(zip(key, pal))
                painted = img.copy()
                painted.putdata([swap.get(p[:3], p[:3]) + (p[3],) if p[3] else p for p in img.getdata()])
                self.textures[f"{ns}/{path}_paint_{variant}.png"] = painted
            return list(perms)
        return []

    # ------------------------------------------------------------------ public

    def block(self, item_id: str, errors: list[str], where: str) -> dict | None:
        picked = self.pick_model(item_id)
        model = self.resolve(picked[0]) if picked else None
        if not model:
            errors.append(f"{where}: paint_viewer can't draw '{item_id}' (no block model with elements found)")
            return None
        paintable, painted_textures = set(), set()
        for el in model["elements"]:
            for face in el["faces"].values():
                variants = self.texture(face["texture"])
                if variants:
                    painted_textures.add(face["texture"])
                paintable.update(variants)
        model["painted"] = sorted(painted_textures)
        ns, path = split(item_id)
        model["x"], model["y"] = picked[1], picked[2]
        self.models[f"models/{ns}/{path}.json"] = model
        name = self.lang.get(f"block.{ns}.{path}") or self.lang.get(f"item.{ns}.{path}") or path.replace("_", " ").title()
        return {"id": item_id, "name": name, "model": f"assets/viewer/models/{ns}/{path}.json",
                "paints": [v for v in self.variants if v in paintable]}

    def write(self, docs: Path) -> None:
        out = docs / "assets" / "viewer"
        for rel, model in self.models.items():
            (out / rel).parent.mkdir(parents=True, exist_ok=True)
            (out / rel).write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
        for rel, img in self.textures.items():
            (out / "tex" / rel).parent.mkdir(parents=True, exist_ok=True)
            img.save(out / "tex" / rel)

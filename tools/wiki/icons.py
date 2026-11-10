"""Item icons for the docs site, built from the mod's own resources and the game jars in build/wiki-cache/.

Lookup order for an item id like ``oritech:nickel_ingot``:
  1. ``wiki/assets/items/<ns>/<path>.png``: a hand-made icon, if you ever want to replace one.
  2. ``build/wiki-icons/<ns>/<path>.png``: the item rendered by the real game client, exactly like the
     inventory icon (CI runs ``./gradlew runClientIconExport``). Covers GeckoLib and other custom models.
  3. The item model ``assets/<ns>/models/item/<path>.json``, followed through its parents:
     * flat items (``item/generated``, ``item/handheld``): their ``layer0``/``layer1`` textures;
     * full blocks (anything built on ``block/cube``): an isometric cube like the inventory icon.
  Anything else (custom/GeckoLib models) has no icon, and recipes show its name instead.

Sources searched for models and textures: src/main/resources, then every jar in build/wiki-cache
(Oritech, vanilla). Run tools/wiki/fetch_assets.py to download those.
"""
from __future__ import annotations

import io
import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OVERRIDES = ROOT / "wiki" / "assets" / "items"
RENDERED = ROOT / "build" / "wiki-icons"   # real inventory renders from `./gradlew runClientIconExport`
CACHE = ROOT / "build" / "wiki-cache"
FLAT_PARENTS = {"item/generated", "item/handheld", "item/handheld_rod", "builtin/generated"}
CUBE_SIZE = 128  # px; rendered big and shown scaled down with crisp pixels


class Sources:
    def __init__(self):
        self.dirs = [ROOT / "src/main/resources"]
        self.jars = []
        if CACHE.exists():
            # Mods before vanilla, so a mod's resources win over vanilla ones with the same path.
            for jar in sorted(CACHE.glob("*.jar"), key=lambda p: p.name.startswith("minecraft-")):
                self.jars.append(zipfile.ZipFile(jar))

    def read(self, path: str) -> bytes | None:
        for d in self.dirs:
            f = d / path
            if f.is_file():
                return f.read_bytes()
        for z in self.jars:
            try:
                return z.read(path)
            except KeyError:
                continue
        return None

    @property
    def has_jars(self) -> bool:
        return bool(self.jars)


def split(ref: str, default_ns: str = "minecraft") -> tuple[str, str]:
    ns, _, path = ref.partition(":") if ":" in ref else (default_ns, "", ref)
    return ns, path


class Icons:
    def __init__(self):
        self.src = Sources()
        self.cache: dict[str, tuple[str, Image.Image] | None] = {}
        self.missing: set[str] = set()

    def get(self, item_id: str) -> tuple[str, Image.Image] | None:
        """(kind, image) for an item id, kind is 'flat' or 'block'; None when there's no usable icon."""
        if item_id not in self.cache:
            try:
                self.cache[item_id] = self._resolve(item_id)
            except (OSError, ValueError, json.JSONDecodeError):
                self.cache[item_id] = None
            if self.cache[item_id] is None:
                self.missing.add(item_id)
        return self.cache[item_id]

    def _resolve(self, item_id: str):
        ns, path = split(item_id)
        override = OVERRIDES / ns / f"{path}.png"
        if override.exists():
            img = Image.open(override).convert("RGBA")
            return ("block" if img.width > 32 else "flat"), img
        rendered = RENDERED / ns / f"{path}.png"
        if rendered.exists():
            img = Image.open(rendered).convert("RGBA")
            if img.getbbox():  # skip blank renders
                return "block", img

        model = self._model(f"{ns}:item/{path}")
        if model is None:
            return None
        chain, textures = model
        if "layer0" in textures or FLAT_PARENTS & set(chain):
            layers = [self._texture(textures[k]) for k in ("layer0", "layer1", "layer2") if k in textures]
            layers = [l for l in layers if l is not None]
            if not layers:
                return None
            img = layers[0].copy()
            for layer in layers[1:]:
                img.alpha_composite(layer.resize(img.size, Image.NEAREST))
            return "flat", img
        if "block/cube" in chain:
            faces = {k: self._texture(textures.get(k, "")) for k in ("up", "north", "east")}
            if all(faces.values()):
                return "block", isometric(faces["up"], faces["north"], faces["east"])
        return None

    def _model(self, ref: str):
        """Follow a model's parents. Returns (normalised parent chain, merged + resolved textures)."""
        chain, textures, seen = [], {}, set()
        while ref and ref not in seen:
            seen.add(ref)
            ns, path = split(ref)
            chain.append(path)
            raw = self.src.read(f"assets/{ns}/models/{path}.json")
            if raw is None:
                if path in FLAT_PARENTS or path.startswith("builtin/"):
                    break
                return None if len(chain) == 1 else (chain, self._resolve_refs(textures))
            data = json.loads(raw)
            for k, v in (data.get("textures") or {}).items():
                textures.setdefault(k, v)  # child values win
            ref = data.get("parent")
        # block/cube itself maps each face to #down/#up/#north/... which cube_all etc. fill in.
        return chain, self._resolve_refs(textures)

    @staticmethod
    def _resolve_refs(textures: dict) -> dict:
        out = {}
        for k in textures:
            v, hops = textures[k], 0
            while isinstance(v, str) and v.startswith("#") and hops < 10:
                v, hops = textures.get(v[1:], ""), hops + 1
            if isinstance(v, str) and v and not v.startswith("#"):
                out[k] = v
        # Cube-family models name faces differently; fill the three faces we draw.
        side = out.get("side") or out.get("all") or out.get("particle")
        out.setdefault("up", out.get("top") or out.get("end") or out.get("all") or side)
        out.setdefault("north", out.get("front") or side)
        out.setdefault("east", side)
        return {k: v for k, v in out.items() if v}

    def _texture(self, ref: str) -> Image.Image | None:
        if not ref:
            return None
        ns, path = split(ref)
        raw = self.src.read(f"assets/{ns}/textures/{path}.png")
        if raw is None:
            return None
        img = Image.open(io.BytesIO(raw)).convert("RGBA")
        if img.height > img.width:  # animated strip: first frame
            img = img.crop((0, 0, img.width, img.width))
        return img


def isometric(top: Image.Image, left: Image.Image, right: Image.Image, n: int = CUBE_SIZE) -> Image.Image:
    """Draw a cube like Minecraft's inventory block icon: top, left (front) and right faces, shaded."""
    canvas = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    h = n / 2
    q = n / 4
    faces = [  # texture, origin, u vector, v vector, brightness
        (top, (0, q), (h, q), (h, -q), 1.0),
        (left, (0, q), (h, q), (0, h), 0.82),
        (right, (h, h), (h, -q), (0, h), 0.62),
    ]
    for tex, (ox, oy), (ax, ay), (bx, by), light in faces:
        t = tex.resize((n, n), Image.NEAREST)
        if light < 1:
            r, g, b, a = t.split()
            r, g, b = (c.point(lambda x, l=light: int(x * l)) for c in (r, g, b))
            t = Image.merge("RGBA", (r, g, b, a))
        # screen = o + (u/n)*A + (v/n)*B ; PIL wants screen -> texture, so invert the 2x2 matrix.
        a11, a12, a21, a22 = ax / n, bx / n, ay / n, by / n
        det = a11 * a22 - a12 * a21
        i11, i12, i21, i22 = a22 / det, -a12 / det, -a21 / det, a11 / det
        coeffs = (i11, i12, -(i11 * ox + i12 * oy), i21, i22, -(i21 * ox + i22 * oy))
        layer = t.transform((n, n), Image.AFFINE, coeffs, resample=Image.NEAREST)
        # Clip to the face's parallelogram (the transform repeats edge pixels outside it).
        mask = Image.new("L", (n, n), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).polygon([(ox, oy), (ox + ax, oy + ay), (ox + ax + bx, oy + ay + by), (ox + bx, oy + by)], fill=255)
        alpha = Image.composite(layer.getchannel("A"), Image.new("L", (n, n), 0), mask)
        layer.putalpha(alpha)
        canvas.alpha_composite(layer)
    return canvas

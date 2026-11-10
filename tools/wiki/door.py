"""Data for the animated hangar door on the docs site (site/javascripts/door-viewer-core.js).

Copies, from the Oritech jar (build/wiki-cache) and this mod's resources, into assets/viewer/door/:
  hangar_door.geo.json, hangar_door.animation.json   Oritech's GeckoLib model and its open/close animations
  extension.geo.json                                  this mod's extension segment model
  hangar_door.png, hangar_door_<paint>.png           Oritech's door texture and its painted versions
The page then plays Oritech's own "retract" (open) / "deploy" (close) animations, with the extensions moving
the way HangarDoorExtensionRenderer moves them in game.
"""
from __future__ import annotations

from pathlib import Path

from icons import Sources

ORITECH = "assets/oritech"
FILES = {
    "hangar_door.geo.json": f"{ORITECH}/geo/block/models/hangar_door.geo.json",
    "hangar_door.animation.json": f"{ORITECH}/animations/block/models/hangar_door.animation.json",
    "extension.geo.json": "assets/oritech_extensions/geo/block/hangar_door_extension.geo.json",
    "hangar_door.png": f"{ORITECH}/textures/block/models/hangar_door.png",
}
PAINTED = f"{ORITECH}/textures/block/models/colored/hangar_door_{{}}.png"


class Door:
    def __init__(self, variants: list[str]):
        self.src = Sources()
        self.variants = variants
        self.files: dict[str, bytes] = {}

    def collect(self, errors: list[str], where: str) -> list[str] | None:
        """Gather the files; returns the paints the door has textures for, or None if it can't be shown."""
        for name, path in FILES.items():
            data = self.src.read(path)
            if data is None:
                errors.append(f"{where}: door_viewer needs {path} (run tools/wiki/fetch_assets.py)")
                return None
            self.files[name] = data
        paints = []
        for v in self.variants:
            data = self.src.read(PAINTED.format(v))
            if data is not None:
                self.files[f"hangar_door_{v}.png"] = data
                paints.append(v)
        return paints

    def write(self, docs: Path) -> None:
        out = docs / "assets" / "viewer" / "door"
        out.mkdir(parents=True, exist_ok=True)
        for name, data in self.files.items():
            (out / name).write_bytes(data)

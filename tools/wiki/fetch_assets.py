#!/usr/bin/env python3
"""Download the game jars the docs site takes item icons from, into build/wiki-cache/.

  * the vanilla Minecraft client jar (version from gradle.properties), from Mojang
  * the Oritech jar (oritech_runtime_version_id in gradle.properties), from Modrinth

build_site.py reads textures and models straight out of these jars. Nothing from them is committed to the
repo; the icons are only copied into the generated website. Without the jars the site still builds, and
recipes show item names instead of icons.

Usage: python3 tools/wiki/fetch_assets.py
"""
from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "build" / "wiki-cache"
UA = {"User-Agent": "oritech-extensions-docs (github.com/keellam4/oritech-extensions)"}


def props() -> dict[str, str]:
    out = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def get_json(url: str):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 0:
        print(f"cached  {dest.relative_to(ROOT)}")
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r, open(tmp, "wb") as f:
        while chunk := r.read(1 << 20):
            f.write(chunk)
    tmp.rename(dest)
    print(f"fetched {dest.relative_to(ROOT)}")


def main() -> int:
    p = props()
    mc = p["minecraft_version"]
    manifest = get_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    version = next(v for v in manifest["versions"] if v["id"] == mc)
    client_url = get_json(version["url"])["downloads"]["client"]["url"]
    download(client_url, CACHE / f"minecraft-{mc}-client.jar")

    oritech_id = p["oritech_runtime_version_id"]
    files = get_json(f"https://api.modrinth.com/v2/version/{oritech_id}")["files"]
    file = next((f for f in files if f.get("primary")), files[0])
    download(file["url"], CACHE / f"oritech-{oritech_id}.jar")
    return 0


if __name__ == "__main__":
    sys.exit(main())

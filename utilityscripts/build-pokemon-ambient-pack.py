#!/usr/bin/env python3
"""Build a Minecraft resource pack from user-supplied Cobblemon ambient OGGs."""

from __future__ import annotations

import argparse
import json
import re
import tempfile
import zipfile
from pathlib import Path


SPECIES_ID = re.compile(r"^[a-z0-9._-]+$")
RESOURCE_PACK_FORMAT = 97
RESOURCE_PACK_MINOR = 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Package your own .ogg files as a Cobblemon ambient-sound resource pack. "
            "Input filenames must be exact Cobblemon Showdown IDs, such as pikachu.ogg."
        )
    )
    parser.add_argument("input", type=Path, help="Folder containing your .ogg files")
    parser.add_argument(
        "output",
        type=Path,
        nargs="?",
        default=Path("pokemon-ambient-overrides.zip"),
        help="Output ZIP (default: ./pokemon-ambient-overrides.zip)",
    )
    parser.add_argument(
        "--description",
        default="Personal Pokémon ambient sound overrides",
        help="Description shown in Minecraft's resource pack menu",
    )
    parser.add_argument(
        "--pack-format",
        type=int,
        default=RESOURCE_PACK_FORMAT,
        help=f"Minecraft resource pack format (default: {RESOURCE_PACK_FORMAT})",
    )
    parser.add_argument(
        "--pack-format-minor",
        type=int,
        default=RESOURCE_PACK_MINOR,
        help=f"Minor resource pack format (default: {RESOURCE_PACK_MINOR})",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Replace the output ZIP if it already exists",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source_dir = args.input.expanduser().resolve()
    output = args.output.expanduser().resolve()

    if not source_dir.is_dir():
        raise SystemExit(f"Input folder does not exist: {source_dir}")
    if output == source_dir or source_dir in output.parents:
        raise SystemExit("Choose an output ZIP outside the input folder.")
    if output.exists() and not args.force:
        raise SystemExit(f"Output already exists: {output} (pass --force to replace it).")
    if args.pack_format < 1 or args.pack_format_minor < 0:
        raise SystemExit("Pack format must be positive and its minor version nonnegative.")

    clips: dict[str, Path] = {}
    for path in sorted(source_dir.iterdir(), key=lambda item: item.name.casefold()):
        if not path.is_file() or path.suffix.casefold() != ".ogg":
            continue
        species_id = path.stem
        if not SPECIES_ID.fullmatch(species_id):
            raise SystemExit(
                f"Invalid filename {path.name!r}. Use a lowercase Cobblemon Showdown ID, "
                "for example pikachu.ogg or ponytagalar.ogg."
            )
        if species_id in clips:
            raise SystemExit(f"Duplicate species ID: {species_id}")
        if path.stat().st_size < 4:
            raise SystemExit(f"{path.name} is not an Ogg file (missing OggS header).")
        with path.open("rb") as clip_file:
            if clip_file.read(4) != b"OggS":
                raise SystemExit(f"{path.name} is not an Ogg file (missing OggS header).")
        clips[species_id] = path

    if not clips:
        raise SystemExit(f"No .ogg files found in {source_dir}")

    sound_events = {
        f"pokemon.{species_id}.ambient": {
            "replace": True,
            "sounds": [{"name": f"cobblemon:custom_ambient/{species_id}", "stream": True}],
        }
        for species_id in clips
    }
    pack_meta = {
        "pack": {
            "pack_format": args.pack_format,
            "min_format": [args.pack_format, args.pack_format_minor],
            "max_format": [args.pack_format, args.pack_format_minor],
            "description": args.description,
        }
    }
    readme = (
        "User-supplied ambient sound overrides for Cobblemon.\n"
        "This pack contains only the clips you supplied. It replaces the matching "
        "pokemon.<showdown-id>.ambient sound events; battle cries are unchanged.\n"
        "Every client that should hear these clips must enable this pack, unless a "
        "server distributes it as its resource pack.\n"
    )

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        prefix=f"{output.stem}-", suffix=".zip", dir=output.parent, delete=False
    ) as temporary:
        temp_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temp_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("pack.mcmeta", json.dumps(pack_meta, indent=2) + "\n")
            archive.writestr(
                "assets/cobblemon/sounds.json",
                json.dumps(sound_events, indent=2) + "\n",
            )
            archive.writestr("README.txt", readme)
            for species_id, path in clips.items():
                archive.write(
                    path,
                    f"assets/cobblemon/sounds/custom_ambient/{species_id}.ogg",
                )
        temp_path.replace(output)
    finally:
        temp_path.unlink(missing_ok=True)

    print(f"Created {output} with {len(clips)} ambient override(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

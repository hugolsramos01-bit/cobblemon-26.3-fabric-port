#!/usr/bin/env python3
"""Migrate only Cobblemon recipe ingredient fields; default is a read-only check."""

import argparse
import copy
import json
from pathlib import Path
import re
import unittest


FIELDS = {
    "cobblemon:brewing_stand": ("input", "bottle"),
    "cobblemon:cooking_pot": ("key",),
    "cobblemon:cooking_pot_shapeless": ("ingredients",),
}
IDENTIFIER = re.compile(r"[a-z0-9_.-]+:[a-z0-9/._-]+\Z")
UNIONS = {
    ("minecraft:apple", "#cobblemon:apples"): "cobblemon:recipe_ingredients/candied_apples",
    ("minecraft:water_bucket", "#c:drinks/milk"): "cobblemon:recipe_ingredients/tea_liquids",
}


def ingredient(value):
    """Convert legacy leaves; retain tag alternatives through dynamic union tags."""
    if isinstance(value, str):
        identifier = value[1:] if value.startswith("#") else value
        if not IDENTIFIER.fullmatch(identifier):
            raise ValueError(f"Invalid ingredient identifier: {value!r}")
        return value
    if isinstance(value, list):
        if not value:
            raise ValueError("Empty ingredient alternatives")
        alternatives = []
        for entry in value:
            converted = ingredient(entry)
            alternatives.extend(converted if isinstance(converted, list) else [converted])
        if any(entry.startswith("#") for entry in alternatives):
            union = UNIONS.get(tuple(alternatives))
            if union is None:
                raise ValueError(f"No declared dynamic union for alternatives: {alternatives!r}")
            return "#" + union
        return alternatives
    if isinstance(value, dict) and len(value) == 1:
        key = next(iter(value))
        if key in ("item", "tag") and isinstance(value[key], str):
            return ingredient(("#" if key == "tag" else "") + value[key])
    raise ValueError(f"Unsupported ingredient: {value!r}")


def migrate(recipe):
    """Leave every field outside the serializer's known ingredient slots intact."""
    recipe_type = recipe.get("type")
    if recipe_type not in FIELDS:
        return copy.deepcopy(recipe)
    result = copy.deepcopy(recipe)
    for field in FIELDS[recipe_type]:
        value = recipe[field]
        if field == "key":
            result[field] = {symbol: ingredient(entry) for symbol, entry in value.items()}
        elif field == "ingredients":
            result[field] = [ingredient(entry) for entry in value]
        else:
            result[field] = ingredient(value)
    return result


class MigrationChecks(unittest.TestCase):
    def test_alternatives_and_duplicates(self):
        self.assertEqual(
            migrate({"type": "cobblemon:cooking_pot_shapeless", "ingredients": [
                {"item": "minecraft:bowl"}, {"item": "minecraft:bowl"},
                [{"item": "minecraft:water_bucket"}, {"tag": "c:drinks/milk"}]]})["ingredients"],
            ["minecraft:bowl", "minecraft:bowl", "#cobblemon:recipe_ingredients/tea_liquids"],
        )

    def test_dynamic_unions_and_item_alternatives(self):
        for alternatives, tag in UNIONS.items():
            self.assertEqual(ingredient(list(alternatives)), "#" + tag)
            self.assertEqual(ingredient("#" + tag), "#" + tag)
        self.assertEqual(ingredient([{"item": "a:b"}, {"item": "a:c"}]), ["a:b", "a:c"])
        self.assertEqual(ingredient([["a:b"], ["a:c"]]), ["a:b", "a:c"])
        with self.assertRaises(ValueError):
            ingredient(["a:b", "#a:c"])

    def test_preserves_other_fields_and_is_idempotent(self):
        recipe = {"type": "cobblemon:cooking_pot", "key": {"A": {"tag": "c:fruit"}},
                  "pattern": [" A ", "AAA"], "result": {"item": "example:output", "count": 2},
                  "category": "foods", "seasoningTag": "example:seasoning", "seasoningProcessors": []}
        migrated = migrate(recipe)
        self.assertEqual(migrated["key"], {"A": "#c:fruit"})
        for field in recipe.keys() - {"key"}:
            self.assertEqual(recipe[field], migrated[field])
        self.assertEqual(migrate(migrated), migrated)
        self.assertEqual(recipe["key"]["A"], {"tag": "c:fruit"})

    def test_brewing_slots(self):
        self.assertEqual(migrate({"type": "cobblemon:brewing_stand", "input": {"item": "a:b"},
                                  "bottle": {"tag": "a:c"}, "result": {"id": "a:d"}}),
                         {"type": "cobblemon:brewing_stand", "input": "a:b", "bottle": "#a:c",
                          "result": {"id": "a:d"}})

    def test_other_serializers_are_untouched(self):
        for kind in ("minecraft:crafting_shapeless", "create:mixing", "farmersdelight:cooking"):
            recipe = {"type": kind, "ingredients": [{"item": "a:b"}], "result": {"item": "a:c"}}
            self.assertEqual(migrate(recipe), recipe)

    def test_rejects_ambiguous_or_malformed_input(self):
        for value in ({"item": "a:b", "tag": "a:c"}, {"item": "a:b", "count": 2},
                      {"item": None}, [], "INVALID", "#a:bad space"):
            with self.assertRaises(ValueError):
                ingredient(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1] /
                        "common/src/main/resources/data/cobblemon/recipe")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="Apply migrations to custom recipe JSON only")
    mode.add_argument("--check", action="store_true", help="Fail if any custom ingredient still needs migration")
    parser.add_argument("--self-test", action="store_true", help="Run focused fixtures before scanning")
    args = parser.parse_args()
    if args.self_test:
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(MigrationChecks)
        if not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful():
            return 1
    if not args.root.is_dir():
        parser.error(f"Recipe directory not found: {args.root}")
    counts = dict.fromkeys(FIELDS, 0)
    pending = 0
    pending_tags = 0
    data_root = args.root.parent.parent
    for alternatives, union in UNIONS.items():
        for entry in alternatives:
            if entry.startswith("#"):
                namespace, tag_path = entry[1:].split(":", 1)
                dependency = data_root / namespace / "tags/item" / (tag_path + ".json")
                if not dependency.is_file():
                    raise ValueError(f"Missing required union member tag: {dependency}")
                json.loads(dependency.read_text(encoding="utf-8"))
        namespace, tag_path = union.split(":", 1)
        path = data_root / namespace / "tags/item" / (tag_path + ".json")
        expected = {"replace": False, "values": list(alternatives)}
        if path.exists():
            if json.loads(path.read_text(encoding="utf-8")) != expected:
                raise ValueError(f"Unexpected contents in generated union tag: {path}")
        else:
            pending_tags += 1
            if args.write:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(expected, indent=2) + "\n", encoding="utf-8")
    for path in sorted(args.root.rglob("*.json")):
        original_bytes = path.read_bytes()
        text = original_bytes.decode("utf-8")
        recipe = json.loads(text)
        recipe_type = recipe.get("type")
        if recipe_type not in FIELDS:
            continue
        counts[recipe_type] += 1
        migrated = migrate(recipe)
        assert migrate(migrated) == migrated, f"Not idempotent: {path}"
        for field in recipe.keys() - set(FIELDS[recipe_type]):
            assert recipe[field] == migrated[field], f"Unexpected field change: {path}: {field}"
        if recipe != migrated:
            pending += 1
            if args.write:
                indentation = re.search(r"\n( +)\"", text)
                indent = len(indentation.group(1)) if indentation else 2
                encoded = json.dumps(migrated, indent=indent, ensure_ascii=False) + "\n"
                if b"\r\n" in original_bytes:
                    encoded = encoded.replace("\n", "\r\n")
                path.write_bytes(encoded.encode("utf-8"))
    print(json.dumps({"custom_recipes": counts, "total": sum(counts.values()),
                      "migrated" if args.write else "pending": pending,
                      "union_tags_created" if args.write else "union_tags_pending": pending_tags}))
    return 0 if args.write or pending + pending_tags == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())

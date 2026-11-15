#!/usr/bin/env python3
"""Migrate known vanilla recipe fields to 26.3; default is a read-only check."""

import argparse
import copy
import json
from pathlib import Path
import re
import unittest


FIELDS = {
    "minecraft:crafting_shaped": ("key",),
    "minecraft:crafting_shapeless": ("ingredients",),
    **{f"minecraft:{kind}": ("ingredient",) for kind in
       ("smelting", "blasting", "smoking", "campfire_cooking", "stonecutting")},
    "minecraft:smithing_transform": ("template", "base", "addition"),
    "minecraft:smithing_trim": ("template", "base", "addition"),
}
UNIONS = {
    ("minecraft:apple", "#cobblemon:apples"): "#cobblemon:recipe_ingredients/candied_apples",
    ("#c:dusts/redstone", "cobblemon:electric_gem"):
        "#cobblemon:recipe_ingredients/cell_battery_conductor",
}
TRIM_PATTERNS = {
    "cobblemon:automaton_armor_trim_smithing_template": "cobblemon:automaton",
}
IDENTIFIER = re.compile(r"[a-z0-9_.-]+:[a-z0-9/._-]+\Z")


def ingredient(value):
    if isinstance(value, str):
        if not IDENTIFIER.fullmatch(value.removeprefix("#")):
            raise ValueError(f"Invalid ingredient: {value!r}")
        return value
    if isinstance(value, dict) and len(value) == 1:
        kind = next(iter(value))
        if kind in ("item", "tag") and isinstance(value[kind], str):
            return ingredient(("#" if kind == "tag" else "") + value[kind])
    if isinstance(value, list) and value:
        alternatives = []
        for entry in value:
            converted = ingredient(entry)
            alternatives.extend(converted if isinstance(converted, list) else [converted])
        if len(alternatives) == 1:
            return alternatives[0]
        if any(entry.startswith("#") for entry in alternatives):
            if tuple(alternatives) not in UNIONS:
                raise ValueError(f"Undeclared dynamic ingredient union: {alternatives!r}")
            return UNIONS[tuple(alternatives)]
        return alternatives
    raise ValueError(f"Unsupported ingredient: {value!r}")


def migrate(recipe):
    kind = recipe.get("type")
    result = copy.deepcopy(recipe)
    if kind not in FIELDS:
        if isinstance(kind, str) and kind.startswith("minecraft:"):
            raise ValueError(f"Unreviewed vanilla serializer: {kind}")
        return result
    for field in FIELDS[kind]:
        if field not in recipe and kind == "minecraft:smithing_transform" and field != "base":
            continue  # Native template/addition ingredients are optional in 26.3.
        value = recipe[field]
        if field == "key":
            result[field] = {symbol: ingredient(entry) for symbol, entry in value.items()}
        elif field == "ingredients":
            result[field] = [ingredient(entry) for entry in value]
        else:
            result[field] = ingredient(value)
    if kind == "minecraft:smithing_trim" and "pattern" not in result:
        template = result["template"]
        if template not in TRIM_PATTERNS:
            raise ValueError(f"No reviewed trim pattern for template: {template}")
        result["pattern"] = TRIM_PATTERNS[template]
    if kind != "minecraft:smithing_trim":
        output = result["result"]
        if isinstance(output, str):
            result["result"] = {"id": output}
            if "count" in result:
                result["result"]["count"] = result.pop("count")
        elif "count" in result:
            if kind != "minecraft:stonecutting" or "count" in output:
                raise ValueError("Ambiguous recipe with both structured result and top-level count")
            output["count"] = result.pop("count")
    return result


class MigrationChecks(unittest.TestCase):
    def test_slots_pattern_outputs_unchanged(self):
        source = {"type": "minecraft:crafting_shaped", "key": {"a": {"item": "a:b"}},
                  "pattern": [" a ", "aaa"], "result": {"id": "a:c", "count": 4},
                  "group": "example", "category": "misc", "conditions": [{"item": "a:condition"}]}
        result = migrate(source)
        self.assertEqual(result["key"], {"a": "a:b"})
        for field in source.keys() - {"key"}:
            self.assertEqual(source[field], result[field])
        self.assertEqual(migrate(result), result)

    def test_shapeless_repeated_slots(self):
        source = {"type": "minecraft:crafting_shapeless", "ingredients":
                  [{"tag": "a:b"}, {"tag": "a:b"}, {"item": "a:c"}], "result": {"id": "a:d"}}
        self.assertEqual(migrate(source)["ingredients"], ["#a:b", "#a:b", "a:c"])

    def test_unions_and_singleton_tag(self):
        for values, tag in UNIONS.items():
            self.assertEqual(ingredient(list(values)), tag)
        self.assertEqual(ingredient([{"tag": "a:b"}]), "#a:b")
        self.assertEqual(ingredient([{ "item": "a:b"}, {"item": "a:c"}]), ["a:b", "a:c"])
        with self.assertRaises(ValueError):
            ingredient(["a:b", "#a:c"])

    def test_smithing_trim_requires_reviewed_pattern(self):
        source = {"type": "minecraft:smithing_trim", "template":
                  {"item": "cobblemon:automaton_armor_trim_smithing_template"},
                  "base": {"tag": "minecraft:trimmable_armor"},
                  "addition": {"tag": "minecraft:trim_materials"}}
        result = migrate(source)
        self.assertEqual(result["pattern"], "cobblemon:automaton")
        self.assertEqual(result["base"], "#minecraft:trimmable_armor")
        self.assertEqual(migrate(result), result)
        source["template"] = {"item": "a:unknown"}
        with self.assertRaises(ValueError):
            migrate(source)

    def test_legacy_stonecutting_output_count(self):
        self.assertEqual(migrate({"type": "minecraft:stonecutting", "ingredient": {"item": "a:b"},
                                  "result": "a:c", "count": 3}),
                         {"type": "minecraft:stonecutting", "ingredient": "a:b",
                          "result": {"id": "a:c", "count": 3}})
        self.assertEqual(migrate({"type": "minecraft:stonecutting", "ingredient": {"item": "a:b"},
                                  "result": {"id": "a:c"}, "count": 1})["result"],
                         {"id": "a:c", "count": 1})

    def test_excluded_serializers(self):
        for kind in ("cobblemon:cooking_pot", "create:mixing", "farmersdelight:cutting"):
            source = {"type": kind, "ingredients": [{"item": "a:b"}], "result": {"item": "a:c"}}
            self.assertEqual(migrate(source), source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1] /
                        "common/src/main/resources/data")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        checks = unittest.defaultTestLoader.loadTestsFromTestCase(MigrationChecks)
        if not unittest.TextTestRunner(verbosity=2).run(checks).wasSuccessful():
            return 1
    if not args.root.is_dir():
        parser.error(f"Data root does not exist: {args.root}")
    pending, counts = [], dict.fromkeys(FIELDS, 0)
    for namespace in sorted(args.root.iterdir()):
        recipe_root = namespace / "recipe"
        if not recipe_root.is_dir():
            continue
        for path in sorted(recipe_root.rglob("*.json")):
            raw = path.read_bytes()
            recipe = json.loads(raw)
            if not recipe.get("type", "").startswith("minecraft:"):
                continue
            migrated = migrate(recipe)
            counts[recipe["type"]] += 1
            assert migrate(migrated) == migrated, f"Not idempotent: {path}"
            if recipe != migrated:
                pending.append((path, raw, migrated))
    # Validate all required union tags and trim pattern before any write.
    for values, tag in UNIONS.items():
        namespace, identifier = tag[1:].split(":", 1)
        path = args.root / namespace / "tags/item" / (identifier + ".json")
        if not path.is_file() or json.loads(path.read_text(encoding="utf-8")) != {
                "replace": False, "values": list(values)}:
            raise ValueError(f"Required reviewed union tag missing or changed: {path}")
    for pattern in TRIM_PATTERNS.values():
        namespace, identifier = pattern.split(":", 1)
        path = args.root / namespace / "trim_pattern" / (identifier + ".json")
        if not path.is_file():
            raise ValueError(f"Trim pattern does not exist: {path}")
    if args.write:
        for path, raw, migrated in pending:
            text = raw.decode("utf-8")
            match = re.search(r"\n( +)\"", text)
            encoded = json.dumps(migrated, indent=len(match.group(1)) if match else 2,
                                 ensure_ascii=False) + "\n"
            if b"\r\n" in raw:
                encoded = encoded.replace("\n", "\r\n")
            path.write_bytes(encoded.encode("utf-8"))
    print(json.dumps({"vanilla_recipes": counts, "total": sum(counts.values()),
                      "migrated" if args.write else "pending": len(pending)}))
    return 0 if args.write or not pending else 1


if __name__ == "__main__":
    raise SystemExit(main())

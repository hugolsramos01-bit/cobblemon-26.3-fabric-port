/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package com.cobblemon.mod.common.advancement.criterion

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition

/** Keeps the player field and legacy AND-list form while using native 26.3 player matching. */
object PlayerPredicateCodec {
    private val entityCodec: Codec<EntityPredicate> = object : Codec<EntityPredicate> {
        override fun <T> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<EntityPredicate, T>> =
            EntityPredicate.CODEC.decode(ops, normalizeEntity(ops, input))

        override fun <T> encode(value: EntityPredicate, ops: DynamicOps<T>, prefix: T): DataResult<T> =
            EntityPredicate.CODEC.encode(value, ops, prefix)
    }

    private val nativeOrLegacy: Codec<Holder<LootItemCondition>> = Codec.withAlternative(
        Codec.withAlternative(
            LootItemCondition.CODEC,
            LootItemCondition.CODEC.listOf(),
            { conditions -> Holder.direct(AllOfCondition.allOf(HolderSet.direct(conditions))) }
        ),
        entityCodec,
        { predicate -> EntityPredicate.wrap(predicate) }
    )

    val CODEC: Codec<Holder<LootItemCondition>> = object : Codec<Holder<LootItemCondition>> {
        override fun <T> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<Holder<LootItemCondition>, T>> =
            nativeOrLegacy.decode(ops, normalizeCondition(ops, input))

        override fun <T> encode(value: Holder<LootItemCondition>, ops: DynamicOps<T>, prefix: T): DataResult<T> =
            nativeOrLegacy.encode(value, ops, prefix)
    }

    private fun <T> normalizeEntity(ops: DynamicOps<T>, input: T): T {
        val map = ops.getMap(input).result().orElse(null) ?: return input
        if (map.get("type") == null || map.get("entity_type") != null) return input
        return ops.createMap(map.entries().map { entry ->
            if (ops.getStringValue(entry.first).result().orElse("") == "type")
                Pair.of(ops.createString("entity_type"), entry.second)
            else entry
        })
    }

    private fun <T> normalizeCondition(ops: DynamicOps<T>, input: T): T {
        val sequence = ops.getStream(input).result().orElse(null)
        if (sequence != null) return ops.createList(sequence.map { normalizeCondition(ops, it) })
        val map = ops.getMap(input).result().orElse(null) ?: return input
        // Rewrite loot condition discriminators only. Entity predicate/NBT payloads stay opaque.
        val legacyType = map.get("condition") ?: return input
        if (map.get("type") != null || ops.getStringValue(legacyType).result().isEmpty) return input
        return ops.createMap(map.entries().map { entry ->
            val name = ops.getStringValue(entry.first).result().orElse("")
            when (name) {
                "condition" -> Pair.of(ops.createString("type"), entry.second)
                "terms", "term" -> Pair.of(entry.first, normalizeCondition(ops, entry.second))
                "predicate" -> if (ops.getStringValue(legacyType).result().orElse("") in
                    setOf("minecraft:entity_properties", "entity_properties"))
                    Pair.of(entry.first, normalizeEntity(ops, entry.second)) else entry
                else -> entry
            }
        })
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This file is part of Cobblemon.
 */

package com.cobblemon.mod.common.advancement.predicate

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.predicates.entity.EntitySubPredicate
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

class CobblemonEntityTypePredicate(private val entityType: Identifier) : EntitySubPredicate {
    override fun matches(entity: Entity, serverLevel: ServerLevel, vec3: Vec3?): Boolean =
        entity.type == BuiltInRegistries.ENTITY_TYPE.get(entityType)

    companion object {
        val CODEC: Codec<CobblemonEntityTypePredicate> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("type").forGetter { it.entityType }
            ).apply(instance, ::CobblemonEntityTypePredicate)
        }
    }
}

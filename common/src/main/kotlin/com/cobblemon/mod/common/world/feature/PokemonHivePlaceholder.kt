/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.feature

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.component.TypedEntityData

/** Deferred worldgen data must survive TypedEntityData stripping the reserved "id" field. */
object PokemonHivePlaceholder {
    private const val MARKER_KEY = "cobblemon:hive_placeholder"
    private const val MARKER_VALUE = "cobblemon_pokemon_entity_placeholder"

    @JvmStatic
    fun create(type: EntityType<*>, properties: String): TypedEntityData<EntityType<*>> {
        val tag = CompoundTag()
        tag.putString(MARKER_KEY, MARKER_VALUE)
        tag.putString("pokemonProperties", properties)
        return TypedEntityData.of(type, tag)
    }

    @JvmStatic
    fun isPlaceholder(data: TypedEntityData<EntityType<*>>): Boolean =
        data.getUnsafe().getStringOr(MARKER_KEY, "") == MARKER_VALUE

    @JvmStatic
    fun properties(data: TypedEntityData<EntityType<*>>): String =
        data.getUnsafe().getStringOr("pokemonProperties", "")
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.CobblemonEntities
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.core.UUIDUtil
import java.util.UUID

fun CompoundTag.isPokemonEntity() : Boolean {
    return this.getString("id").orElse("").equals(CobblemonEntities.POKEMON_KEY.toString())
}

/** Reads UUIDs using Minecraft's current codec while accepting the historical int-array representation. */
fun CompoundTag.getUUID(key: String): UUID =
    get(key)?.let { UUIDUtil.CODEC.parse(NbtOps.INSTANCE, it).result().orElse(null) } ?: UUID(0L, 0L)

/** Writes UUIDs in the same NBT representation used by Minecraft's UUID codec. */
fun CompoundTag.putUUID(key: String, value: UUID) {
    UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, value).result().ifPresent { put(key, it) }
}

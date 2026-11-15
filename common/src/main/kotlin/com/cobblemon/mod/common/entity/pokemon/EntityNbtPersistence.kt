/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package com.cobblemon.mod.common.entity.pokemon

import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.MapCodec
import com.mojang.serialization.MapLike
import com.mojang.serialization.RecordBuilder
import net.minecraft.nbt.CompoundTag
import java.util.stream.Stream

/** Root map adapter for existing mutable-NBT entity events and custom serializers. */
internal object EntityNbtPersistence {
    val CODEC: MapCodec<CompoundTag> = object : MapCodec<CompoundTag>() {
        override fun <T> decode(ops: DynamicOps<T>, input: MapLike<T>): DataResult<CompoundTag> =
            CompoundTag.CODEC.parse(ops, ops.createMap(input.entries()))

        override fun <T> encode(input: CompoundTag, ops: DynamicOps<T>, prefix: RecordBuilder<T>): RecordBuilder<T> {
            val fields = CompoundTag.CODEC.encodeStart(ops, input).flatMap { ops.getMap(it) }
            var builder = prefix.withErrorsFrom(fields)
            fields.result().ifPresent { map ->
                map.entries().forEach { entry -> builder = builder.add(entry.first, entry.second) }
            }
            return builder
        }

        override fun <T> keys(ops: DynamicOps<T>): Stream<T> = Stream.empty()
    }
}

/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderException
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs

/** Preserves the VarInt length prefix of the former FriendlyByteBuf collection methods. */
fun <T> FriendlyByteBuf.writeCollection(collection: Collection<T>, writer: (FriendlyByteBuf, T) -> Unit) {
    writeVarInt(collection.size)
    collection.forEach { writer(this, it) }
}

fun <T, C : MutableCollection<T>> FriendlyByteBuf.readCollection(
    factory: (Int) -> C,
    reader: (FriendlyByteBuf) -> T
): C {
    val size = readVarInt()
    if (size < 0) {
        throw DecoderException("Negative packet collection size: $size")
    }
    // Follow the current codecs' allocation cap; the collection can grow as entries are decoded.
    val collection = factory(minOf(size, ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE))
    repeat(size) { collection.add(reader(this)) }
    return collection
}

fun <T> FriendlyByteBuf.readList(reader: (FriendlyByteBuf) -> T): List<T> =
    readCollection({ capacity -> ArrayList<T>(capacity) }, reader)

fun <K, V> FriendlyByteBuf.writeMap(
    map: Map<K, V>,
    keyWriter: (FriendlyByteBuf, K) -> Unit,
    valueWriter: (FriendlyByteBuf, V) -> Unit
) {
    writeVarInt(map.size)
    map.forEach { (key, value) ->
        keyWriter(this, key)
        valueWriter(this, value)
    }
}

fun <K, V> FriendlyByteBuf.readMap(keyReader: (FriendlyByteBuf) -> K, valueReader: (FriendlyByteBuf) -> V): Map<K, V> {
    val size = readVarInt()
    if (size < 0) {
        throw DecoderException("Negative packet map size: $size")
    }
    val map = LinkedHashMap<K, V>(minOf(size, ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE))
    repeat(size) { map[keyReader(this)] = valueReader(this) }
    return map
}

/** Low-level Cobblemon buffers use a four-byte length, unlike Minecraft packet collections. */
fun <T> ByteBuf.writeFixedSizeCollection(collection: Collection<T>, writer: (ByteBuf, T) -> Unit) {
    writeInt(collection.size)
    collection.forEach { writer(this, it) }
}

fun <K, V> ByteBuf.writeFixedSizeMap(map: Map<K, V>, keyWriter: (ByteBuf, K) -> Unit, valueWriter: (ByteBuf, V) -> Unit) {
    writeInt(map.size)
    map.forEach { (key, value) ->
        keyWriter(this, key)
        valueWriter(this, value)
    }
}

fun <K, V> ByteBuf.readFixedSizeMap(keyReader: (ByteBuf) -> K, valueReader: (ByteBuf) -> V): Map<K, V> {
    val size = readInt()
    if (size < 0) {
        throw DecoderException("Negative fixed-size map size: $size")
    }
    val map = mutableMapOf<K, V>()
    repeat(size) { map[keyReader(this)] = valueReader(this) }
    return map
}

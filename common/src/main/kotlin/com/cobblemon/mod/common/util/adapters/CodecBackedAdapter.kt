/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.google.gson.*
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.core.HolderLookup
import net.minecraft.resources.RegistryOps
import java.lang.reflect.Type

class CodecBackedAdapter<T>(val codec: Codec<T>, registryAccess: HolderLookup.Provider? = null) : JsonDeserializer<T>, JsonSerializer<T> {

    private val registryAccessOverride = registryAccess
    // Gson adapters outlive worlds and are often constructed before a server exists.
    // Resolve the current operation's registries instead of retaining EMPTY forever.
    val registryAccess: HolderLookup.Provider
        get() = registryAccessOverride ?: RegistryAccessResolver.getAccess()

    override fun deserialize(
        jElement: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): T {
        val ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess)
        val result = ops.withDecoder(codec).apply(jElement)
        return result.result().orElseThrow {
            IllegalStateException("Failed to deserialize $jElement: ${result.error().orElse(null)}")
        }.first
    }

    override fun serialize(
        src: T,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess)
        val result = ops.withEncoder(codec).apply(src)
        return result.result().orElseThrow {
            IllegalStateException("Failed to serialize $src: ${result.error().orElse(null)}")
        }
    }
}

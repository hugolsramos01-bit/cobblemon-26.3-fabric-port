/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.Locale

/** Preserves the lowercase enum JSON format after removal of the vanilla adapter. */
class LowerCaseEnumTypeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!type.rawType.isEnum) return null
        @Suppress("UNCHECKED_CAST")
        val constants = type.rawType.enumConstants.associate { constant ->
            (constant as Enum<*>).name.lowercase(Locale.ROOT) to (constant as T)
        }
        return object : TypeAdapter<T>() {
            override fun write(writer: JsonWriter, value: T?) {
                if (value == null) writer.nullValue()
                else writer.value((value as Enum<*>).name.lowercase(Locale.ROOT))
            }

            override fun read(reader: JsonReader): T? {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    return null
                }
                return constants[reader.nextString()]
            }
        }
    }
}

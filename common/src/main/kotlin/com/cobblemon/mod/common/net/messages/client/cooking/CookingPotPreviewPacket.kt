/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cooking

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/** Server-authoritative display state; this packet never grants an inventory item. */
class CookingPotPreviewPacket(
    val containerId: Int,
    preview: ItemStack,
    val seasoningTagId: Identifier?
) : NetworkPacket<CookingPotPreviewPacket> {
    val previewItem: ItemStack = preview.copy()
    val seasoningTag: TagKey<Item>? = seasoningTagId?.let { TagKey.create(Registries.ITEM, it) }

    init { require(containerId >= 0) { "Invalid cooking pot window ID" } }

    override val id = ID
    fun appliesTo(currentContainerId: Int) = containerId == currentContainerId

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(containerId)
        // Optional codec encodes EMPTY too and preserves registry-backed components.
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, previewItem)
        buffer.writeBoolean(seasoningTagId != null)
        seasoningTagId?.let { Identifier.STREAM_CODEC.encode(buffer, it) }
    }

    companion object {
        val ID = cobblemonResource("cooking_pot_preview")
        fun decode(buffer: RegistryFriendlyByteBuf) = CookingPotPreviewPacket(
            buffer.readVarInt(),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
            if (buffer.readBoolean()) Identifier.STREAM_CODEC.decode(buffer) else null
        )
    }
}

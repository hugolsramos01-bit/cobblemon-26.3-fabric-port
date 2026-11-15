/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.recipe

import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.crafting.RecipeHolder

/**
 * Server recipe bodies are not present in the 26.3 client recipe access, so sync only the
 * Cobblemon types consumed by JEI and the seasoning tooltip after player data sync on login.
 * The current common event API has no recipe reload callback; a `/reload` requires reconnecting.
 */
class ClientboundCobblemonRecipeSnapshotPacket(recipes: List<RecipeHolder<*>>) : NetworkPacket<ClientboundCobblemonRecipeSnapshotPacket> {
    val recipes: List<RecipeHolder<*>> = recipes.toList()

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(recipes.size <= MAX_RECIPE_COUNT) {
            "Cobblemon recipe snapshot exceeds the $MAX_RECIPE_COUNT recipe limit"
        }

        val startIndex = buffer.writerIndex()
        buffer.writeVarInt(recipes.size)
        recipes.forEach { RecipeHolder.STREAM_CODEC.encode(buffer, it) }
        require(buffer.writerIndex() - startIndex <= MAX_ENCODED_BYTES) {
            "Cobblemon recipe snapshot exceeds the $MAX_ENCODED_BYTES byte payload limit"
        }
    }

    companion object {
        val ID = cobblemonResource("recipe_snapshot")
        // Keep below the vanilla custom payload ceiling and reject oversize snapshots explicitly.
        const val MAX_RECIPE_COUNT = 4096
        const val MAX_ENCODED_BYTES = 900 * 1024

        fun decode(buffer: RegistryFriendlyByteBuf): ClientboundCobblemonRecipeSnapshotPacket {
            require(buffer.readableBytes() <= MAX_ENCODED_BYTES) {
                "Cobblemon recipe snapshot exceeds the $MAX_ENCODED_BYTES byte payload limit"
            }
            val recipeCount = buffer.readVarInt()
            require(recipeCount in 0..MAX_RECIPE_COUNT) {
                "Invalid Cobblemon recipe snapshot size: $recipeCount"
            }
            val recipes = List(recipeCount) { RecipeHolder.STREAM_CODEC.decode(buffer) }
            require(!buffer.isReadable) { "Unexpected trailing data in Cobblemon recipe snapshot" }
            return ClientboundCobblemonRecipeSnapshotPacket(recipes)
        }

        init {
            CobblemonEvents.DATA_SYNCHRONIZED.subscribe { player ->
                val recipeManager = player.level().getServer().recipeManager
                val recipes = recipeManager.recipes.filter { holder ->
                    when (holder.value().getType()) {
                        CobblemonRecipeTypes.COOKING_POT_COOKING,
                        CobblemonRecipeTypes.COOKING_POT_SHAPELESS,
                        CobblemonRecipeTypes.BREWING_STAND -> true
                        else -> false
                    }
                }
                ClientboundCobblemonRecipeSnapshotPacket(recipes).sendToPlayer(player)
            }
        }
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.recipe

import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase
import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipe
import com.cobblemon.mod.common.platform.events.PlatformEvents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.crafting.RecipeHolder
import java.util.concurrent.CopyOnWriteArrayList

data class BrewingStandRecipeSnapshot(val id: Identifier, val recipe: BrewingStandRecipe)

data class ClientCobblemonRecipeSnapshotData(
    val generation: Long,
    val cookingPotRecipes: List<CookingPotRecipeBase>,
    val brewingStandRecipes: List<BrewingStandRecipeSnapshot>
)

object ClientCobblemonRecipeSnapshot {
    @Volatile
    private var current = ClientCobblemonRecipeSnapshotData(0, emptyList(), emptyList())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val logoutSubscription: ObservableSubscription<*> = PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe { clear() }

    fun snapshot(): ClientCobblemonRecipeSnapshotData = current

    fun addUpdateListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun replace(holders: List<RecipeHolder<*>>) {
        val cookingPotRecipes = holders.mapNotNull { it.value() as? CookingPotRecipeBase }
        val brewingStandRecipes = holders.mapNotNull { holder ->
            val recipe = holder.value() as? BrewingStandRecipe ?: return@mapNotNull null
            BrewingStandRecipeSnapshot(holder.id().identifier(), recipe)
        }
        current = ClientCobblemonRecipeSnapshotData(
            current.generation + 1,
            cookingPotRecipes,
            brewingStandRecipes
        )
        listeners.forEach { it() }
    }

    fun clear() {
        current = ClientCobblemonRecipeSnapshotData(current.generation + 1, emptyList(), emptyList())
        listeners.forEach { it() }
    }
}

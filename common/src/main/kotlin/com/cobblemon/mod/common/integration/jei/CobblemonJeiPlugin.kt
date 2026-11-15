/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei

import com.cobblemon.mod.common.client.recipe.ClientCobblemonRecipeSnapshot
import com.cobblemon.mod.common.integration.jei.brewing.JeiBrewingStandRecipe
import com.cobblemon.mod.common.integration.jei.berry.BerryMutationProvider
import com.cobblemon.mod.common.integration.jei.brewing.BrewingStandJeiProvider
import com.cobblemon.mod.common.integration.jei.cooking.CampfirePotJeiProvider
import com.cobblemon.mod.common.integration.jei.cooking.CampfirePotRecipeCategory
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase
import com.cobblemon.mod.common.util.cobblemonResource
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.resources.Identifier

@JeiPlugin
class CobblemonJeiPlugin : IModPlugin {
    private var registeredSnapshotGeneration = -1L
    private var registeredCookingRecipes: List<CookingPotRecipeBase> = emptyList()
    private var registeredBrewingRecipes: List<IJeiBrewingRecipe> = emptyList()

    init {
        ClientCobblemonRecipeSnapshot.addUpdateListener(::refreshRecipes)
    }

    private val jeiProviders: Set<CobblemonJeiProvider> = setOf(
        BerryMutationProvider(),
        CampfirePotJeiProvider(),
        BrewingStandJeiProvider()
    )

    override fun getPluginUid(): Identifier {
        return ID
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        jeiProviders.forEach {
            it.registerCategory(registration)
        }
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        jeiProviders.forEach {
            it.registerRecipes(registration)
        }

        val snapshot = ClientCobblemonRecipeSnapshot.snapshot()
        registeredSnapshotGeneration = snapshot.generation
        registeredCookingRecipes = snapshot.cookingPotRecipes
        registeredBrewingRecipes = snapshot.brewingStandRecipes.map { JeiBrewingStandRecipe(it.recipe, it.id) }
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        jeiProviders.forEach {
            it.registerRecipeCatalsysts(registration)
        }
    }

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        CobblemonJeiPlugin.jeiRuntime = jeiRuntime
        refreshRecipes()
    }

    private fun refreshRecipes() {
        val runtime = jeiRuntime ?: return
        val snapshot = ClientCobblemonRecipeSnapshot.snapshot()
        if (snapshot.generation == registeredSnapshotGeneration) return

        if (registeredCookingRecipes.isNotEmpty()) {
            runtime.recipeManager.hideRecipes(CampfirePotRecipeCategory.RECIPE_TYPE, registeredCookingRecipes)
        }
        if (registeredBrewingRecipes.isNotEmpty()) {
            runtime.recipeManager.hideRecipes(RecipeTypes.BREWING, registeredBrewingRecipes)
        }

        registeredCookingRecipes = snapshot.cookingPotRecipes
        registeredBrewingRecipes = snapshot.brewingStandRecipes.map { JeiBrewingStandRecipe(it.recipe, it.id) }
        if (registeredCookingRecipes.isNotEmpty()) {
            runtime.recipeManager.addRecipes(CampfirePotRecipeCategory.RECIPE_TYPE, registeredCookingRecipes)
        }
        if (registeredBrewingRecipes.isNotEmpty()) {
            runtime.recipeManager.addRecipes(RecipeTypes.BREWING, registeredBrewingRecipes)
        }
        registeredSnapshotGeneration = snapshot.generation
    }

    companion object {
        var jeiRuntime: IJeiRuntime? = null
        val ID = cobblemonResource("jei_plugin")
    }
}

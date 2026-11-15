/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory
import net.minecraft.world.item.crafting.RecipeBookCategory
import net.minecraft.world.inventory.RecipeBookType

/** Registered leaf categories; the search tab is a client aggregate, not a recipe category. */
enum class CobblemonRecipeCategories(val id: String, private val search: Boolean = false) {
    COOKING_POT_SEARCH("COBBLEMON_COOKING_POT_SEARCH", true),
    COOKING_POT_FOODS("COBBLEMON_COOKING_POT_FOODS"),
    COOKING_POT_MEDICINES("COBBLEMON_COOKING_POT_MEDICINES"),
    COOKING_POT_COMPLEX_DISHES("COBBLEMON_COOKING_POT_COMPLEX_DISHES"),
    COOKING_POT_MISC("COBBLEMON_COOKING_POT_MISC");

    private val category: ExtendedRecipeBookCategory =
        if (search) object : ExtendedRecipeBookCategory {} else RecipeBookCategory()

    fun toVanillaCategory(): ExtendedRecipeBookCategory = category

    fun toRecipeCategory(): RecipeBookCategory = category as? RecipeBookCategory
        ?: error("Search tabs cannot be assigned to a recipe")

    companion object {
        val customAggregateCategories: Map<ExtendedRecipeBookCategory, List<RecipeBookCategory>> = mapOf(
            COOKING_POT_SEARCH.toVanillaCategory() to listOf(
                COOKING_POT_FOODS.toRecipeCategory(), COOKING_POT_MISC.toRecipeCategory(),
                COOKING_POT_MEDICINES.toRecipeCategory(), COOKING_POT_COMPLEX_DISHES.toRecipeCategory()
            )
        )

        /** Called during platform initialization, before the built-in registries freeze. */
        fun register() {
            entries.filterNot { it.search }.forEach { entry ->
                Registry.register(BuiltInRegistries.RECIPE_BOOK_CATEGORY,
                    Identifier.fromNamespaceAndPath("cobblemon", entry.id.removePrefix("COBBLEMON_").lowercase()),
                    entry.toRecipeCategory())
            }
        }
    }
}

object CobblemonRecipeBookTypes {
    const val COOKING_POT_NAME = "COBBLEMON_COOKING_POT"
    // 26.3 stores recipe-book settings in four fixed vanilla fields. The Cooking Pot therefore
    // shares its open/filter state with crafting instead of having separate persistent settings.
    val COOKING_POT: RecipeBookType = RecipeBookType.CRAFTING
}

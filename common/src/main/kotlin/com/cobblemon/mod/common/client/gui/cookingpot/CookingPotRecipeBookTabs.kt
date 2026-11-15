/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonRecipeCategories
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.TabInfo
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Optional

/** Construct icons only after item components are initialized on the client. */
object CookingPotRecipeBookTabs {
    fun createTabs(): List<TabInfo> = listOf(
        TabInfo(ItemStack(Items.COMPASS), Optional.empty(), CobblemonRecipeCategories.COOKING_POT_SEARCH.toVanillaCategory()),
        TabInfo(CobblemonItems.LEEK_AND_POTATO_STEW, CobblemonRecipeCategories.COOKING_POT_FOODS.toRecipeCategory()),
        TabInfo(CobblemonItems.POTION, CobblemonRecipeCategories.COOKING_POT_MEDICINES.toRecipeCategory()),
        TabInfo(CobblemonItems.APRIJUICE_RED, CobblemonRecipeCategories.COOKING_POT_COMPLEX_DISHES.toRecipeCategory()),
        TabInfo(CobblemonItems.PROTEIN, CobblemonRecipeCategories.COOKING_POT_MISC.toRecipeCategory())
    )
}

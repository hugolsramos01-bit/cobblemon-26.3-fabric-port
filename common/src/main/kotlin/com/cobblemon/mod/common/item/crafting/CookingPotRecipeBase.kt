/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.crafting

import com.cobblemon.mod.common.CobblemonItems
import net.minecraft.core.HolderLookup
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import java.util.Optional
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.CraftingInput
import com.cobblemon.mod.common.CobblemonRecipeCategories
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.level.Level

interface CookingPotRecipeBase : Recipe<CraftingInput> {
    val displayIngredients: List<Optional<Ingredient>>
    val result: ItemStackTemplate
    val groupName: String
    val category: CookingPotBookCategory
    val seasoningTag: TagKey<Item>
    val seasoningProcessors: List<SeasoningProcessor>

    override fun group() = groupName
    fun getGroup() = groupName
    override fun showNotification() = true
    override fun recipeBookCategory() = when (category) {
        CookingPotBookCategory.FOODS -> CobblemonRecipeCategories.COOKING_POT_FOODS
        CookingPotBookCategory.MEDICINES -> CobblemonRecipeCategories.COOKING_POT_MEDICINES
        CookingPotBookCategory.COMPLEX_DISHES -> CobblemonRecipeCategories.COOKING_POT_COMPLEX_DISHES
        CookingPotBookCategory.MISC -> CobblemonRecipeCategories.COOKING_POT_MISC
    }.toRecipeCategory()
    override fun matches(input: CraftingInput, level: Level): Boolean
    fun category() = category
    fun getResultItem(registries: HolderLookup.Provider) = this.result.create()

    override fun assemble(input: CraftingInput): ItemStack {
        return result.create()
    }

    fun assemble(input: CraftingInput, registries: HolderLookup.Provider) = assemble(input)

    fun applySeasoning(stack: ItemStack, seasoning: List<ItemStack>) {
        for (processor in seasoningProcessors) {
            processor.apply(stack, seasoning)
        }
    }

    fun getToastSymbol(): ItemStack? {
        return ItemStack(CobblemonItems.CAMPFIRE_POT_RED)
    }

}

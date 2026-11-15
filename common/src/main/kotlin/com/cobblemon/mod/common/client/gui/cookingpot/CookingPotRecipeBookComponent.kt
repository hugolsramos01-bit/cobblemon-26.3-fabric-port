/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.mixin.accessor.GhostSlotsAccessor
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.screens.recipebook.GhostSlots
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.client.gui.screens.recipebook.RecipeCollection
import net.minecraft.network.chat.Component
import net.minecraft.recipebook.PlaceRecipeHelper
import net.minecraft.resources.Identifier
import net.minecraft.util.context.ContextMap
import net.minecraft.world.entity.player.StackedItemContents
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay

class CookingPotRecipeBookComponent(menu: CookingPotMenu) :
    RecipeBookComponent<CookingPotMenu>(menu, CookingPotRecipeBookTabs.createTabs()) {

    companion object {
        private val FILTER_BUTTON_SPRITES = WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled_highlighted"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled_highlighted")
        )
    }

    override fun getFilterButtonTextures() = FILTER_BUTTON_SPRITES
    override fun getRecipeFilterName(): Component =
        Component.translatable("cobblemon.container.campfire_pot.recipe_book.toggle_recipes")

    override fun isCraftingSlot(slot: Slot) = slot.index == menu.resultSlotIndex ||
        slot.index in CampfireBlockEntity.CRAFTING_GRID_SLOTS

    override fun selectMatchingRecipes(collection: RecipeCollection, contents: StackedItemContents) {
        collection.selectRecipes(contents, ::canDisplay)
    }

    private fun canDisplay(display: RecipeDisplay) = when (display) {
        is ShapedCraftingRecipeDisplay -> display.width() in 1..menu.getGridWidth() &&
            display.height() in 1..menu.getGridHeight()
        is ShapelessCraftingRecipeDisplay -> display.ingredients().size <= menu.getGridWidth() * menu.getGridHeight()
        else -> false
    }

    override fun fillGhostRecipe(ghosts: GhostSlots, display: RecipeDisplay, context: ContextMap) {
        if (!canDisplay(display)) return
        val accessor = ghosts as GhostSlotsAccessor
        accessor.cobblemonSetResult(menu.slots[menu.resultSlotIndex], context, display.result())
        val inputs = CampfireBlockEntity.CRAFTING_GRID_SLOTS.map { menu.slots[it] }
        when (display) {
            is ShapedCraftingRecipeDisplay -> PlaceRecipeHelper.placeRecipe(menu.getGridWidth(), menu.getGridHeight(),
                display.width(), display.height(), display.ingredients()) { ingredient, index, _, _ ->
                    accessor.cobblemonSetInput(inputs[index], context, ingredient)
                }
            is ShapelessCraftingRecipeDisplay -> display.ingredients().forEachIndexed { index, ingredient ->
                accessor.cobblemonSetInput(inputs[index], context, ingredient)
            }
        }
    }
}

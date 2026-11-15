/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendToServer
import com.cobblemon.mod.common.block.campfirepot.CampfirePotColor
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.campfirepot.SeasoningSlot
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.COOKING_POT_COLOR_INDEX
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.IS_LID_OPEN_INDEX
import com.cobblemon.mod.common.integration.jei.CobblemonJeiPlugin
import com.cobblemon.mod.common.integration.jei.cooking.CampfirePotRecipeCategory
import com.cobblemon.mod.common.net.messages.client.cooking.ToggleCookingPotLidPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenPosition
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import kotlin.math.ceil

@Environment(EnvType.CLIENT)
class CookingPotScreen(menu: CookingPotMenu, playerInventory: Inventory, title: Component) :
    AbstractRecipeBookScreen<CookingPotMenu>(menu, CookingPotRecipeBookComponent(menu), playerInventory,
        Component.translatable("cobblemon.container.campfire_pot")) {

    companion object {
        private const val BACKGROUND_HEIGHT = 166
        private const val BACKGROUND_WIDTH = 176
        const val COOK_PROGRESS_HEIGHT = 12
        const val COOK_PROGRESS_WIDTH = 22
        private val BACKGROUND = cobblemonResource("textures/gui/campfirepot/campfire_pot.png")
        val COOK_PROGRESS_SPRITE: Identifier = cobblemonResource("textures/gui/campfirepot/cook_progress.png")
    }

    private lateinit var cookButton: CookButton

    override fun init() {
        super.init()
        titleLabelX = imageWidth / 2 - font.width(title) / 2
        titleLabelY = 6
        if (::cookButton.isInitialized) removeWidget(cookButton)
        cookButton = CookButton(leftPos + 97, topPos + 56, menu.containerData.get(IS_LID_OPEN_INDEX) == 0,
            CampfirePotColor.entries[menu.containerData.get(COOKING_POT_COLOR_INDEX).coerceIn(0, CampfirePotColor.entries.lastIndex)]) {
            sendToServer(ToggleCookingPotLidPacket(menu.containerData.get(IS_LID_OPEN_INDEX) == 0))
        }
        addRenderableWidget(cookButton)
    }

    override fun getRecipeBookButtonPosition() = ScreenPosition(leftPos + 5, topPos + 35)

    override fun onRecipeBookButtonClick() {
        if (::cookButton.isInitialized) cookButton.setPosition(leftPos + 97, topPos + 56)
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040.toInt(), false)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0f, 0f,
            BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_WIDTH, BACKGROUND_HEIGHT)
        val progress = ceil(menu.getBurnProgress() * COOK_PROGRESS_WIDTH).toInt().coerceIn(0, COOK_PROGRESS_WIDTH)
        if (progress > 0) graphics.blit(RenderPipelines.GUI_TEXTURED, COOK_PROGRESS_SPRITE, leftPos + 96, topPos + 39,
            0f, 0f, progress, COOK_PROGRESS_HEIGHT, COOK_PROGRESS_WIDTH, COOK_PROGRESS_HEIGHT)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (::cookButton.isInitialized) {
            cookButton.color = CampfirePotColor.entries[menu.containerData.get(COOKING_POT_COLOR_INDEX).coerceIn(0, CampfirePotColor.entries.lastIndex)]
            cookButton.selected = menu.containerData.get(IS_LID_OPEN_INDEX) == 0
            cookButton.setPosition(leftPos + 97, topPos + 56)
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    override fun extractSlot(graphics: GuiGraphicsExtractor, slot: Slot, mouseX: Int, mouseY: Int) {
        if (slot is SeasoningSlot && ((!menu.carried.isEmpty && !slot.mayPlace(menu.carried)) ||
            (slot.hasItem() && !slot.mayPlace(slot.item)))) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 822018048)
        }
        super.extractSlot(graphics, slot, mouseX, mouseY)
        // Display-only state from the server; never inserted into the result inventory slot.
        // The native recipe display separately supplies an unseasoned ghost result.
        if (slot.index == menu.resultSlotIndex && !slot.hasItem() && !menu.previewItem.isEmpty) {
            graphics.fakeItem(menu.previewItem, slot.x, slot.y)
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80C6C6C6.toInt())
            graphics.itemDecorations(font, menu.previewItem, slot.x, slot.y)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (isProgressHovered(event.x(), event.y()) && Cobblemon.implementation.isModInstalled("jei")) {
            CobblemonJeiPlugin.jeiRuntime?.recipesGui?.showTypes(listOf(CampfirePotRecipeCategory.RECIPE_TYPE))
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun extractTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (isProgressHovered(mouseX.toDouble(), mouseY.toDouble()) && Cobblemon.implementation.isModInstalled("jei")) {
            graphics.setTooltipForNextFrame(font, Component.translatable("jei.tooltip.show.recipes"), mouseX, mouseY)
            return
        }
        val resultSlot = menu.slots[menu.resultSlotIndex]
        if (!resultSlot.hasItem() && !menu.previewItem.isEmpty && menu.carried.isEmpty &&
            isHovering(resultSlot.x, resultSlot.y, 16, 16, mouseX.toDouble(), mouseY.toDouble())) {
            graphics.setTooltipForNextFrame(font, menu.previewItem, mouseX, mouseY)
            return
        }
        super.extractTooltip(graphics, mouseX, mouseY)
    }

    private fun isProgressHovered(x: Double, y: Double) =
        x >= leftPos + 96 && x < leftPos + 96 + COOK_PROGRESS_WIDTH &&
        y >= topPos + 39 && y < topPos + 39 + COOK_PROGRESS_HEIGHT
}

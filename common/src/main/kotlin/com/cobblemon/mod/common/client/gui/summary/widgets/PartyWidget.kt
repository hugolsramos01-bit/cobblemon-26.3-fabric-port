/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.SummaryButton
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import java.security.InvalidParameterException

class PartyWidget(
    pX: Int,
    pY: Int,
    val isParty: Boolean,
    val summary: Summary,
    private val partyList: List<Pokemon?>
) : SoundlessWidget(pX, pY, WIDTH, HEIGHT, Component.literal("PartyOverlay")) {

    companion object {
        const val WIDTH = 114
        const val HEIGHT = 113
        private const val SCALE = 0.5F

        private val backgroundResource = cobblemonResource("textures/gui/summary/summary_party_background.png")
        private val swapButtonResource = cobblemonResource("textures/gui/summary/summary_party_swap.png")
        private val swapButtonActiveResource = cobblemonResource("textures/gui/summary/summary_party_swap_active.png")
        private val swapButtonIconResource = cobblemonResource("textures/gui/summary/summary_party_swap_icon.png")
    }

    var swapEnabled: Boolean = false
    var swapSource: Int? = null
    var draggedSlot: PartySlotWidget? = null

    private val partySize = partyList.size
    private val partySlots = arrayListOf<PartySlotWidget>()
    private val swapButton: SummaryButton = SummaryButton(
        buttonX = x + 80F,
        buttonY = y - 9F,
        buttonWidth = 26,
        buttonHeight = 14,
        resource = swapButtonResource,
        activeResource = swapButtonActiveResource,
        clickAction = {
            enableSwap(!swapEnabled)
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
        }
    )

    init {
        if (partySize > 6 || partySize < 1)
            throw InvalidParameterException("Invalid party size")

        this.partyList.forEachIndexed { index, pokemon ->
            var x = x + 6
            var y = y + 7

            if (index > 0) {
                val isEven = index % 2 == 0
                val offsetIndex = (index - (if (isEven) 0 else 1)) / 2
                val offsetX = if (isEven) 0 else 51
                val offsetY = if (isEven) 0 else 8

                x += offsetX
                y += (32 * offsetIndex) + offsetY
            }

            PartySlotWidget(
                pX = x,
                pY = y,
                partyWidget = this,
                summary = summary,
                pokemon = pokemon,
                index = index,
                isClientPartyMember = isParty
            ).also { widget ->
                this.addWidget(widget)
                partySlots.add(widget)
            }
        }
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, pMouseX: Int, pMouseY: Int, pPartialTicks: Float) {
        blitk(
            context = context,
            texture = backgroundResource,
            x = x,
            y = y,
            width = width,
            height = height
        )

        // Label
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.party").bold(),
            x = x + 32.5,
            y = y - 14.5,
            centered = true,
            shadow = true
        )

        swapButton.extractRenderState(context, pMouseX, pMouseY, pPartialTicks)

        blitk(
            context = context,
            texture = swapButtonIconResource,
            x = (x + 90) / SCALE,
            y = (y - 6) / SCALE,
            width = 12,
            height = 17,
            scale = SCALE
        )

        partySlots.forEach { it.extractRenderState(context, pMouseX, pMouseY, pPartialTicks) }

        if (draggedSlot != null) {
            // Extract the dragged slot last so it stays above the party slots.
            draggedSlot!!.extractRenderState(context, pMouseX, pMouseY, pPartialTicks)
        }
    }

    override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!active || !visible || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        if (swapButton.isButtonHovered(event.x(), event.y())) {
            swapButton.onPress(event)
            return true
        }

        val index = getIndexFromPos(event.x(), event.y())
        if (index < 0) return false
        val pokemon = partyList[index] ?: return true
        if (swapEnabled) {
            swapSource = index
            draggedSlot = PartySlotWidget(
                pX = event.x() - PartySlotWidget.WIDTH / 2,
                pY = event.y() - PartySlotWidget.HEIGHT / 2,
                partyWidget = this,
                summary = summary,
                pokemon = pokemon,
                index = -1,
                isClientPartyMember = isParty
            )
            playSound(CobblemonSounds.PC_GRAB)
        } else if (summary.selectedPokemon.uuid != pokemon.uuid) {
            summary.switchSelection(index)
            playSound(CobblemonSounds.GUI_CLICK)
        }
        // Keep focus on this panel so 26.3 dispatches drag/release to the same owner.
        return true
    }

    override fun mouseReleased(event: net.minecraft.client.input.MouseButtonEvent): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        val source = swapSource ?: return false
        val target = getIndexFromPos(event.x(), event.y())
        cancelDrag()
        if (swapEnabled && target >= 0 && target != source) {
            summary.swapPartySlot(source, target)
        }
        playSound(CobblemonSounds.PC_DROP)
        return true
    }

    override fun mouseDragged(event: net.minecraft.client.input.MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (!swapEnabled || swapSource == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        if (!isWithinScreen(event.x(), event.y())) {
            cancelDrag()
            playSound(CobblemonSounds.PC_DROP)
        } else {
            // Only the temporary preview moves. Party cards keep their layout coordinates.
            draggedSlot?.setPosition(
                (event.x() - PartySlotWidget.WIDTH / 2).toInt(),
                (event.y() - PartySlotWidget.HEIGHT / 2).toInt()
            )
        }
        return true
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean = active && visible &&
        (isWithinScreen(mouseX, mouseY) || swapButton.isButtonHovered(mouseX, mouseY))

    private fun cancelDrag() {
        swapSource = null
        draggedSlot = null
    }

    fun enableSwap(boolean: Boolean = true) {
        swapEnabled = boolean && isParty
        swapButton.buttonActive = swapEnabled
        if (!swapEnabled) cancelDrag()
    }

    private fun getIndexFromPos(mouseX: Double, mouseY: Double): Int {
        for ((index, slot) in partySlots.withIndex()) {
            if (mouseX >= slot.x && mouseX < slot.x + slot.width
                && mouseY >= slot.y && mouseY < slot.y + slot.height) {
                return index
            }
        }
        return -1
    }

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }

    fun isWithinScreen(mouseX: Double, mouseY: Double): Boolean = mouseX >= x && mouseX < x + WIDTH
            && mouseY >= y && mouseY < y + HEIGHT
}

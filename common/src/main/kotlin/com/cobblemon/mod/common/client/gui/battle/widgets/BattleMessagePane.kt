/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.battle.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.battle.ClientBattleMessageQueue
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.util.FormattedCharSequence

/**
 * Pane for seeing and interacting with battle messages.
 *
 * @author Hiroku
 * @since June 24th, 2022
 */
class BattleMessagePane(
    private val messageQueue: ClientBattleMessageQueue
): ObjectSelectionList<BattleMessagePane.BattleMessageLine>(
    Minecraft.getInstance(),
    FRAME_WIDTH, // width
    FRAME_HEIGHT, // height
    1, // top
    LINE_HEIGHT
), CobblemonRenderable {
    var opacity = 1F
    private var scrolling = false
    private val messageListener: (FormattedCharSequence) -> Unit = {
        val fullyScrolledDown = maxScrollAmount() - scrollAmount() < 10
        addEntry(BattleMessageLine(this, it))
        if (fullyScrolledDown) setScrollAmount(maxScrollAmount().toDouble())
    }

    val appropriateX: Int
        get() = minecraft.window.guiScaledWidth - (FRAME_WIDTH + 12)
    val appropriateY: Int
        get() = minecraft.window.guiScaledHeight - (30 + (if (expanded) FRAME_EXPANDED_HEIGHT else FRAME_HEIGHT))

    init {
        correctSize()
        //setRenderBackground(false)

        messageQueue.subscribe(messageListener)
    }

    fun dispose() {
        messageQueue.listeners.remove(messageListener)
    }

    private fun correctSize() {
        val textBoxHeight = if (expanded) TEXT_BOX_HEIGHT * 2 else TEXT_BOX_HEIGHT
        setRectangle(TEXT_BOX_WIDTH, textBoxHeight, appropriateX, appropriateY + 6)
    }

    companion object {
        const val LINE_HEIGHT = 10
        const val LINE_WIDTH = 146
        const val FRAME_WIDTH = 169
        const val FRAME_HEIGHT = 55
        const val FRAME_EXPANDED_HEIGHT = 101
        const val TEXT_BOX_WIDTH = 153
        const val TEXT_BOX_HEIGHT = 46
        const val EXPAND_TOGGLE_SIZE = 5

        private val battleMessagePaneFrameResource = cobblemonResource("textures/gui/battle/battle_log.png")
        private val battleMessagePaneFrameExpandedResource = cobblemonResource("textures/gui/battle/battle_log_expanded.png")
        private val battleMessageHighlight = cobblemonResource("textures/gui/battle/battle_log_row_selected_color.png")
        private var expanded = false
    }

    override fun addEntry(entry: BattleMessageLine): Int {
        return super.addEntry(entry)
    }

    override fun getRowLeft(): Int {
        return super.getRowLeft() + 4
    }

    override fun extractSelection(guiGraphics: GuiGraphicsExtractor, entry: BattleMessageLine, color: Int) {
        blitk(
            context = guiGraphics,
            texture = battleMessageHighlight,
            x = x + 6,
            y = entry.y - 2,
            height = 10,
            width = 1,
            alpha = opacity
        )

        blitk(
            context = guiGraphics,
            texture = battleMessageHighlight,
            x = x + 6,
            y = entry.y - 2,
            height = 1,
            width = LINE_WIDTH,
            alpha = opacity
        )

        blitk(
            context = guiGraphics,
            texture = battleMessageHighlight,
            x = x + 6,
            y = entry.y + 7,
            height = 1,
            width = LINE_WIDTH,
            alpha = opacity
        )

        blitk(
            context = guiGraphics,
            texture = battleMessageHighlight,
            x = x + 6 + LINE_WIDTH - 1,
            y = entry.y - 2,
            height = 10,
            width = 1,
            alpha = opacity
        )
    }

    override fun getRowWidth(): Int {
        return LINE_WIDTH
    }

    override fun scrollBarX(): Int {
        return this.x + 154
    }

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        correctSize()
        blitk(
            context = context,
            texture = if (expanded) battleMessagePaneFrameExpandedResource else battleMessagePaneFrameResource,
            x = this.x,
            y = appropriateY,
            height = if (expanded) FRAME_EXPANDED_HEIGHT else FRAME_HEIGHT,
            width = FRAME_WIDTH,
            alpha = opacity
        )

        val textBoxHeight = if (expanded) TEXT_BOX_HEIGHT * 2 else TEXT_BOX_HEIGHT
        context.enableScissor(
            this.x + 5,
            appropriateY + 6,
            this.x + 5 + width,
            appropriateY + 6 + textBoxHeight
        )
        super.extractWidgetRenderState(context, mouseX, mouseY, partialTicks)
        context.disableScissor()
    }

    override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean): Boolean {
    val mouseX = event.x()
    val mouseY = event.y()
    val button = event.button()

        val toggleOffsetY = if (expanded) 92 else 46
        if (mouseX > (this.x + 160) && mouseX < (this.x + 160 + EXPAND_TOGGLE_SIZE) && mouseY > (appropriateY + toggleOffsetY) && mouseY < (appropriateY + toggleOffsetY + EXPAND_TOGGLE_SIZE)) {
            expanded = !expanded
        }

        updateScrollingState(mouseX, mouseY)
        if (scrolling) {
            focused = getEntryAtPosition(mouseX, mouseY)
            isDragging = true
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        val toggleY = appropriateY + if (expanded) 92 else 46
        val overToggle = mouseX > x + 160 && mouseX < x + 160 + EXPAND_TOGGLE_SIZE
                && mouseY > toggleY && mouseY < toggleY + EXPAND_TOGGLE_SIZE
        val overScrollBar = mouseX >= scrollBarX() && mouseX < scrollBarX() + 3
                && mouseY >= y && mouseY < bottom
        return super.isMouseOver(mouseX, mouseY) || overToggle || overScrollBar
    }

    override fun mouseDragged(event: net.minecraft.client.input.MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
    val mouseX = event.x()
    val mouseY = event.y()
    val button = event.button()

        if (scrolling) {
            if (mouseY < this.y) {
                setScrollAmount(0.0)
            } else if (mouseY > bottom) {
                setScrollAmount(maxScrollAmount().toDouble())
            } else {
                setScrollAmount(scrollAmount() + deltaY)
            }
        }
        return super.mouseDragged(event, deltaX, deltaY)
    }

    private fun updateScrollingState(mouseX: Double, mouseY: Double) {
        scrolling = mouseX >= this.scrollBarX().toDouble()
                && mouseX < (this.scrollBarX() + 3).toDouble()
                && mouseY >= this.y
                && mouseY < bottom
    }

    class BattleMessageLine(val pane: BattleMessagePane, val line: FormattedCharSequence) : Entry<BattleMessageLine>() {
        override fun getNarration() = "".text()
        override fun extractContent(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, isHovered: Boolean, partialTicks: Float) {
            drawScaledText(
                context,
                line,
                x,
                y - 2,
                opacity = pane.opacity
            )
        }
    }
}

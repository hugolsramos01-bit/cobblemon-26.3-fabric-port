/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import kotlin.jvm.JvmName

/**
 * Cobblemon's compact list surface, implemented on the 26.3 extraction API.
 * Entries keep the old row callback contract so existing list behavior stays
 * local to the widget while the game collects GUI render state.
 */
abstract class ScrollingWidget<T : ScrollingWidget.Entry<T>>(
    top: Int = 0,
    val left: Int = 0,
    width: Int = 10,
    height: Int = 10,
    val slotHeight: Int = 10,
    val scrollBarWidth: Int = 5
) : ObjectSelectionList<T>(
    Minecraft.getInstance(),
    width,
    height,
    top + height,
    slotHeight
), CobblemonRenderable {

    @get:JvmName("getCobblemonScrollAmount")
    @set:JvmName("setCobblemonScrollAmount")
    var scrollAmount: Double
        get() = scrollAmount()
        set(value) = setScrollAmount(value)

    open fun getMaxScroll(): Int = maxScrollAmount()
    val maxPosition: Int get() = contentHeight()
    val itemHeight: Int get() = slotHeight

    val scrollbarPosition: Int get() = scrollBarX()

    init {
        setX(left)
        centerListVertically = false
    }

    override fun scrollBarX(): Int = left + width - scrollBarWidth

    override fun contentHeight(): Int = children().size * slotHeight

    open fun getEntry(index: Int): T = children()[index]

    override fun getRowLeft(): Int = left
    override fun getRowRight(): Int = left + width
    override fun getRowWidth(): Int = width
    override fun getRowTop(index: Int): Int = y + 2 - scrollAmount.toInt() + index * slotHeight
    override fun getRowBottom(index: Int): Int = getRowTop(index) + slotHeight

    override fun extractListBackground(context: GuiGraphicsExtractor) = Unit
    override fun extractListSeparators(context: GuiGraphicsExtractor) = Unit
    override fun extractSelection(context: GuiGraphicsExtractor, entry: T, color: Int) = Unit

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        focused = if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
            getEntryAtPosition(mouseX.toDouble(), mouseY.toDouble())
        } else {
            null
        }

        context.enableScissor(x, y, right, bottom)
        try {
            renderListItems(context, mouseX, mouseY, delta)
        } finally {
            context.disableScissor()
        }

        if (getMaxScroll() > 0) {
            renderScrollbar(context, mouseX, mouseY, delta)
        }
    }

    open fun renderListItems(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        children().forEachIndexed { index, entry ->
            val rowTop = getRowTop(index)
            val rowBottom = getRowBottom(index)
            if (rowBottom >= y && rowTop <= bottom) {
                renderItem(context, mouseX, mouseY, delta, index, getRowLeft(), rowTop, rowWidth, slotHeight)
            }
        }
    }

    open fun renderItem(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        index: Int,
        x: Int,
        y: Int,
        entryWidth: Int,
        entryHeight: Int
    ) {
        val entry = getEntry(index)
        entry.renderBack(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, focused === entry, delta)
        entry.render(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, focused === entry, delta)
    }

    open fun renderScrollbar(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val xLeft = scrollBarX()
        val xRight = xLeft + scrollBarWidth
        val barHeight = height
        if (barHeight <= 0) return

        val thumbHeight = Mth.clamp(
            (barHeight.toLong() * barHeight / maxPosition.coerceAtLeast(1)).toInt(),
            minOf(1, barHeight),
            barHeight
        )
        val maxScroll = getMaxScroll().coerceAtLeast(1)
        val thumbTop = y + (scrollAmount * (barHeight - thumbHeight) / maxScroll).toInt()

        context.fill(xLeft, y, xRight, bottom, 0xFF000000.toInt())
        context.fill(xLeft, thumbTop, xRight, thumbTop + thumbHeight, 0xFF808080.toInt())
        context.fill(xLeft, thumbTop, xRight - 1, thumbTop + thumbHeight - 1, 0xFFC0C0C0.toInt())
    }

    abstract class Entry<T : Entry<T>> : ObjectSelectionList.Entry<T>() {
        override fun getNarration(): Component = Component.empty()

        open fun renderBack(
            context: GuiGraphicsExtractor,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) = Unit

        open fun render(
            context: GuiGraphicsExtractor,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) = Unit

        final override fun extractContent(
            context: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float
        ) {
            renderBack(context, -1, y, x, width, height, mouseX, mouseY, hovered, delta)
            render(context, -1, y, x, width, height, mouseX, mouseY, hovered, delta)
        }
    }

    abstract class Slot<T : Slot<T>> : Entry<T>()
}

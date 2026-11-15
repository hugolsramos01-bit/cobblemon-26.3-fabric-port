/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.gui.tmmachine

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import com.cobblemon.mod.common.api.gui.withTextAlpha

/** The TM description's bounded, left-aligned text, extracted instead of drawn immediately. */
class TMTextLines private constructor(private val lines: List<String>) {
    companion object {
        fun create(component: Component, width: Number, maxLines: Number): TMTextLines = TMTextLines(
            Minecraft.getInstance().font.splitter.splitLines(component, width.toInt(), Style.EMPTY)
                .take(maxLines.toInt()).map { it.string }
        )
    }
    fun renderLeftAligned(context: GuiGraphicsExtractor, x: Number, y: Number,
        ySpacing: Number, colour: Int, shadow: Boolean = true) {
        lines.forEachIndexed { index, line ->
            context.text(Minecraft.getInstance().font, line, x.toInt(),
                (y.toFloat() + ySpacing.toFloat() * index).toInt(), withTextAlpha(colour), shadow)
        }
    }
}

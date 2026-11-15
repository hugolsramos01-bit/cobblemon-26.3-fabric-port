/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui

import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.gui.drawPosableGuiPortrait
import com.cobblemon.mod.common.client.gui.drawPosableGuiProfile
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence

/** Preserve ARGB alpha, while treating legacy 24-bit RGB text colors as opaque. */
fun withTextAlpha(color: Int): Int = if (color ushr 24 == 0) color or (0xFF shl 24) else color

@JvmOverloads
fun blitk(
    context: GuiGraphicsExtractor,
    texture: Identifier? = null,
    x: Number,
    y: Number,
    height: Number = 0,
    width: Number = 0,
    uOffset: Number = 0,
    vOffset: Number = 0,
    textureWidth: Number = width,
    textureHeight: Number = height,
    blitOffset: Number = 0,
    red: Number = 1,
    green: Number = 1,
    blue: Number = 1,
    alpha: Number = 1F,
    blend: Boolean = true,
    scale: Float = 1F
) {
    val identifier = texture ?: return
    val drawWidth = width.toInt()
    val drawHeight = height.toInt()
    val atlasWidth = textureWidth.toFloat()
    val atlasHeight = textureHeight.toFloat()
    if (drawWidth <= 0 || drawHeight <= 0 || atlasWidth <= 0f || atlasHeight <= 0f || alpha.toFloat() <= 0f) return

    fun channel(value: Number) = (value.toFloat().coerceIn(0f, 1f) * 255f).toInt()
    val tint = (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    val pose = context.pose()
    pose.pushMatrix()
    try {
        pose.scale(scale, scale)
        pose.translate(x.toFloat(), y.toFloat())
        if (blitOffset.toFloat() != 0f) context.nextStratum()
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            identifier,
            0,
            0,
            uOffset.toFloat(),
            vOffset.toFloat(),
            drawWidth,
            drawHeight,
            atlasWidth.toInt(),
            atlasHeight.toInt(),
            tint
        )
    } finally {
        pose.popMatrix()
    }
}

@JvmOverloads
fun drawCenteredText(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: Component,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true
) {
    val comp = if (font == null) text else text.copy().withStyle { it.withFont(FontDescription.Resource(font)) }
    val textRenderer = Minecraft.getInstance().font
    context.text(textRenderer, comp, x.toInt() - textRenderer.width(comp) / 2, y.toInt(), withTextAlpha(colour), shadow)
}

@JvmOverloads
fun drawText(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: MutableComponent,
    x: Number,
    y: Number,
    centered: Boolean = false,
    colour: Int,
    shadow: Boolean = true,
    pMouseX: Number? = null,
    pMouseY: Number? = null
): Boolean {
    val comp = if (font == null) text else text.copy().withStyle { it.withFont(FontDescription.Resource(font)) }
    val textRenderer = Minecraft.getInstance().font
    var x = x
    val width = textRenderer.width(comp)
    if (centered) {
        x = x.toDouble() - width / 2
    }
    context.text(textRenderer, comp, x.toInt(), y.toInt(), withTextAlpha(colour), shadow)
    var isHovered = false
    if (pMouseY != null && pMouseX != null) {
        if (pMouseX.toInt() >= x.toInt() && pMouseX.toInt() <= x.toInt() + width &&
            pMouseY.toInt() >= y.toInt() && pMouseY.toInt() <= y.toInt() + textRenderer.lineHeight
        ) {
            isHovered = true
        }
    }
    return isHovered
}

@JvmOverloads
fun drawTextJustifiedRight(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: MutableComponent,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true
) {
    val comp = text.let { if (font != null) it.font(font) else it }
    val fontRenderer = Minecraft.getInstance().font
    context.text(fontRenderer, comp, x.toInt() - fontRenderer.width(comp), y.toInt(), withTextAlpha(colour), shadow)
}

@JvmOverloads
fun drawText(
    context: GuiGraphicsExtractor,
    text: FormattedCharSequence,
    x: Number,
    y: Number,
    centered: Boolean = false,
    colour: Int,
    shadow: Boolean = true
) {
    val textRenderer = Minecraft.getInstance().font
    var tweakedX = x
    if (centered) {
        val width = textRenderer.width(text)
        tweakedX = tweakedX.toDouble() - width / 2
    }
    context.text(textRenderer, text, tweakedX.toInt(), y.toInt(), withTextAlpha(colour), shadow)
}

@JvmOverloads
fun drawString(
    context: GuiGraphicsExtractor,
    text: String,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true,
    font: Identifier? = null
) {
    val comp = Component.literal(text).also {
        font?.let { resource -> it.setStyle(it.style.withFont(FontDescription.Resource(resource))) }
    }
    val textRenderer = Minecraft.getInstance().font
    context.text(textRenderer, comp, x.toInt(), y.toInt(), withTextAlpha(colour), shadow)
}

@JvmOverloads
fun drawPosablePortrait(
    identifier: Identifier,
    context: GuiGraphicsExtractor,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    drawPosableGuiPortrait(
        context = context,
        identifier = identifier,
        contextScale = contextScale,
        state = state,
        reversed = reversed,
        partialTicks = partialTicks,
        doQuirks = doQuirks,
        limbSwing = limbSwing,
        limbSwingAmount = limbSwingAmount,
        ageInTicks = ageInTicks,
        headYaw = headYaw,
        headPitch = headPitch,
        scale = scale,
        r = r,
        g = g,
        b = b,
        a = a
    )
}

fun drawProfile(
    resourceIdentifier: Identifier,
    context: GuiGraphicsExtractor,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F
) {
    drawPosableGuiProfile(context, resourceIdentifier, state, partialTicks, scale)
}

fun renderSprite(context: GuiGraphicsExtractor, sprite: Identifier) {
    context.blit(RenderPipelines.GUI_TEXTURED, sprite, -1, 0, 0F, 0F, 2, 2, 2, 2)
}

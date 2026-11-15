/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tmmachine

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import com.cobblemon.mod.common.api.gui.withTextAlpha

/** Extract a tinted atlas region while retaining fractional positions and scale. */
fun blitk(matrixStack: GuiGraphicsExtractor, texture: Identifier?, x: Number, y: Number,
    height: Number = 0, width: Number = 0, uOffset: Number = 0, vOffset: Number = 0,
    textureWidth: Number = width, textureHeight: Number = height, blitOffset: Number = 0,
    red: Number = 1, green: Number = 1, blue: Number = 1, alpha: Number = 1f,
    blend: Boolean = true, scale: Float = 1f) {
    if (texture == null || width.toFloat() <= 0 || height.toFloat() <= 0 || alpha.toFloat() <= 0) return
    fun channel(value: Number) = (value.toFloat().coerceIn(0f,1f) * 255).toInt()
    val tint = (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    val pose = matrixStack.pose()
    pose.pushMatrix()
    try {
        pose.scale(scale, scale)
        pose.translate(x.toFloat(), y.toFloat())
        matrixStack.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, uOffset.toFloat(), vOffset.toFloat(),
            width.toInt(), height.toInt(), textureWidth.toInt(), textureHeight.toInt(), tint)
    } finally { pose.popMatrix() }
}

fun drawScaledText(context: GuiGraphicsExtractor, font: Identifier? = null, text: Component,
    x: Number, y: Number, scale: Float = 1f, opacity: Number = 1f,
    maxCharacterWidth: Int = Int.MAX_VALUE, colour: Int = 0xFFFFFF or ((opacity.toFloat()*255).toInt() shl 24),
    centered: Boolean = false, shadow: Boolean = false) {
    if (opacity.toFloat() < 0.05f) return
    val label = if (font == null) text else text.copy().withStyle { it.withFont(FontDescription.Resource(font)) }
    val renderer = Minecraft.getInstance().font
    val textWidth = renderer.width(label)
    val fit = if (textWidth <= maxCharacterWidth) 1f else maxCharacterWidth.toFloat()/textWidth
    val effectiveScale = scale * fit
    val pose = context.pose()
    pose.pushMatrix()
    try {
        pose.scale(effectiveScale,effectiveScale)
        val tx = x.toFloat()/effectiveScale - if (centered) textWidth/2f else 0f
        val ty = y.toFloat()/effectiveScale + (1f-fit)*(if(font==null)5f else 6f)*scale
        context.text(renderer,label,tx.toInt(),ty.toInt(),withTextAlpha(colour),shadow)
    } finally { pose.popMatrix() }
}

fun drawScaledTextJustifiedRight(context: GuiGraphicsExtractor, font: Identifier? = null, text: Component,
    x: Number, y: Number, scale: Float = 1f, opacity: Number = 1f,
    maxCharacterWidth: Int = Int.MAX_VALUE, colour: Int = 0xFFFFFF or ((opacity.toFloat()*255).toInt() shl 24),
    shadow: Boolean = false) {
    val label = if(font==null)text else text.copy().withStyle { it.withFont(FontDescription.Resource(font)) }
    val width=Minecraft.getInstance().font.width(label)
    val fit=if(width<=maxCharacterWidth)1f else maxCharacterWidth.toFloat()/width
    drawScaledText(context,font,text,x.toFloat()-width*scale*fit,y,scale,opacity,maxCharacterWidth,colour,shadow=shadow)
}

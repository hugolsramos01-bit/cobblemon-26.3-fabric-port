/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.block.campfirepot.CampfirePotColor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.components.Button
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

class CookButton(
    pX: Int, pY: Int,
    var selected: Boolean = false,
    var color: CampfirePotColor,
    onPress: OnPress
): Button(pX, pY, SIZE.toInt(), SIZE.toInt(), Component.literal("Cook"), onPress, DEFAULT_NARRATION) {

    companion object {
        private const val SIZE = 20F

        private val buttonResource = cobblemonResource("textures/gui/campfirepot/button.png")
    }

    override fun extractContents(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val suffix = if (selected) "" else "_open"
        val icon = cobblemonResource("textures/item/campfire_pots/campfire_pot_${color.suffix}${suffix}.png")
        context.blit(RenderPipelines.GUI_TEXTURED, buttonResource, x, y, 0f,
            if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) SIZE else 0f,
            SIZE.toInt(), SIZE.toInt(), SIZE.toInt(), (SIZE * 2).toInt())
        context.blit(RenderPipelines.GUI_TEXTURED, icon, x + 2, y + 2, 0f, 0f, 16, 16, 16, 16)
    }

    override fun playDownSound(soundManager: SoundManager) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F))
    }
}
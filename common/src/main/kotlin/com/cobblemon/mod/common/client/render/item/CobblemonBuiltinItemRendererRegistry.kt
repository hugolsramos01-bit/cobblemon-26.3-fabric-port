/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import net.minecraft.world.item.Item
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/**
 * A registry of [CobblemonBuiltinItemRenderer]s.
 *
 * @author Nick, Licious
 * @since December 28th, 2022
 */
object CobblemonBuiltinItemRendererRegistry {

    private val renderers = hashMapOf<Item, CobblemonBuiltinItemRenderer>()

    fun register(item: Item, renderer: CobblemonBuiltinItemRenderer) {
        this.renderers[item] = renderer
    }

    fun rendererOf(item: Item): CobblemonBuiltinItemRenderer? = this.renderers[item]

    /**
     * Replaces legacy builtin/entity item rendering with a real special-model layer in the 26.3
     * extracted item state. The renderer captures its dynamic geometry before this state is
     * submitted later in the frame.
     */
    @JvmStatic
    fun extract(stack: ItemStack, mode: ItemDisplayContext, renderState: ItemStackRenderState): Boolean {
        val renderer = rendererOf(stack.item) ?: return false
        renderState.clear()
        renderState.appendModelIdentityElement(renderer)
        val rendererState = renderer.extract(stack, mode)
        if (rendererState != null) {
            renderState.newLayer().setupSpecialModel(renderer, CobblemonBuiltinItemRenderer.RenderState(renderer, rendererState))
        }
        return true
    }

}

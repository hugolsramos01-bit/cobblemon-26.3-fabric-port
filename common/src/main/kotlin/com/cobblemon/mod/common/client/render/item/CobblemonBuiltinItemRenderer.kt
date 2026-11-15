/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.special.SpecialModelRenderer
import java.util.function.Consumer
import org.joml.Vector3fc

/**
 * A custom renderer for an item whose render data is extracted into a special-model item layer.
 *
 * @author Nick, Licious
 * @since December 28th, 2022
 */
interface CobblemonBuiltinItemRenderer : SpecialModelRenderer<CobblemonBuiltinItemRenderer.RenderState> {

    /** Captures item-dependent render data while the item stack is being extracted. */
    fun extract(stack: ItemStack, mode: ItemDisplayContext): Any?

    /** Submits previously extracted data to the deferred 26.3 render pipeline. */
    fun submitExtracted(state: Any, matrices: PoseStack, collector: SubmitNodeCollector, light: Int, overlay: Int, hasFoil: Boolean, outlineColor: Int)

    override fun extractArgument(stack: ItemStack): RenderState = RenderState(this, extract(stack, ItemDisplayContext.NONE))

    override fun submit(
        argument: RenderState?,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int,
        hasFoil: Boolean,
        outlineColor: Int
    ) {
        val renderState = argument ?: return
        val state = renderState.state ?: return
        renderState.renderer.submitExtracted(state, poseStack, collector, packedLight, packedOverlay, hasFoil, outlineColor)
    }

    override fun getExtents(extents: Consumer<Vector3fc>) = Unit

    data class RenderState(val renderer: CobblemonBuiltinItemRenderer, val state: Any?)

}

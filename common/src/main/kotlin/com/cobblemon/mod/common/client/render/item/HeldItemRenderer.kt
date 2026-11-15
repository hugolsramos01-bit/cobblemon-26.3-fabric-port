/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.api.tags.CobblemonItemTags.WEARABLE_FACE_ITEMS
import com.cobblemon.mod.common.api.tags.CobblemonItemTags.WEARABLE_HAT_ITEMS
import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f

class HeldItemRenderer {
    private val itemModelResolver = Minecraft.getInstance().itemModelResolver

    companion object {
        const val ITEM_FACE = "item_face"
        const val ITEM_HAT = "item_hat"
        const val ITEM = "item"
        private const val FULL_BRIGHT = 0x00F000F0

        private fun PoseStack.setOriginAndScale(x: Float, y: Float, z: Float, scale: Float = 1f) {
            translate(x * .0625 * scale, y * .0625 * scale, z * .0625 * scale)
            scale(scale, scale, scale)
        }
    }

    fun renderOnModel(
        item: ItemStack,
        state: PosableState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int = FULL_BRIGHT,
        frontLight: Boolean = false,
        entity: LivingEntity? = null
    ) {
        if (!item.`is`(CobblemonItemTags.HIDDEN_ITEMS)) {
            renderAtLocator(item, state, entity, poseStack, collector, light, frontLight)
        }
        for ((targetLocator, animationItem) in state.animationItems) {
            renderAtLocator(animationItem, state, entity, poseStack, collector, light, frontLight, targetLocator)
        }
    }

    private fun renderAtLocator(
        item: ItemStack,
        state: PosableState,
        entity: LivingEntity?,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        frontLight: Boolean = false,
        targetLocator: String =
            if (item.`is`(WEARABLE_FACE_ITEMS) && state.locatorStates.containsKey(ITEM_FACE)) ITEM_FACE
            else if (item.`is`(WEARABLE_HAT_ITEMS) && state.locatorStates.containsKey(ITEM_HAT)) ITEM_HAT
            else ITEM
    ) {
        if (item.isEmpty) return
        val locator = state.locatorStates[targetLocator] ?: return
        val level = entity?.level() ?: Minecraft.getInstance().level ?: return
        val owner: ItemOwner = entity ?: Minecraft.getInstance().player ?: return

        poseStack.pushPose()
        poseStack.mulPose(locator.matrix)
        val isLeft = when (state.currentModel?.getLocatorDisplayContext(targetLocator)) {
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND -> true
            else -> false
        }
        val displayContext = state.currentModel?.getLocatorDisplayContext(targetLocator)
            ?: if ((item.`is`(WEARABLE_FACE_ITEMS) && targetLocator == ITEM_FACE) ||
                (item.`is`(WEARABLE_HAT_ITEMS) && targetLocator == ITEM_HAT)
            ) ItemDisplayContext.HEAD else ItemDisplayContext.FIXED

        when (displayContext) {
            ItemDisplayContext.FIXED -> {
                poseStack.setOriginAndScale(0f, .5f, 0f, .5f)
                poseStack.rotate(Axis.XP.rotationDegrees(90.0f))
            }
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND -> {
                if (state is NPCClientDelegate) {
                    poseStack.rotate(Axis.XP.rotationDegrees(-90.0f))
                    poseStack.translate(0.025f * if (isLeft) 1 else -1, 0.0f, 0.0f)
                } else if (state is PokemonClientDelegate || state is FloatingState) {
                    poseStack.rotate(Axis.YP.rotationDegrees(-90.0f * if (isLeft) -1f else 1f))
                    poseStack.rotate(Axis.ZP.rotationDegrees(90.0f * if (isLeft) -1f else 1f))
                    poseStack.translate(0.0f, 0.0f, -0.0625f)
                }
            }
            ItemDisplayContext.HEAD -> {
                if (state is NPCClientDelegate) {
                    if (item.`is`(WEARABLE_FACE_ITEMS)) poseStack.setOriginAndScale(0f, -2f, 7.25f, 0.62f)
                    else if (item.`is`(WEARABLE_HAT_ITEMS)) poseStack.setOriginAndScale(0f, -10.25f, 0f, 0.62f)
                } else {
                    if (item.`is`(WEARABLE_FACE_ITEMS)) poseStack.setOriginAndScale(0f, 1f, 6.75f, 0.62f)
                    else if (item.`is`(WEARABLE_HAT_ITEMS)) poseStack.setOriginAndScale(0f, -6.75f, 0f, 0.62f)
                }
            }
            else -> Unit
        }

        val renderState = ItemStackRenderState()
        if (entity != null) {
            itemModelResolver.updateForLiving(renderState, item, displayContext, entity)
        } else {
            itemModelResolver.updateForTopItem(renderState, item, displayContext, level, owner, 0)
        }
        renderState.submit(poseStack, collector, if (frontLight) FULL_BRIGHT else light, OverlayTexture.NO_OVERLAY, 0)
        poseStack.popPose()
    }
}

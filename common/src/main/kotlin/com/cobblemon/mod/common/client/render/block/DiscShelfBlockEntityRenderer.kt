/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.entity.DiscShelfBlockEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.phys.Vec3

class DiscShelfBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<DiscShelfBlockEntity, DiscShelfRenderState> {
    private val unknownTexture = cobblemonResource("textures/block/disc_shelf/music_disc_unknown.png")

    override fun createRenderState() = DiscShelfRenderState()

    override fun extractRenderState(entity: DiscShelfBlockEntity, state: DiscShelfRenderState, tickDelta: Float, cameraPosition: Vec3, breakProgress: ModelFeatureRenderer.CrumblingOverlay?) {
        super.extractRenderState(entity, state, tickDelta, cameraPosition, breakProgress)
        val facing = entity.blockState.getValue(HorizontalDirectionalBlock.FACING)
        state.facing = facing
        state.lightCoords = entity.level?.let { LightCoordsUtil.getLightCoords(it, entity.blockPos.relative(facing)) }
            ?: state.lightCoords.takeIf { it != 0 } ?: LightCoordsUtil.FULL_BRIGHT
        state.slots = entity.items.mapIndexedNotNull { index, item ->
            if (item.isEmpty) null else {
                val texture = if (item.item == CobblemonItems.TECHNICAL_MACHINE) {
                    item.components.get(CobblemonItemComponents.TM_MOVE)?.move?.elementalType?.showdownId
                        ?.let { cobblemonResource("textures/block/disc_shelf/${it}_tm.png") } ?: unknownTexture
                } else getItemTexture(item)
                DiscShelfSlot(index, texture)
            }
        }
    }

    override fun submit(state: DiscShelfRenderState, pose: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        pose.pushPose()
        applyCenteredBlockRotation(pose, discShelfFrameRotation(state.facing))
        val light = state.lightCoords
        for (slot in state.slots) {
            pose.pushPose()
            applyDiscShelfSlotTransform(pose, slot.index)
            collector.submitCustomGeometry(pose, RenderTypes.text(slot.texture)) { capturedPose, consumer ->
                drawDiscShelfSlot(capturedPose, consumer, light)
            }
            pose.popPose()
        }
        pose.popPose()
    }

    private fun getItemTexture(stack: ItemStack): Identifier {
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        val resource = cobblemonResource("textures/block/disc_shelf/${id.path}.png")
        return if (Minecraft.getInstance().resourceManager.getResource(resource).isEmpty) unknownTexture else resource
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.campfirepot.CampfireBlock
import com.cobblemon.mod.common.block.campfirepot.CampfirePotBlock
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.item.CampfirePotItem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.util.ARGB
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.phys.Vec3

class CampfireBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<CampfireBlockEntity, CampfirePotRenderState> {
    private val itemModelResolver = ctx.itemModelResolver()

    override fun createRenderState() = CampfirePotRenderState()

    override fun extractRenderState(entity: CampfireBlockEntity, state: CampfirePotRenderState, tickDelta: Float, cameraPosition: Vec3, breakProgress: ModelFeatureRenderer.CrumblingOverlay?) {
        super.extractRenderState(entity, state, tickDelta, cameraPosition, breakProgress)
        state.hasPot = false
        state.potParts = emptyList()
        state.brothTints = intArrayOf()
        state.seasonings = emptyList()
        val potItem = entity.getPotItem()?.item as? CampfirePotItem ?: return
        state.hasPot = true
        val blockState = entity.blockState
        val facing = blockState.getValue(FACING)
        state.facing = facing
        state.gameTime = entity.time + tickDelta
        val yRot = (blockState.getValue(CampfireBlock.ITEM_DIRECTION).opposite.toYRot() + facing.toYRot()) % 360
        val potState = potItem.block.defaultBlockState()
            .setValue(CampfirePotBlock.OPEN, !blockState.getValue(CampfireBlock.LID))
            .setValue(FACING, Direction.fromYRot(yRot.toDouble()))
            .setValue(CampfirePotBlock.OCCUPIED, entity.getSeasonings().isNotEmpty() || entity.getIngredients().isNotEmpty())
        state.potParts = extractCampfirePotParts(Minecraft.getInstance().modelManager.blockStateModelSet, potState)
        // The old renderModel applied broth color only to quads with a tint index.
        state.brothTints = campfireBrothTints(state.potParts, entity.brothColor)
        state.seasonings = entity.getSeasonings().map { stack ->
            ItemStackRenderState().also {
                itemModelResolver.updateForTopItem(it, stack, ItemDisplayContext.GROUND, entity.level, null, 0)
            }
        }
    }

    override fun submit(state: CampfirePotRenderState, pose: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        if (!state.hasPot) return
        pose.pushPose()
        applyCenteredBlockRotation(pose, campfireFrameRotation(state.facing))
        pose.pushPose()
        pose.translate(0.0, 0.4375, 0.0)
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), state.potParts, state.brothTints, state.lightCoords, OverlayTexture.NO_OVERLAY, -1)
        pose.popPose()
        state.seasonings.forEachIndexed { index, item ->
            pose.pushPose()
            applyCampfireSeasoningTransform(pose, state.gameTime, index, state.seasonings.size)
            item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)
            pose.popPose()
        }
        pose.popPose()
    }
}

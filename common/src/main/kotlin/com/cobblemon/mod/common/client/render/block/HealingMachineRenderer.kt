/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3

class HealingMachineRenderer<T: BlockEntity>(ctx: BlockEntityRendererProvider.Context): BlockEntityRenderer<T, HealingMachineRenderState> {
    private val itemModelResolver = ctx.itemModelResolver()

    companion object {
        private val offsets = listOf(
            0.2 to 0.385,
            -0.2 to 0.385,
            0.2 to 0.0,
            -0.2 to 0.0,
            0.2 to -0.385,
            -0.2 to -0.385
        )
    }

    override fun createRenderState() = HealingMachineRenderState()

    override fun extractRenderState(blockEntity: T, state: HealingMachineRenderState, tickDelta: Float, cameraPosition: Vec3, breakProgress: ModelFeatureRenderer.CrumblingOverlay?) {
        super.extractRenderState(blockEntity, state, tickDelta, cameraPosition, breakProgress)
        state.pokeBalls = emptyList()
        if (blockEntity !is HealingMachineBlockEntity) return
        val blockState = if (blockEntity.level != null) blockEntity.blockState
            else CobblemonBlocks.HEALING_MACHINE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH)
        state.yRotation = blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot()
        state.pokeBalls = blockEntity.pokeBalls().map { (index, pokeBall) ->
            val itemState = ItemStackRenderState()
            itemModelResolver.updateForTopItem(itemState, pokeBall.stack(), ItemDisplayContext.GROUND, blockEntity.level, null, 0)
            index to itemState
        }
    }

    override fun submit(state: HealingMachineRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        poseStack.pushPose()
        applyHealingMachineFrame(poseStack, state.yRotation)
        for ((index, itemState) in state.pokeBalls) {
            poseStack.pushPose()
            val offset = offsets[index]
            poseStack.translate(offset.first, 0.4, offset.second)
            itemState.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)
            poseStack.popPose()
        }
        poseStack.popPose()
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.FossilAnalyzerBlockEntity
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure
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
import net.minecraft.world.phys.Vec3

class FossilAnalyzerRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<FossilAnalyzerBlockEntity, FossilAnalyzerRenderState> {
    private val itemModelResolver = ctx.itemModelResolver()

    override fun createRenderState() = FossilAnalyzerRenderState()

    override fun extractRenderState(entity: FossilAnalyzerBlockEntity, state: FossilAnalyzerRenderState, tickDelta: Float, cameraPosition: Vec3, breakProgress: ModelFeatureRenderer.CrumblingOverlay?) {
        super.extractRenderState(entity, state, tickDelta, cameraPosition, breakProgress)
        state.fossils = emptyList()
        val blockState = if (entity.level != null) entity.blockState
            else CobblemonBlocks.FOSSIL_ANALYZER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH)
        // A disconnected analyzer has no multiblock inventory to display.
        val structure = entity.multiblockStructure ?: return
        val direction = blockState.getValue(HorizontalDirectionalBlock.FACING)
        state.yRotation = direction.toYRot() + if (direction == Direction.WEST || direction == Direction.EAST) 180F else 0F
        state.directionOffset = when (direction) {
            Direction.NORTH -> Vec3(0.0, 0.0, 0.05)
            Direction.SOUTH -> Vec3(0.0, 0.0, -0.05)
            Direction.EAST -> Vec3(-0.05, 0.0, 0.0)
            Direction.WEST -> Vec3(0.05, 0.0, 0.0)
            else -> Vec3.ZERO
        }
        state.fossils = (structure as FossilMultiblockStructure).fossilInventory.map { stack ->
            ItemStackRenderState().also { itemState ->
                itemModelResolver.updateForTopItem(itemState, stack, ItemDisplayContext.NONE, entity.level, null, 0)
            }
        }
    }

    override fun submit(state: FossilAnalyzerRenderState, matrices: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        for ((index, itemState) in state.fossils.withIndex()) {
            matrices.pushPose()
            applyFossilItemTransform(matrices, state.yRotation, state.directionOffset, index)
            itemState.submit(matrices, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)
            matrices.popPose()
        }
    }
}

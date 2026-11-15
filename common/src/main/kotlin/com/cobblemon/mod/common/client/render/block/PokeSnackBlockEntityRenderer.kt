/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.berry.Berries
import com.cobblemon.mod.common.block.PokeSnackBlock
import com.cobblemon.mod.common.block.PokeSnackBlock.Companion.CANDLE
import com.cobblemon.mod.common.block.PokeSnackBlock.Companion.getCandleById
import com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.setPosition
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.geometry.Axis
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.model.BlockDisplayContext
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT
import net.minecraft.world.phys.Vec3

class PokeSnackRenderState : net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState() {
    val candle = BlockModelRenderState()
    var hasCandle = false
    var facing = Direction.NORTH
    var berryGeometry = FrozenModelGeometry(emptyList())
}

open class PokeSnackBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<PokeSnackBlockEntity, PokeSnackRenderState> {

    private val blockModelResolver = ctx.blockModelResolver()

    override fun createRenderState() = PokeSnackRenderState()

    override fun extractRenderState(
        blockEntity: PokeSnackBlockEntity,
        state: PokeSnackRenderState,
        tickDelta: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, tickDelta, cameraPosition, breakProgress)
        val blockState = blockEntity.blockState
        state.facing = blockState.getValue(FACING)

        val candleId = blockState.getValue(CANDLE)
        state.hasCandle = candleId > 0
        state.candle.clear()
        if (state.hasCandle) {
            val candleState = getCandleById(candleId).defaultBlockState().setValue(LIT, blockState.getValue(LIT))
            blockModelResolver.update(state.candle, candleState, BlockDisplayContext.create())
            state.candle.blockLightCoords = state.lightCoords
        }

        val ingredients = (blockEntity.ingredientComponent?.ingredientIds?.take(3) ?: emptyList())
            .mapNotNull { Berries.getByIdentifier(it) }
        val geometry = ModelGeometryCapture()
        val pose = PoseStack()
        for ((index, berry) in ingredients.withIndex()) {
            val model = BerryModelRepository.modelOf(berry.fruitModelIdentifier) ?: continue
            val placement = berry.pokeSnackPositionings[if (ingredients.size == 2) 0 else index]
            val position = placement.position
            val rotation = placement.rotation

            val oldPose = model.storePose()
            try {
                if (ingredients.size == 1 && !state.hasCandle) {
                    model.setPosition(Axis.X_AXIS.ordinal, 8F)
                    model.setPosition(Axis.Y_AXIS.ordinal, position.y.toFloat())
                    model.setPosition(Axis.Z_AXIS.ordinal, 8F)
                    model.setRotation(Math.PI.toFloat(), 0F, 0F)
                } else {
                    model.setPosition(Axis.X_AXIS.ordinal, position.x.toFloat())
                    model.setPosition(Axis.Y_AXIS.ordinal, position.y.toFloat())
                    val z = if (ingredients.size == 2 && index != 0) 16F - position.z.toFloat() else position.z.toFloat()
                    model.setPosition(Axis.Z_AXIS.ordinal, z)
                    val rotateX = if (ingredients.size == 2 && index != 0) 180.0 + (180.0 - rotation.x) else rotation.x
                    model.setRotation(
                        Math.toRadians(rotateX).toFloat(),
                        Math.toRadians(rotation.y).toFloat(),
                        Math.toRadians(rotation.z).toFloat()
                    )
                }
                model.render(pose, geometry, state.lightCoords, OverlayTexture.NO_OVERLAY)
            } finally {
                model.loadPose(oldPose)
            }
        }
        state.berryGeometry = geometry.freeze()
    }

    override fun submit(
        state: PokeSnackRenderState,
        pose: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        val rotation = when (state.facing) {
            Direction.NORTH -> 180f
            Direction.SOUTH -> 0f
            Direction.WEST -> 90f
            Direction.EAST -> -90f
            else -> 0f
        }

        pose.pushPose()
        pose.translate(0.5, 0.5, 0.5)
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation).get(org.joml.Matrix4f()))
        pose.translate(-0.5, -0.5, -0.5)

        if (state.hasCandle) {
            pose.pushPose()
            pose.translate(0.0, PokeSnackBlock.CAKE_HEIGHT, 0.0)
            state.candle.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, -1)
            pose.popPose()
        }

        if (!state.berryGeometry.isEmpty()) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)) { capturedPose, consumer ->
                state.berryGeometry.draw(capturedPose, consumer)
            }
        }
        pose.popPose()
    }
}

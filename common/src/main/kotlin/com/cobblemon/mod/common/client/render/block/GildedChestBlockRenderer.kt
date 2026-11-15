/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.GildedChestBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.blockentity.BlockEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3

class GildedChestRenderState : BlockEntityRenderState() {
    lateinit var texture: net.minecraft.resources.Identifier
    lateinit var geometry: FrozenModelGeometry
    lateinit var repeatedGeometry: FrozenModelGeometry
    var layers: List<FrozenModelLayer> = emptyList()
    var yaw = 0F
}

class GildedChestBlockRenderer(context: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<GildedChestBlockEntity, GildedChestRenderState> {

    private val renderContext = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.BLOCK)
        it.put(RenderContext.DO_QUIRKS, true)
    }

    override fun createRenderState() = GildedChestRenderState()

    override fun extractRenderState(
        entity: GildedChestBlockEntity,
        state: GildedChestRenderState,
        tickDelta: Float,
        cameraPosition: Vec3,
        breakProgress: net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(entity, state, tickDelta, cameraPosition, breakProgress)

        val aspects = emptySet<String>()
        val posableState = entity.posableState
        posableState.currentAspects = aspects
        posableState.updatePartialTicks(tickDelta)
        val poserId = entity.type.poserId
        val model = VaryingModelRepository.getPoser(poserId, posableState) as BlockEntityModel
        model.context = renderContext
        val texture = VaryingModelRepository.getTexture(poserId, posableState)
        posableState.currentModel = model
        renderContext.put(RenderContext.ASPECTS, aspects)
        renderContext.put(RenderContext.TEXTURE, texture)
        renderContext.put(RenderContext.SPECIES, poserId)
        renderContext.put(RenderContext.POSABLE_STATE, posableState)

        model.applyAnimations(
            entity = null,
            state = posableState,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = posableState.animationSeconds * 20
        )

        state.texture = texture
        state.yaw = entity.blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()
        val capturePose = PoseStack()
        val baseCapture = ModelGeometryCapture()
        model.render(renderContext, capturePose, baseCapture, state.lightCoords, OverlayTexture.NO_OVERLAY, -1)

        val layerCapture = LayerGeometryCapture()
        model.setLayerContext(layerCapture, posableState, VaryingModelRepository.getLayers(poserId, posableState))
        val repeatedCapture = ModelGeometryCapture()
        model.render(renderContext, capturePose, repeatedCapture, state.lightCoords, OverlayTexture.NO_OVERLAY, -1)
        model.resetLayerContext()

        state.geometry = baseCapture.freeze()
        state.repeatedGeometry = repeatedCapture.freeze()
        state.layers = layerCapture.freeze()
        model.setDefault()
    }

    override fun submit(state: GildedChestRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        poseStack.pushPose()
        poseStack.rotate(Axis.ZP.rotationDegrees(180F))
        poseStack.translate(-0.5, 0.0, 0.5)
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw))
        poseStack.rotate(Axis.YP.rotationDegrees(180F))

        val base = state.geometry
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture)) { pose, consumer ->
            base.draw(pose, consumer)
        }
        val repeated = state.repeatedGeometry
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture)) { pose, consumer ->
            repeated.draw(pose, consumer)
        }
        state.layers.forEach { it.submit(poseStack, collector) }

        poseStack.popPose()
    }
}

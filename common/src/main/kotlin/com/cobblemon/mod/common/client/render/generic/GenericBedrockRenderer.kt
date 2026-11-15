/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.generic

import com.cobblemon.mod.common.client.entity.GenericBedrockClientDelegate
import com.cobblemon.mod.common.client.render.block.FrozenModelGeometry
import com.cobblemon.mod.common.client.render.block.FrozenModelLayer
import com.cobblemon.mod.common.client.render.block.LayerGeometryCapture
import com.cobblemon.mod.common.client.render.block.ModelGeometryCapture
import com.cobblemon.mod.common.client.render.models.blockbench.generic.PosableGenericEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier

class GenericBedrockRenderState : EntityRenderState() {
    lateinit var texture: Identifier
    var geometry = FrozenModelGeometry(emptyList())
    var layers: List<FrozenModelLayer> = emptyList()
    var modelScale = 1F
    var yaw = 0F
}

class GenericBedrockRenderer(context: EntityRendererProvider.Context) : EntityRenderer<GenericBedrockEntity, GenericBedrockRenderState>(context) {
    val model = PosableGenericEntityModel()

    override fun createRenderState() = GenericBedrockRenderState()

    override fun extractRenderState(entity: GenericBedrockEntity, state: GenericBedrockRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)

        val delegateState = entity.delegate as GenericBedrockClientDelegate
        delegateState.currentAspects = entity.aspects
        val model = VaryingModelRepository.getPoser(entity.category, delegateState)
        this.model.posableModel = model
        model.context = this.model.context
        this.model.setupEntityTypeContext(entity)
        state.texture = VaryingModelRepository.getTexture(entity.category, delegateState)
        state.yaw = entity.getYRot(partialTicks)
        state.modelScale = entity.scale

        delegateState.updatePartialTicks(partialTicks)
        val layerCapture = LayerGeometryCapture()
        model.setLayerContext(layerCapture, delegateState, VaryingModelRepository.getLayers(entity.category, delegateState))
        model.applyAnimations(entity, delegateState, 0F, 0F, entity.tickCount + partialTicks, 0F, 0F)
        val capture = ModelGeometryCapture()
        this.model.renderPosable(entity, PoseStack(), capture, state.lightCoords, OverlayTexture.NO_OVERLAY, -0x1)
        state.geometry = capture.freeze()
        state.layers = layerCapture.freeze()

        model.green = 1F
        model.blue = 1F
        model.red = 1F

        model.resetLayerContext()
    }

    override fun submit(state: GenericBedrockRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        if (state.isInvisible) return

        poseStack.pushPose()
        poseStack.scale(1.0F, -1.0F, 1.0F)
        poseStack.scale(state.modelScale, state.modelScale, state.modelScale)
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw))
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture)) { pose, consumer ->
            state.geometry.draw(pose, consumer)
        }
        state.layers.forEach { it.submit(poseStack, collector) }
        poseStack.popPose()
        super.submit(state, poseStack, collector, cameraState)
    }
}

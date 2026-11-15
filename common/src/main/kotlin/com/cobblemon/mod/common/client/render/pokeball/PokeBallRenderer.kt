/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokeball

import com.cobblemon.mod.common.client.entity.EmptyPokeBallClientDelegate
import com.cobblemon.mod.common.client.render.block.FrozenModelGeometry
import com.cobblemon.mod.common.client.render.block.FrozenModelLayer
import com.cobblemon.mod.common.client.render.block.LayerGeometryCapture
import com.cobblemon.mod.common.client.render.block.ModelGeometryCapture
import com.cobblemon.mod.common.client.render.models.blockbench.pokeball.PosablePokeBallModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier

class PokeBallRenderState : EntityRenderState() {
    lateinit var texture: Identifier
    lateinit var geometry: FrozenModelGeometry
    var layers: List<FrozenModelLayer> = emptyList()
    var yaw = 0F
}

class PokeBallRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<EmptyPokeBallEntity, PokeBallRenderState>(context) {

    private val model = PosablePokeBallModel()

    override fun createRenderState() = PokeBallRenderState()

    override fun extractRenderState(entity: EmptyPokeBallEntity, renderState: PokeBallRenderState, partialTicks: Float) {
        super.extractRenderState(entity, renderState, partialTicks)
        renderState.yaw = entity.getYRot(partialTicks)

        val state = entity.delegate as EmptyPokeBallClientDelegate
        state.currentAspects = entity.aspects
        model.context.put(RenderContext.POSABLE_STATE, state)
        model.context.put(RenderContext.ASPECTS, entity.aspects)
        model.setupEntityTypeContext(entity)
        model.context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)

        val posableModel = VaryingModelRepository.getPoser(entity.pokeBall.name, state)
        model.posableModel = posableModel
        posableModel.context = model.context
        state.updatePartialTicks(partialTicks)
        posableModel.applyAnimations(
            entity = entity,
            state = state,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = entity.tickCount + partialTicks
        )

        val baseCapture = ModelGeometryCapture()
        val layerCapture = LayerGeometryCapture()
        val capturePose = PoseStack()
        posableModel.setLayerContext(layerCapture, state, VaryingModelRepository.getLayers(entity.pokeBall.name, state))
        model.renderPosable(entity, capturePose, baseCapture, renderState.lightCoords, OverlayTexture.NO_OVERLAY, -1)
        posableModel.resetLayerContext()

        renderState.texture = VaryingModelRepository.getTexture(entity.pokeBall.name, state)
        renderState.geometry = baseCapture.freeze()
        renderState.layers = layerCapture.freeze()

        posableModel.red = 1F
        posableModel.green = 1F
        posableModel.blue = 1F
        posableModel.setDefault()
    }

    override fun submit(
        state: PokeBallRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        poseStack.pushPose()
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw))
        poseStack.scale(0.7F, -0.7F, -0.7F)

        val geometry = state.geometry
        if (!geometry.isEmpty()) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture)) { pose, consumer ->
                geometry.draw(pose, consumer)
            }
        }
        state.layers.forEach { it.submit(poseStack, collector) }

        poseStack.popPose()
        super.submit(state, poseStack, collector, cameraState)
    }
}

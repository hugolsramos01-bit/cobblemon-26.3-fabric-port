/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.npc

import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.render.block.FrozenModelGeometry
import com.cobblemon.mod.common.client.render.block.FrozenModelLayer
import com.cobblemon.mod.common.client.render.block.LayerGeometryCapture
import com.cobblemon.mod.common.client.render.block.ModelGeometryCapture
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.PosableEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.npc.PosableNPCModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import kotlin.math.min

class NPCRenderState : LivingEntityRenderState() {
    var entity: NPCEntity? = null
    var texture: Identifier? = null
    var geometry = FrozenModelGeometry(emptyList())
    var layers: List<FrozenModelLayer> = emptyList()
    var modelScale = 1F
    var hideNameTag = false
    var npcShadowRadius = 0F
}

class NPCRenderer(context: Context) : LivingEntityRenderer<NPCEntity, NPCRenderState, PosableEntityModel<NPCEntity>>(context, PosableNPCModel(), 0.5f) {
    private val heldItemRenderer = HeldItemRenderer()

    override fun createRenderState() = NPCRenderState()

    override fun getTextureLocation(state: NPCRenderState): Identifier =
        state.texture ?: Identifier.withDefaultNamespace("missingno")

    override fun getShadowRadius(state: NPCRenderState): Float = state.npcShadowRadius * state.scale

    override fun extractRenderState(entity: NPCEntity, state: NPCRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.entity = entity
        state.hideNameTag = entity.hideNameTag
        state.modelScale = entity.npc.modelScale

        val delegate = entity.delegate as NPCClientDelegate
        delegate.currentAspects = entity.aspects
        val poser = VaryingModelRepository.getPoser(entity.resourceIdentifier, delegate)
        model.posableModel = poser
        poser.context = model.context
        model.setupEntityTypeContext(entity)
        state.texture = VaryingModelRepository.getTexture(entity.resourceIdentifier, delegate)
        model.context.put(RenderContext.TEXTURE, state.texture!!)
        state.npcShadowRadius = min(
            entity.boundingBox.maxX - entity.boundingBox.minX,
            entity.boundingBox.maxZ - entity.boundingBox.minZ
        ).toFloat() / 1.5F

        delegate.updatePartialTicks(partialTicks)
        val layerCapture = LayerGeometryCapture()
        poser.setLayerContext(layerCapture, delegate, VaryingModelRepository.getLayers(entity.resourceIdentifier, delegate))
        try {
            model.setupAnim(state)
            val geometryCapture = ModelGeometryCapture()
            model.renderPosable(entity, PoseStack(), geometryCapture, state.lightCoords, OverlayTexture.NO_OVERLAY, -1)
            state.geometry = geometryCapture.freeze()
            state.layers = layerCapture.freeze()
        } finally {
            poser.red = 1F
            poser.green = 1F
            poser.blue = 1F
            poser.resetLayerContext()
        }
    }

    override fun submit(state: NPCRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        val entity = state.entity ?: return
        val delegate = entity.delegate as NPCClientDelegate

        poseStack.pushPose()
        poseStack.scale(state.scale, state.scale, state.scale)
        setupRotations(state, poseStack, state.bodyRot, state.scale)
        poseStack.scale(-1F, -1F, 1F)
        poseStack.scale(entity.renderScale, entity.renderScale, entity.renderScale)
        poseStack.scale(state.modelScale, state.modelScale, state.modelScale)
        poseStack.translate(0.0, -1.5, 0.0)

        val renderType = getRenderType(state, isBodyVisible(state), state.isInvisible, state.appearsGlowing())
        if (renderType != null && !state.geometry.isEmpty()) {
            val geometry = state.geometry
            collector.submitCustomGeometry(poseStack, renderType) { pose, consumer -> geometry.draw(pose, consumer) }
        }
        state.layers.forEach { it.submit(poseStack, collector) }

        poseStack.popPose()

        // The locator matrix already contains the NPC's orientation and scale.
        // Render the held item outside the body transform to avoid applying both twice.
        if (entity.deathTime < 1) {
            heldItemRenderer.renderOnModel(entity.mainHandItem, delegate, poseStack, collector, state.lightCoords, false, entity)
        }

        super.submit(state, poseStack, collector, cameraState)
    }

    override fun shouldShowName(entity: NPCEntity, distance: Double): Boolean =
        !entity.hideNameTag && super.shouldShowName(entity, distance)
}

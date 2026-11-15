/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.boat

import com.cobblemon.mod.common.entity.boat.CobblemonBoatEntity
import com.cobblemon.mod.common.entity.boat.CobblemonBoatType
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.`object`.boat.BoatModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.BoatRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Unit as MinecraftUnit
import org.joml.Quaternionf

class CobblemonBoatRenderer(
    ctx: EntityRendererProvider.Context,
    private val hasChest: Boolean
) : EntityRenderer<CobblemonBoatEntity, CobblemonBoatRenderer.RenderState>(ctx) {

    class RenderState : BoatRenderState() {
        lateinit var texture: Identifier
        lateinit var model: BoatModel
    }

    private val boatModels = hashMapOf<CobblemonBoatType, Pair<Identifier, BoatModel>>()
    private val waterPatchModel = Model.Simple(ctx.bakeLayer(ModelLayers.BOAT_WATER_PATCH)) { _ ->
        RenderTypes.waterMask()
    }

    init {
        this.shadowRadius = 0.8F
        CobblemonBoatType.entries.forEach { type ->
            boatModels[type] = generateTextureIdentifier(type, hasChest) to generateBoatModel(ctx, type, hasChest)
        }
    }

    override fun createRenderState() = RenderState()

    override fun extractRenderState(entity: CobblemonBoatEntity, state: RenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.yRot = entity.getYRot(partialTicks)
        state.hurtTime = entity.hurtTime - partialTicks
        state.hurtDir = entity.hurtDir
        state.damageTime = (entity.damage - partialTicks).coerceAtLeast(0F)
        state.bubbleAngle = entity.getBubbleAngle(partialTicks)
        state.isUnderWater = entity.isUnderWater
        val (texture, model) = boatModels.getValue(entity.boatType)
        state.texture = texture
        state.model = model
    }

    override fun submit(
        state: RenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        poseStack.pushPose()
        poseStack.translate(0F, 0.375F, 0F)
        poseStack.rotate(Axis.YP.rotationDegrees(180F - state.yRot))
        if (state.hurtTime > 0F) {
            val hurtAngle = Mth.sin(state.hurtTime.toDouble()).toFloat() *
                state.hurtTime * state.damageTime / 10F * state.hurtDir
            poseStack.rotate(Axis.XP.rotationDegrees(hurtAngle))
        }
        if (!state.isUnderWater && !Mth.equal(state.bubbleAngle, 0F)) {
            poseStack.rotate(
                Quaternionf().setAngleAxis(state.bubbleAngle * 0.017453292F, 1F, 0F, 1F)
            )
        }
        poseStack.scale(-1F, -1F, 1F)
        poseStack.rotate(Axis.YP.rotationDegrees(90F))
        collector.submitModel(
            state.model,
            state,
            poseStack,
            state.texture,
            state.lightCoords,
            OverlayTexture.NO_OVERLAY,
            state.outlineColor
        )
        if (!state.isUnderWater) {
            collector.submitModel(
                waterPatchModel,
                MinecraftUnit.INSTANCE,
                poseStack,
                RenderTypes.waterMask(),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
            )
        }
        poseStack.popPose()
        super.submit(state, poseStack, collector, cameraState)
    }

    companion object {

        private fun generateTextureIdentifier(type: CobblemonBoatType, hasChest: Boolean): Identifier {
            val boatSubPath = if (hasChest) "chest_boat" else "boat"
            return cobblemonResource("textures/entity/$boatSubPath/${type.name.lowercase()}.png")
        }

        private fun generateBoatModel(
            ctx: EntityRendererProvider.Context,
            type: CobblemonBoatType,
            hasChest: Boolean
        ): BoatModel {
            val modelLayer = createBoatModelLayer(type, hasChest)
            return BoatModel(ctx.bakeLayer(modelLayer))
        }

        internal fun createBoatModelLayer(type: CobblemonBoatType, hasChest: Boolean): ModelLayerLocation {
            val boatSubPath = if (hasChest) "chest_boat" else "boat"
            return ModelLayerLocation(cobblemonResource("$boatSubPath/${type.name.lowercase()}"), "main")
        }
    }
}

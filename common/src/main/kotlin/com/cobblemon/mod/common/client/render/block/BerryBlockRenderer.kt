/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.mulch.MulchVariant
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import com.cobblemon.mod.common.client.render.atlas.CobblemonAtlases.BERRY_SPRITE_ATLAS
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.QuadInstance
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class BerryBlockRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<BerryBlockEntity, BerryBlockEntityRenderState> {
    private data class BerryPlacement(val model: ModelPart, val position: Vec3, val rotation: Vec3)
    private val berryGeometryCache = object : LinkedHashMap<List<BerryPlacement>, FrozenModelGeometry>(256, 0.75F, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<BerryPlacement>, FrozenModelGeometry>?) = size > 512
    }
    private val mulchModels = mapOf(
        MulchVariant.COARSE to CobblemonBakingOverrides.COARSE_MULCH,
        MulchVariant.GROWTH to CobblemonBakingOverrides.GROWTH_MULCH,
        MulchVariant.HUMID to CobblemonBakingOverrides.HUMID_MULCH,
        MulchVariant.LOAMY to CobblemonBakingOverrides.LOAMY_MULCH,
        MulchVariant.PEAT to CobblemonBakingOverrides.PEAT_MULCH,
        MulchVariant.RICH to CobblemonBakingOverrides.RICH_MULCH,
        MulchVariant.SANDY to CobblemonBakingOverrides.SANDY_MULCH,
        MulchVariant.SURPRISE to CobblemonBakingOverrides.SURPRISE_MULCH
    )

    override fun createRenderState() = BerryBlockEntityRenderState()

    override fun extractRenderState(entity: BerryBlockEntity, state: BerryBlockEntityRenderState, tickDelta: Float, cameraPosition: Vec3, breakProgress: ModelFeatureRenderer.CrumblingOverlay?) {
        super.extractRenderState(entity, state, tickDelta, cameraPosition, breakProgress)
        val placements = mutableListOf<BerryPlacement>()
        val age = entity.blockState.getValue(BerryBlock.AGE)
        if (age == 0) {
            entity.berry()?.let { berry ->
                BerryModelRepository.modelOf(berry.fruitModelIdentifier)?.let { model ->
                    val placement = berry.stageOnePositioning
                    placements.add(BerryPlacement(model, placement.position, placement.rotation))
                }
            }
        } else if (age > BerryBlock.MATURE_AGE) {
            val isFlower = age == BerryBlock.FLOWER_AGE
            for ((berry, growth) in entity.berryAndGrowthPoint()) {
                val id = if (isFlower) berry.flowerModelIdentifier else berry.fruitModelIdentifier
                val model = BerryModelRepository.modelOf(id) ?: continue
                placements.add(BerryPlacement(model, growth.position, growth.rotation))
            }
        }
        state.berryGeometry = berryGeometryCache.getOrPut(placements.toList()) {
            val capture = ModelGeometryCapture()
            val capturePose = PoseStack()
            for (placement in placements) {
                captureBerryModel(placement.model, placement.position, placement.rotation, capturePose, capture, 0)
            }
            capture.freeze()
        }
        val pose = PoseStack()
        val mulch = ModelGeometryCapture()
        mulchModels[entity.mulchVariant]?.getModel()?.getQuads(entity.blockState, null, entity.level?.random)?.forEach { quad ->
            val instance = QuadInstance().also { it.setColor(-1); it.setLightCoords(state.lightCoords); it.setOverlayCoords(OverlayTexture.NO_OVERLAY) }
            mulch.putBakedQuad(pose.last(), quad, instance)
        }
        state.mulchGeometry = mulch.freeze()
    }

    override fun submit(state: BerryBlockEntityRenderState, pose: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        // Preserve the old enlarged berry culling box using the frame's native camera frustum.
        if (!cameraState.cullFrustum.isVisible(AABB.ofSize(Vec3.atLowerCornerOf(state.blockPos), 2.0, 4.0, 2.0))) return
        val berries = state.berryGeometry
        val light = state.lightCoords
        if (!berries.isEmpty()) collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BERRY_SPRITE_ATLAS.location())) { capturedPose, consumer ->
            berries.draw(capturedPose, consumer, light)
        }
        val mulch = state.mulchGeometry
        if (!mulch.isEmpty()) collector.submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock()) { capturedPose, consumer ->
            mulch.draw(capturedPose, consumer)
        }
    }
}

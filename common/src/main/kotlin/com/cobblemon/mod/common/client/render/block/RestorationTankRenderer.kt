/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.RestorationTankBlockEntity
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure
import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import com.cobblemon.mod.common.client.render.models.blockbench.fossil.FossilModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.WaveFunction
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.parabolaFunction
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.rerange
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.timeDilate
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.QuadInstance
import com.mojang.math.Axis
import kotlin.math.pow
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.core.Direction
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.phys.Vec3

class RestorationTankRenderState : BlockEntityRenderState() {
    var connector = FrozenModelGeometry(emptyList())
    var fluid = FrozenModelGeometry(emptyList())
    var fetusPasses: List<FrozenModelLayer> = emptyList()
    var connectorDirection: Direction? = null
}

class RestorationTankRenderer(ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<RestorationTankBlockEntity, RestorationTankRenderState> {

    private val context = RenderContext().also {
        it.put(RenderContext.DO_QUIRKS, true)
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE)
    }

    override fun createRenderState() = RestorationTankRenderState()

    override fun extractRenderState(
        entity: RestorationTankBlockEntity,
        state: RestorationTankRenderState,
        tickDelta: Float,
        cameraPosition: Vec3,
        breakProgress: net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(entity, state, tickDelta, cameraPosition, breakProgress)
        state.connectorDirection = null
        state.connector = FrozenModelGeometry(emptyList())
        state.fluid = FrozenModelGeometry(emptyList())
        state.fetusPasses = emptyList()

        val structure = entity.multiblockStructure as? FossilMultiblockStructure ?: return
        val level = entity.level ?: return
        val connectorDirection = structure.tankConnectorDirection
        state.connectorDirection = connectorDirection

        if (connectorDirection != null) {
            val connectorCapture = ModelGeometryCapture()
            val capturePose = PoseStack()
            CONNECTOR_MODEL.getQuads(entity.blockState, null, level.random).forEach { quad ->
                val instance = QuadInstance().also {
                    it.setColor(0xFFBFBFBF.toInt())
                    it.setLightCoords(state.lightCoords)
                    it.setOverlayCoords(OverlayTexture.NO_OVERLAY)
                }
                connectorCapture.putBakedQuad(capturePose.last(), quad, instance)
            }
            state.connector = connectorCapture.freeze()
        }

        val fillLevel = structure.fillLevel
        if (fillLevel == 0 && !structure.hasCreatedPokemon) return

        val fluidIndex = when {
            structure.isRunning() -> 8
            structure.hasCreatedPokemon -> 7
            else -> fillLevel.coerceAtMost(FLUID_MODELS.size - 1) - 1
        }
        if (fluidIndex in FLUID_MODELS.indices) {
            val fluidCapture = ModelGeometryCapture()
            val capturePose = PoseStack()
            FLUID_MODELS[fluidIndex].getQuads(entity.blockState, null, level.random).forEach { quad ->
                val instance = QuadInstance().also {
                    it.setColor(0xFFBFBFBF.toInt())
                    it.setLightCoords(state.lightCoords)
                    it.setOverlayCoords(OverlayTexture.NO_OVERLAY)
                }
                fluidCapture.putBakedQuad(capturePose.last(), quad, instance)
            }
            state.fluid = fluidCapture.freeze()
        }

        if (structure.isRunning() || structure.hasCreatedPokemon) {
            state.fetusPasses = extractFetus(entity, structure, tickDelta, state.lightCoords)
        }
    }

    override fun submit(
        state: RestorationTankRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        val direction = state.connectorDirection
        if (direction != null) {
            poseStack.pushPose()
            poseStack.translate(0.5, 0.0, 0.5)
            when (direction) {
                Direction.NORTH -> Unit
                Direction.EAST -> poseStack.rotate(Axis.YP.rotationDegrees(270f))
                Direction.SOUTH -> poseStack.rotate(Axis.YP.rotationDegrees(180f))
                Direction.WEST -> poseStack.rotate(Axis.YP.rotationDegrees(90f))
                else -> Unit
            }
            poseStack.translate(-0.5, 0.0, -0.5)
            if (!state.connector.isEmpty()) {
                val geometry = state.connector
                collector.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock()) { pose, consumer -> geometry.draw(pose, consumer) }
            }
            if (!state.fluid.isEmpty()) {
                val geometry = state.fluid
                collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock()) { pose, consumer -> geometry.draw(pose, consumer) }
            }
            poseStack.popPose()
        } else if (!state.fluid.isEmpty()) {
            val geometry = state.fluid
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock()) { pose, consumer -> geometry.draw(pose, consumer) }
        }
        state.fetusPasses.forEach { it.submit(poseStack, collector) }
    }

    private fun extractFetus(
        entity: RestorationTankBlockEntity,
        structure: FossilMultiblockStructure,
        tickDelta: Float,
        light: Int
    ): List<FrozenModelLayer> {
        val fossil = structure.resultingFossil ?: return emptyList()
        val tankState = entity.level?.getBlockState(entity.blockPos) ?: return emptyList()
        if (tankState.block != CobblemonBlocks.RESTORATION_TANK) return emptyList()
        val tankDirection = tankState.getValue(HorizontalDirectionalBlock.FACING)
        val connectionDirection = structure.tankConnectorDirection

        val posableState = structure.fossilState
        posableState.updatePartialTicks(tickDelta)
        posableState.currentAspects = fossil.result?.aspects ?: emptySet()

        var fetusId = fossil.identifier.withPath("${fossil.identifier.path}_fetus")
        val progress = (1 - structure.timeRemaining / FossilMultiblockStructure.TIME_TO_TAKE.toFloat()).coerceIn(0F, 1F)
        val fetusModel = VaryingModelRepository.getPoser(fetusId, posableState) as? FossilModel ?: run {
            fetusId = cobblemonResource("substitute_fetus")
            VaryingModelRepository.getPoser(fetusId, posableState) as FossilModel
        }

        val stages = listOf(
            EMBRYO_IDENTIFIERS[0] to EMBRYO_CURVE_1(progress),
            EMBRYO_IDENTIFIERS[1] to EMBRYO_CURVE_2(progress),
            EMBRYO_IDENTIFIERS[2] to EMBRYO_CURVE_3(progress),
            fetusId to FOSSIL_CURVE(progress)
        )
        val passes = mutableListOf<FrozenModelLayer>()

        stages.forEach { (identifier, scaleAtStage) ->
            if (scaleAtStage <= 0F) return@forEach
            val model = VaryingModelRepository.getPoser(identifier, posableState) as FossilModel
            val texture = VaryingModelRepository.getTexture(identifier, posableState)
            posableState.currentModel = model
            posableState.setPoseToFirstSuitable()

            val scale = if (structure.timeRemaining == 0) model.maxScale else scaleAtStage * model.maxScale
            val capturePose = PoseStack()
            capturePose.translate(0.5, 1.0 + fetusModel.yTranslation, 0.5)
            capturePose.rotate(Axis.ZP.rotationDegrees(180F))
            if (connectionDirection != null) {
                when {
                    tankDirection.getCounterClockWise(Direction.Axis.Y) == connectionDirection -> capturePose.rotate(Axis.YP.rotationDegrees(-90F))
                    tankDirection == connectionDirection -> capturePose.rotate(Axis.YP.rotationDegrees(180F))
                    tankDirection.opposite != connectionDirection -> capturePose.rotate(Axis.YP.rotationDegrees(90F))
                }
            }

            model.context = context
            context.put(RenderContext.TEXTURE, texture)
            context.put(RenderContext.SPECIES, fossil.identifier)
            context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE)
            context.put(RenderContext.POSABLE_STATE, posableState)

            capturePose.pushPose()
            capturePose.scale(scale, scale, scale)
            capturePose.translate(0.0, model.yGrowthPoint.toDouble(), 0.0)
            model.applyAnimations(
                entity = null,
                state = posableState,
                headYaw = 0F,
                headPitch = 0F,
                limbSwing = 0F,
                limbSwingAmount = 0F,
                ageInTicks = posableState.animationSeconds * 20
            )

            val baseCapture = ModelGeometryCapture()
            model.render(context, capturePose, baseCapture, light, OverlayTexture.NO_OVERLAY, -1)
            passes += FrozenModelLayer(RenderTypes.entityCutout(texture), baseCapture.freeze())

            val layerCapture = LayerGeometryCapture()
            model.setLayerContext(layerCapture, posableState, VaryingModelRepository.getLayers(fetusId, posableState))
            val layeredCapture = ModelGeometryCapture()
            model.render(context, capturePose, layeredCapture, light, OverlayTexture.NO_OVERLAY, -1)
            model.resetLayerContext()
            passes += FrozenModelLayer(RenderTypes.entityCutout(texture), layeredCapture.freeze())
            passes += layerCapture.freeze()

            model.setDefault()
        }
        return passes
    }

    companion object {
        private val FLUID_MODELS = listOf(
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_1.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_2.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_3.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_4.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_5.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_6.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_7.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_CHUNKED_8.getModel(),
            CobblemonBakingOverrides.RESTORATION_TANK_FLUID_BUBBLING.getModel()
        )

        private val CONNECTOR_MODEL = CobblemonBakingOverrides.RESTORATION_TANK_CONNECTOR.getModel()

        private val EMBRYO_IDENTIFIERS = listOf(
            cobblemonResource("embryo_stage1"),
            cobblemonResource("embryo_stage2"),
            cobblemonResource("embryo_stage3")
        )

        private val EMBRYO_CURVE_1 = parabolaFunction(peak = 0.5F, period = 1F).rerange(0.0F, 0.8F).timeDilate(2.5F)
        private val EMBRYO_CURVE_2 = parabolaFunction(peak = 0.9F, period = 1F).rerange(0.2F, 1.2F).timeDilate(2.5F)
        private val EMBRYO_CURVE_3 = parabolaFunction(peak = 1F, period = 1F).rerange(0.6F, 1.4F).timeDilate(2.5F)
        private val FOSSIL_CURVE: WaveFunction = { t: Float -> -0.4F * (t - 2.5F).pow(2) + 1F }.timeDilate(2.5F)
    }
}

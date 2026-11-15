/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.client.render.block.FrozenModelGeometry
import com.cobblemon.mod.common.client.render.block.FrozenModelLayer
import com.cobblemon.mod.common.client.render.block.LayerGeometryCapture
import com.cobblemon.mod.common.client.render.block.ModelGeometryCapture
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f

class PokemonItemRenderer : CobblemonBuiltinItemRenderer {
    val context = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.PROFILE)
        it.put(RenderContext.DO_QUIRKS, false)
    }

    override fun extract(stack: ItemStack, mode: ItemDisplayContext): Any? {
        val pokemonItem = stack.item as? PokemonItem ?: return null
        val (species, aspects) = pokemonItem.getSpeciesAndAspects(stack) ?: return null
        val transformations = positions[mode] ?: return null
        val state = FloatingState()
        state.currentAspects = aspects
        val model = VaryingModelRepository.getPoser(species.resourceIdentifier, state)
        model.context = context
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.PROFILE)
        context.put(RenderContext.SPECIES, species.resourceIdentifier)
        context.put(RenderContext.ASPECTS, aspects)
        context.put(RenderContext.POSABLE_STATE, state)
        state.currentModel = model

        state.setPoseToFirstSuitable(PoseType.PORTRAIT)
        model.applyAnimations(null, state, 0F, 0F, 0F, 0F, 0F)

        val tint = pokemonItem.tint(stack)
        val tintRed = (tint.x * 255).toInt()
        val tintGreen = (tint.y * 255).toInt()
        val tintBlue = (tint.z * 255).toInt()
        val tintAlpha = (tint.w * 255).toInt()
        val color = (tintAlpha shl 24) or (tintRed shl 16) or (tintGreen shl 8) or tintBlue

        val baseCapture = ModelGeometryCapture()
        val layerCapture = LayerGeometryCapture()
        model.withLayerContext(layerCapture, state, VaryingModelRepository.getLayers(species.resourceIdentifier, state)) {
            model.render(context, PoseStack(), baseCapture, 0, OverlayTexture.NO_OVERLAY, color)
        }
        val baseGeometry = baseCapture.freeze()
        val layers = layerCapture.freeze()
        val texture = VaryingModelRepository.getTexture(species.resourceIdentifier, state)
        val profileScale = model.profileScale
        model.setDefault()

        return PokemonRenderData(
            transformations.translation.x,
            transformations.translation.y,
            transformations.translation.z,
            transformations.scale.x,
            transformations.scale.y,
            transformations.scale.z,
            transformations.rotation.x,
            transformations.rotation.y,
            transformations.rotation.z,
            profileScale,
            texture,
            baseGeometry,
            layers,
            stack.get(DataComponents.ENCHANTMENTS)?.isEmpty == false,
            mode == ItemDisplayContext.GUI
        )
    }

    override fun submitExtracted(
        state: Any,
        matrices: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        overlay: Int,
        hasFoil: Boolean,
        outlineColor: Int
    ) {
        val data = state as? PokemonRenderData ?: return
        val isGui = data.modeIsGui
        if (isGui) Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_FLAT)

        matrices.pushPose()
        matrices.scale(data.scaleX, data.scaleY, data.scaleZ)
        matrices.translate(data.translateX, data.translateY, data.translateZ)
        matrices.scale(data.profileScale, data.profileScale, 0.15F)
        val rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(data.rotationX, data.rotationY, data.rotationZ))
        matrices.rotate(rotation)

        val packedLight = if (isGui) GUI_LIGHT else light
        collector.submitCustomGeometry(matrices, RenderTypes.entityCutout(data.texture)) { pose, consumer ->
            data.baseGeometry.draw(pose, LightOverrideVertexConsumer(consumer, packedLight))
        }
        if (hasFoil || data.wasEnchanted) {
            collector.submitCustomGeometry(matrices, RenderTypes.itemCutoutGlint(data.texture)) { pose, consumer ->
                data.baseGeometry.draw(pose, LightOverrideVertexConsumer(consumer, packedLight))
            }
        }
        data.layers.forEach { layer ->
            collector.submitCustomGeometry(matrices, layer.renderType) { pose, consumer ->
                layer.geometry.draw(pose, LightOverrideVertexConsumer(consumer, packedLight))
            }
        }
        matrices.popPose()

    }

    private data class PokemonRenderData(
        val translateX: Double,
        val translateY: Double,
        val translateZ: Double,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
        val rotationX: Float,
        val rotationY: Float,
        val rotationZ: Float,
        val profileScale: Float,
        val texture: Identifier,
        val baseGeometry: FrozenModelGeometry,
        val layers: List<FrozenModelLayer>,
        val wasEnchanted: Boolean,
        val modeIsGui: Boolean = false
    )

    companion object {
        private const val GUI_LIGHT = (13 shl 20) or (13 shl 4)
        val positions: MutableMap<ItemDisplayContext, Transformations> = mutableMapOf()

        init {
            positions[ItemDisplayContext.GUI] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(1.0, -0.2, -0.5),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -3.5F),
                PokemonItemRenderer().Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.FIXED] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(1.0, -0.5, -5.0),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.1F),
                PokemonItemRenderer().Transformation(0F, 35F - 180F, 0F)
            )
            positions[ItemDisplayContext.FIRST_PERSON_RIGHT_HAND] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(2.75, 0.0, 1.0),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.5F),
                PokemonItemRenderer().Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.FIRST_PERSON_LEFT_HAND] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(-0.75, 0.0, 1.0),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.5F),
                PokemonItemRenderer().Transformation(0F, -35F, 0F)
            )
            positions[ItemDisplayContext.THIRD_PERSON_RIGHT_HAND] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(1.0, -1.0, -1.25),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.5F),
                PokemonItemRenderer().Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.THIRD_PERSON_LEFT_HAND] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(1.0, -1.0, -1.25),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.5F),
                PokemonItemRenderer().Transformation(0F, -35F, 0F)
            )
            positions[ItemDisplayContext.GROUND] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(1.0, -0.5, -1.0),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.5F),
                PokemonItemRenderer().Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.HEAD] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(1.0, -3.5, 3.0),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.5F),
                PokemonItemRenderer().Transformation(0F, 215F, 0F)
            )
            positions[ItemDisplayContext.NONE] = PokemonItemRenderer().Transformations(
                PokemonItemRenderer().Transformation(0.0, 0.0, 0.0),
                PokemonItemRenderer().Transformation(0.5F, -0.5F, -0.5F),
                PokemonItemRenderer().Transformation(0F, 0F, 0F)
            )
        }
    }

    inner class Transformations(val translation: Transformation<Double>, val scale: Transformation<Float>, val rotation: Transformation<Float>)
    inner class Transformation<T>(val x: T, val y: T, val z: T)
}

/** Keeps the extraction-time light out of captured vertices so submit-time lighting stays current. */
private class LightOverrideVertexConsumer(private val delegate: VertexConsumer, private val packedLight: Int) : VertexConsumer by delegate {
    override fun addVertex(pose: PoseStack.Pose, x: Float, y: Float, z: Float): VertexConsumer {
        delegate.addVertex(pose, x, y, z)
        return this
    }

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.addVertex(x, y, z)
        return this
    }

    override fun setColor(color: Int): VertexConsumer { delegate.setColor(color); return this }
    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer { delegate.setColor(red, green, blue, alpha); return this }
    override fun setUv(u: Float, v: Float): VertexConsumer { delegate.setUv(u, v); return this }
    override fun setOverlay(overlay: Int): VertexConsumer { delegate.setOverlay(overlay); return this }
    override fun setLight(light: Int): VertexConsumer { delegate.setLight(packedLight); return this }
    override fun setUv3(u: Float, v: Float): VertexConsumer { delegate.setUv3(u, v); return this }
    override fun setNormal(pose: PoseStack.Pose, x: Float, y: Float, z: Float): VertexConsumer { delegate.setNormal(pose, x, y, z); return this }
    override fun setLineWidth(width: Float): VertexConsumer { delegate.setLineWidth(width); return this }
}

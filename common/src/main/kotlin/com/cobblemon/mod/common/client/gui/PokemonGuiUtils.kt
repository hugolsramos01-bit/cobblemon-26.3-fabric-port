/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.api.gui.renderSprite
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay.Companion.PORTRAIT_DIAMETER
import com.cobblemon.mod.common.client.render.block.LayerGeometryCapture
import com.cobblemon.mod.common.client.render.block.ModelGeometryCapture
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.mixin.accessor.GuiGraphicsExtractorAccessor
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.toEulerXYZDegrees
import com.cobblemon.mod.common.util.toHex
import com.mojang.math.Axis
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import kotlin.math.atan

enum class ProfileTransformType {
    PROFILE,
    NONE,
    POKEDEX,
    SUMMARY,
}

fun drawProfilePokemon(
    renderablePokemon: RenderablePokemon,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    profileTransformType: ProfileTransformType = ProfileTransformType.PROFILE,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13,
    includeHeldItems: Boolean = false
) = drawProfilePokemon(
    species = renderablePokemon.species.resourceIdentifier,
    context = context,
    rotation = rotation,
    poseType = poseType,
    state = state.also { it.currentAspects = renderablePokemon.aspects },
    partialTicks = partialTicks,
    scale = scale,
    profileTransformType = profileTransformType,
    applyBaseScale = applyBaseScale,
    r = r,
    g = g,
    b = b,
    a = a,
    headYaw = headYaw,
    headPitch = headPitch,
    blockLight = blockLight,
    heldItem = renderablePokemon.heldItem.takeIf { includeHeldItems }
)

fun drawProfilePokemon(
    species: Identifier,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    profileTransformType: ProfileTransformType = ProfileTransformType.PROFILE,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13,
    heldItem: ItemStack? = null
) {
    val sprite = VaryingModelRepository.getSprite(species, state, SpriteType.PROFILE)

    if (sprite == null) {

        val model = VaryingModelRepository.getPoser(species, state)
        val texture = VaryingModelRepository.getTexture(species, state)

        val renderContext = RenderContext()
        model.context = renderContext
        VaryingModelRepository.getTextureNoSubstitute(species, state).let { renderContext.put(RenderContext.TEXTURE, it) }
        val baseScale = PokemonSpecies.getByIdentifier(species)!!.getForm(state.currentAspects).baseScale
        renderContext.put(RenderContext.SCALE, baseScale)
        renderContext.put(RenderContext.SPECIES, species)
        renderContext.put(RenderContext.ASPECTS, state.currentAspects)
        renderContext.put(RenderContext.RENDER_STATE, RenderContext.RenderState.PROFILE)
        renderContext.put(RenderContext.POSABLE_STATE, state)
        renderContext.put(RenderContext.DO_QUIRKS, doQuirks)

        state.currentModel = model

        state.setPoseToFirstSuitable(poseType)
        state.updatePartialTicks(partialTicks)

        val modelPose = PoseStack()
        modelPose.scale(scale, scale, -scale)

        if (profileTransformType == ProfileTransformType.NONE) {
            modelPose.translate(0F, 0F, -4.0F)
            if (applyBaseScale) {
                modelPose.scale(baseScale, baseScale, 1 / baseScale)
            }
        } else {
            val (profileScale, profileTranslation) = when (profileTransformType) {
                ProfileTransformType.POKEDEX -> (model.profilePokedexScale ?: model.profileScale) to
                    (model.profilePokedexTranslation ?: model.profileTranslation)
                ProfileTransformType.SUMMARY -> (model.profileSummaryScale ?: model.profileScale) to
                    (model.profileSummaryTranslation ?: model.profileTranslation)
                else -> model.profileScale to model.profileTranslation
            }

            modelPose.translate(
                profileTranslation.x,
                profileTranslation.y + 1.5 * profileScale,
                profileTranslation.z - 4.0
            )
            modelPose.scale(profileScale, profileScale, 1 / profileScale)
        }

        modelPose.rotate(rotation)

        model.applyAnimations(null, state, 0F, 0F, 0F, headYaw, headPitch)

        val baseCapture = ModelGeometryCapture()
        val layerCapture = LayerGeometryCapture()
        val packedLight = blockLight shl 4

        val colour = toHex(r, g, b, a)
        model.withLayerContext(layerCapture, state, VaryingModelRepository.getLayers(species, state)) {
            model.render(renderContext, modelPose, baseCapture, packedLight, OverlayTexture.NO_OVERLAY, colour)
        }
        val geometry = baseCapture.freeze()
        val layers = layerCapture.freeze()
        val items = heldItem?.let { capturePosableGuiItems(it, state, modelPose) }.orEmpty()
        model.setDefault()
        (context as GuiGraphicsExtractorAccessor).getGuiRenderState().submitPosableModel(
            texture = texture,
            geometry = geometry,
            layers = layers,
            items = items,
            scissorArea = context.getScissorArea(),
            pose = context.pose()
        )
    } else {
        renderSprite(context, sprite)
    }
}

/** Captures an NPC/profile model into the same deferred GUI path as Pokémon portraits. */
fun drawPosableGuiProfile(
    context: GuiGraphicsExtractor,
    identifier: Identifier,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F
) {
    val sprite = VaryingModelRepository.getSprite(identifier, state, SpriteType.PROFILE)
    if (sprite != null) {
        renderSprite(context, sprite)
        return
    }

    val model = VaryingModelRepository.getPoser(identifier, state)
    val texture = VaryingModelRepository.getTexture(identifier, state)
    val renderContext = RenderContext()
    model.context = renderContext
    VaryingModelRepository.getTextureNoSubstitute(identifier, state).let { renderContext.put(RenderContext.TEXTURE, it) }
    renderContext.put(RenderContext.SCALE, 1F)
    renderContext.put(RenderContext.SPECIES, identifier)
    renderContext.put(RenderContext.ASPECTS, state.currentAspects)
    renderContext.put(RenderContext.POSABLE_STATE, state)
    state.currentModel = model
    state.setPoseToFirstSuitable(PoseType.PORTRAIT)
    state.updatePartialTicks(partialTicks)
    model.applyAnimations(null, state, 0F, 0F, 0F, 0F, 0F)

    val pose = PoseStack()
    pose.scale(scale, scale, -scale)
    pose.translate(model.profileTranslation.x, model.profileTranslation.y + 1.5 * model.profileScale, model.profileTranslation.z - 4.0)
    pose.scale(model.profileScale, model.profileScale, 1 / model.profileScale)
    pose.rotate(Axis.YP.rotationDegrees(-32F))
    pose.rotate(Axis.XP.rotationDegrees(5F))

    val baseCapture = ModelGeometryCapture()
    val layerCapture = LayerGeometryCapture()
    model.withLayerContext(layerCapture, state, VaryingModelRepository.getLayers(identifier, state)) {
        model.render(renderContext, pose, baseCapture, (11 shl 4) or (7 shl 20), OverlayTexture.NO_OVERLAY, -1)
    }
    val geometry = baseCapture.freeze()
    val layers = layerCapture.freeze()
    model.setDefault()
    (context as GuiGraphicsExtractorAccessor).getGuiRenderState().submitPosableModel(
        texture = texture,
        geometry = geometry,
        layers = layers,
        scissorArea = context.getScissorArea(),
        pose = context.pose()
    )
}

private fun capturePosableGuiItems(mainItem: ItemStack, state: PosableState, modelPose: PoseStack): List<PosableGuiItemRenderState> {
    val minecraft = Minecraft.getInstance()
    val level = minecraft.level ?: return emptyList()
    val owner = minecraft.player ?: return emptyList()
    val capturedItems = mutableListOf<PosableGuiItemRenderState>()

    fun captureItem(item: ItemStack, targetLocator: String? = null) {
        if (item.isEmpty) return
        val locatorName = targetLocator ?: when {
            item.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS) && state.locatorStates.containsKey(HeldItemRenderer.ITEM_FACE) -> HeldItemRenderer.ITEM_FACE
            item.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && state.locatorStates.containsKey(HeldItemRenderer.ITEM_HAT) -> HeldItemRenderer.ITEM_HAT
            else -> HeldItemRenderer.ITEM
        }
        val locator = state.locatorStates[locatorName] ?: return
        val configuredContext = state.currentModel?.getLocatorDisplayContext(locatorName)
        val isLeft = configuredContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        val displayContext = configuredContext ?: if (
            (item.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS) && locatorName == HeldItemRenderer.ITEM_FACE) ||
            (item.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && locatorName == HeldItemRenderer.ITEM_HAT)
        ) ItemDisplayContext.HEAD else ItemDisplayContext.FIXED

        val pose = PoseStack()
        pose.mulPose(modelPose.last().pose())
        pose.mulPose(locator.matrix)
        when (displayContext) {
            ItemDisplayContext.FIXED -> {
                pose.translate(0.0, 0.5 * 0.0625 * 0.5, 0.0)
                pose.scale(0.5F, 0.5F, 0.5F)
                pose.rotate(Axis.XP.rotationDegrees(90F))
            }
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND -> {
                pose.rotate(Axis.YP.rotationDegrees(-90F * if (isLeft) -1F else 1F))
                pose.rotate(Axis.ZP.rotationDegrees(90F * if (isLeft) -1F else 1F))
                pose.translate(0.0, 0.0, -0.0625)
            }
            ItemDisplayContext.HEAD -> {
                if (item.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)) {
                    pose.translate(0.0, 1F * 0.0625 * 0.62, 6.75F * 0.0625 * 0.62)
                    pose.scale(0.62F, 0.62F, 0.62F)
                } else if (item.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS)) {
                    pose.translate(0.0, -6.75F * 0.0625 * 0.62, 0.0)
                    pose.scale(0.62F, 0.62F, 0.62F)
                }
            }
            else -> Unit
        }

        val itemRenderState = ItemStackRenderState()
        minecraft.itemModelResolver.updateForTopItem(itemRenderState, item, displayContext, level, owner, 0)
        capturedItems.add(PosableGuiItemRenderState(itemRenderState, Matrix4f(pose.last().pose()), 0x00F000F0))
    }

    if (!mainItem.`is`(CobblemonItemTags.HIDDEN_ITEMS)) {
        captureItem(mainItem)
    }
    for ((targetLocator, animationItem) in state.animationItems) {
        captureItem(animationItem, targetLocator)
    }

    return capturedItems
}

const val HEAD_YAW_FACTOR = 40f
const val HEAD_PITCH_FACTOR = 20f
const val SMOOTHING_FACTOR = 0.05f

fun calculateHeadYawAndPitch(
    xCoordinate: Float,
    yCoordinate: Float,
    rotation: Quaternionf,
    mouseX: Int,
    mouseY: Int,
    currentYaw: Float,
    currentPitch: Float,
    resetToCenter: Boolean,
): Pair<Float, Float> {
    val horizontalAngle = atan(((xCoordinate - mouseX) / 40.0f).toDouble()).toFloat()
    val verticalAngle = atan(((yCoordinate - mouseY) / 40.0f).toDouble()).toFloat()

    val eulerXYZRotationDegrees = rotation.toEulerXYZDegrees()
    val targetYaw = if (!resetToCenter) horizontalAngle * HEAD_YAW_FACTOR - eulerXYZRotationDegrees.y else 0f
    val targetPitch = if (!resetToCenter) -verticalAngle * HEAD_PITCH_FACTOR - eulerXYZRotationDegrees.x else 0f

    val newYaw = Mth.rotLerp(SMOOTHING_FACTOR, currentYaw, targetYaw)
    val newPitch = Mth.rotLerp(SMOOTHING_FACTOR, currentPitch, targetPitch)

    return Pair(newYaw, newPitch)
}

/** Captures a dialogue/profile portrait into immutable data consumed by the GUI PIP renderer. */
fun drawPosableGuiPortrait(
    context: GuiGraphicsExtractor,
    identifier: Identifier,
    contextScale: Float = 1F,
    state: PosableState,
    reversed: Boolean = false,
    partialTicks: Float,
    doQuirks: Boolean = true,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    scale: Float = 13F,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    val sprite = VaryingModelRepository.getSprite(identifier, state, SpriteType.PORTRAIT)
    if (sprite != null) {
        renderSprite(context, sprite)
        return
    }

    val model = VaryingModelRepository.getPoser(identifier, state)
    val texture = VaryingModelRepository.getTexture(identifier, state)
    val renderContext = RenderContext()
    model.context = renderContext
    VaryingModelRepository.getTextureNoSubstitute(identifier, state).let { renderContext.put(RenderContext.TEXTURE, it) }
    renderContext.put(RenderContext.SCALE, contextScale)
    renderContext.put(RenderContext.SPECIES, identifier)
    renderContext.put(RenderContext.ASPECTS, state.currentAspects)
    renderContext.put(RenderContext.POSABLE_STATE, state)
    renderContext.put(RenderContext.DO_QUIRKS, doQuirks)
    state.currentModel = model

    val originalPose = state.currentPose
    state.setPoseToFirstSuitable(PoseType.PORTRAIT)
    state.updatePartialTicks(partialTicks)
    model.applyAnimations(null, state, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch)
    originalPose?.let { state.setPose(it) }

    val pose = PoseStack()
    pose.translate(0.0, PORTRAIT_DIAMETER.toDouble() + 2.0, 0.0)
    pose.scale(scale, scale, -scale)
    pose.translate(0.0, -PORTRAIT_DIAMETER / 18.0, 0.0)
    val portraitScale = model.portraitScale
    pose.translate(
        model.portraitTranslation.x * if (reversed) -1F else 1F,
        model.portraitTranslation.y + 1.5 * portraitScale,
        model.portraitTranslation.z - 4
    )
    pose.scale(portraitScale, portraitScale, 1 / portraitScale)
    pose.rotate(Axis.YP.rotationDegrees(-32F * if (reversed) -1F else 1F))
    pose.rotate(Axis.XP.rotationDegrees(5F))

    val baseCapture = ModelGeometryCapture()
    val layerCapture = LayerGeometryCapture()
    val packedLight = (11 shl 4) or (7 shl 20)
    val colour = toHex(r, g, b, a)
    model.withLayerContext(layerCapture, state, VaryingModelRepository.getLayers(identifier, state)) {
        model.render(renderContext, pose, baseCapture, packedLight, OverlayTexture.NO_OVERLAY, colour)
    }
    val geometry = baseCapture.freeze()
    val layers = layerCapture.freeze()
    model.setDefault()
    (context as GuiGraphicsExtractorAccessor).getGuiRenderState().submitPosableModel(
        texture = texture,
        geometry = geometry,
        layers = layers,
        scissorArea = context.getScissorArea(),
        pose = context.pose()
    )
}

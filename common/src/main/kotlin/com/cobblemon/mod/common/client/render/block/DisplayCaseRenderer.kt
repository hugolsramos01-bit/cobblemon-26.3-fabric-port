/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.block.DisplayCaseBlock
import com.cobblemon.mod.common.block.entity.DisplayCaseBlockEntity
import com.cobblemon.mod.common.item.PokedexItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import org.joml.Matrix4f
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.item.BannerItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.phys.Vec3

internal enum class DisplayCasePositioning(
    val scaleX: Float, val scaleY: Float, val scaleZ: Float,
    val transX: Float, val transY: Float, val transZ: Float,
    val rotY: Float = 0f
) {
    POKE_BALL(1f, 1f, 1f, 0f, 0.04f, 0f),
    POKEDEX(1f, 1f, 1f, 0f, 0.04f, 0f, 180f),
    BLOCK_MODEL(1f, 1f, 1f, 0f, -0.15f, 0f),
    ITEM_MODEL(1f, 1f, 1f, 0f, 0.04f, 0f),
    BED(1f, 1f, 1f, 0f, -0.02f, 0f),
    BANNER(1f, 1f, 1f, 0f, -0.02f, 0f, 180f),
    MOB_HEAD(1f, 1f, 1f, 0f, -0.025f, 0f, 180f),
    SHIELD(1f, 1f, 1f, 0f, -0.045f, 0f, 180f),
    PASTURE(1f, 1f, 1f, 0f, 0.0375f, 0f),
    COIN_POUCH(1f, 1f, 1f, 0f, 0.415f, 0f)
}

class DisplayCaseRenderState : BlockEntityRenderState() {
    val item = ItemStackRenderState()
    var yaw = 0f
    internal var positioning = DisplayCasePositioning.ITEM_MODEL
}

class DisplayCaseRenderer(ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<DisplayCaseBlockEntity, DisplayCaseRenderState> {

    private val itemModelResolver = ctx.itemModelResolver()
    private val coinPouchStack: ItemStack by lazy {
        ItemStack(CobblemonItems.RELIC_COIN_POUCH).also {
            it.set(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(1f), emptyList(), emptyList(), emptyList()))
        }
    }

    override fun createRenderState() = DisplayCaseRenderState()

    override fun extractRenderState(
        entity: DisplayCaseBlockEntity,
        state: DisplayCaseRenderState,
        tickDelta: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(entity, state, tickDelta, cameraPosition, breakProgress)
        val level = entity.level ?: return
        val stack = if (entity.getStack().`is`(CobblemonItems.RELIC_COIN_POUCH)) coinPouchStack else entity.getStack()
        state.item.clear()
        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND, level, null, 0)
        state.positioning = getPositioningType(stack, state.item.usesBlockLight())
        val direction = entity.blockState.getValue(DisplayCaseBlock.ITEM_DIRECTION)
        state.yaw = if (state.positioning == DisplayCasePositioning.ITEM_MODEL) direction.opposite.toYRot() else direction.toYRot()
        state.lightCoords = LightCoordsUtil.getLightCoords(level, entity.blockPos)
    }

    override fun submit(
        state: DisplayCaseRenderState,
        pose: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        pose.pushPose()
        pose.translate(0.5, 0.4, 0.5)
        pose.scale(state.positioning.scaleX, state.positioning.scaleY, state.positioning.scaleZ)
        pose.translate(state.positioning.transX, state.positioning.transY, state.positioning.transZ)
        pose.mulPose(Axis.YP.rotationDegrees(-state.yaw).get(Matrix4f()))
        pose.mulPose(Axis.YP.rotationDegrees(state.positioning.rotY).get(Matrix4f()))
        state.item.submit(pose, collector, state.lightCoords, 0, 0)
        pose.popPose()
    }

    companion object {
        private val mobHeads = listOf<Item>(
            Items.SKELETON_SKULL,
            Items.WITHER_SKELETON_SKULL,
            Items.ZOMBIE_HEAD,
            Items.PIGLIN_HEAD,
            Items.PLAYER_HEAD,
            Items.DRAGON_HEAD,
            Items.CREEPER_HEAD
        )

        private fun getPositioningType(stack: ItemStack, usesBlockLight: Boolean) = when {
            mobHeads.contains(stack.item) -> DisplayCasePositioning.MOB_HEAD
            stack.item is BlockItem && (stack.item as BlockItem).block is BedBlock -> DisplayCasePositioning.BED
            stack.item is BlockItem && (stack.item as BlockItem).block is net.minecraft.world.level.block.BannerBlock -> DisplayCasePositioning.BANNER
            stack.`is`(CobblemonItemTags.POKE_BALLS) -> DisplayCasePositioning.POKE_BALL
            stack.`is`(CobblemonItemTags.PLAQUES) -> DisplayCasePositioning.ITEM_MODEL
            stack.item is PokedexItem -> DisplayCasePositioning.POKEDEX
            stack.item == CobblemonItems.RELIC_COIN_POUCH -> DisplayCasePositioning.COIN_POUCH
            stack.item == CobblemonItems.PASTURE -> DisplayCasePositioning.PASTURE
            stack.item == Items.SHIELD -> DisplayCasePositioning.SHIELD
            stack.item == Items.DECORATED_POT -> DisplayCasePositioning.MOB_HEAD
            usesBlockLight -> DisplayCasePositioning.BLOCK_MODEL
            else -> DisplayCasePositioning.ITEM_MODEL
        }
    }
}

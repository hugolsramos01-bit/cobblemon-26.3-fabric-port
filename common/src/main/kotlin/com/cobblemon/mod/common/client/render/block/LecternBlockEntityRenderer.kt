/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.LecternBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

class CobblemonLecternRenderState : BlockEntityRenderState() {
    val item = ItemStackRenderState()
    var yaw = 0f
}

class LecternBlockEntityRenderer(ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<LecternBlockEntity, CobblemonLecternRenderState> {

    private val itemModelResolver = ctx.itemModelResolver()

    override fun createRenderState() = CobblemonLecternRenderState()

    override fun extractRenderState(
        blockEntity: LecternBlockEntity,
        state: CobblemonLecternRenderState,
        tickDelta: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, tickDelta, cameraPosition, breakProgress)
        val level = blockEntity.level ?: return
        val stack = blockEntity.getItemStack()
        if (stack.isEmpty) return

        state.yaw = blockEntity.blockState.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot()
        state.item.clear()
        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND, level, null, 0)
    }

    override fun submit(
        state: CobblemonLecternRenderState,
        pose: PoseStack,
        collector: SubmitNodeCollector,
        cameraState: CameraRenderState
    ) {
        if (state.item.isEmpty) return

        pose.pushPose()
        pose.translate(0.5, 1.17, 0.5)
        pose.mulPose(Axis.YP.rotationDegrees(-state.yaw).get(Matrix4f()))
        pose.mulPose(Axis.XP.rotationDegrees(22.5f).get(Matrix4f()))
        pose.translate(0.0, 0.0, 0.13)
        state.item.submit(pose, collector, state.lightCoords, 0, 0)
        pose.popPose()
    }
}

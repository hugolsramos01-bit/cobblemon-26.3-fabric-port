/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.client.render.player.RecallArmAnimationTracker
import com.cobblemon.mod.common.client.render.player.RecallArmAnimationTracker.RecallAnim
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class PlayerRecallBallRenderer(
    renderLayerParent: RenderLayerParent<AvatarRenderState, PlayerModel>
) : RenderLayer<AvatarRenderState, PlayerModel>(renderLayerParent) {

    override fun submit(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        state: AvatarRenderState,
        limbSwing: Float,
        limbSwingAmount: Float
    ) {
        val mc = Minecraft.getInstance()
        val player = mc.level?.getEntity(state.id) ?: return
        val anim = RecallArmAnimationTracker.get(player.uuid) ?: return
        if (player === mc.player && mc.options.cameraType.isFirstPerson) return

        matrixStack.pushPose()
        getParentModel().translateToHand(state, HumanoidArm.RIGHT, matrixStack)
        matrixStack.rotate(Axis.XP.rotationDegrees(-90F))
        matrixStack.rotate(Axis.YP.rotationDegrees(180F))
        matrixStack.translate(0.0625, 0.125, -0.625)
        matrixStack.scale(0.54F, -0.54F, -0.54F)
        matrixStack.translate(0.0, 0.5, 0.0)
        matrixStack.rotate(Axis.YP.rotationDegrees(180F))

        RecallArmAnimationTracker.renderBall(anim, matrixStack, collector, packedLight, mc.deltaTracker.realtimeDeltaTicks)

        anim.ballState.locatorStates["beam"]?.let { beam ->
            matrixStack.pushPose()
            matrixStack.mulPose(beam.matrix)
            val offset = matrixStack.last().pose().transformPosition(Vector3f(0F, 0.18F, -0.3F))
            matrixStack.popPose()
            anim.beamOrigin = mc.gameRenderer.mainCamera().position().add(Vec3(offset))
        }
        matrixStack.popPose()
    }
}

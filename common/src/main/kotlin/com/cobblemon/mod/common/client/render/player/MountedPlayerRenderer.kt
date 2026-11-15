/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.riding.RidingAnimation
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.AnimationUtils
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.world.phys.Vec3

/**
 * @author landonjw
 */
object MountedPlayerRenderer {
    private var applyingRegularAnimation = false

    private fun ModelPart.snapshotPose() = PartPose(x, y, z, xRot, yRot, zRot, xScale, yScale, zScale)

    @JvmField
    var shouldApplyRootAnimation = false

    @JvmField
    val relevantPartsByName = mutableMapOf(
        "body" to ModelPart(emptyList(), emptyMap()),
    )

    val defaultAnimations = listOf(
        RidingAnimation(
            fileName = "player",
            animationName = "animation.player.default_ride_pose"
        )
    )

    fun animate(
        model: HumanoidModel<*>,
        pokemonEntity: PokemonEntity,
        player: AbstractClientPlayer,
        headYaw: Float,
        headPitch: Float,
        ageInTicks: Float,
        limbSwing: Float,
        limbSwingAmount: Float
    ) {
        // Re-running setupAnim below can re-enter the mixin that calls this method.
        if (applyingRegularAnimation) return

        // We need to reset the root because it's a synthetic part, Minecraft won't do this for us.
        val root = relevantPartsByName["body"] ?: return
        root.x = 0F
        root.y = 0F
        root.z = 0F
        root.xRot = 0F
        root.yRot = 0F
        root.zRot = 0F

        val seat = pokemonEntity.getSeatForPassenger(player) ?: return
        val animations = seat.poseAnimations
            ?.firstOrNull { it.poseTypes.isEmpty() || it.poseTypes.contains(pokemonEntity.getCurrentPoseType()) }?.animations
            ?: defaultAnimations
        val state = pokemonEntity.delegate as PokemonClientDelegate

        relevantPartsByName.values.forEach {
            it.xRot = 0F
            it.yRot = 0F
            it.zRot = 0F
        }

        state.runtime.environment.setSimpleVariable("age_in_ticks", DoubleValue(ageInTicks.toDouble()))
        state.runtime.environment.setSimpleVariable("limb_swing", DoubleValue(limbSwing.toDouble()))
        state.runtime.environment.setSimpleVariable("limb_swing_amount", DoubleValue(limbSwingAmount.toDouble()))
        state.runtime.environment.setSimpleVariable("head_yaw", DoubleValue(headYaw.toDouble()))
        state.runtime.environment.setSimpleVariable("head_pitch", DoubleValue(headPitch.toDouble()))

        // TODO add the q.player reference, requires a cached thing or something on the client idk

        for (animation in animations) {
            val bedrockAnimation = BedrockAnimationRepository.getAnimationOrNull(animation.fileName, animation.animationName) ?: continue
            bedrockAnimation.run(
                context = null,
                relevantPartsByName = relevantPartsByName,
                rootPart = null,
                state = state,
                animationSeconds = state.animationSeconds,
                limbSwing = limbSwing,
                limbSwingAmount = limbSwingAmount,
                ageInTicks = ageInTicks,
                intensity = 1F
            )
        }

        applyRegularAnimation(model, player, ageInTicks)
    }

    /**
     * This does what Minecraft would typically do for various additional body part
     * rendering thingies. It's largely copy paste.
     */
    fun applyRegularAnimation(
        model: HumanoidModel<*>,
        player: AbstractClientPlayer,
        ageInTicks: Float
    ) {
        val minecraft = Minecraft.getInstance()
        val partialTick = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val renderState = minecraft.entityRenderDispatcher.extractEntity(player, partialTick) as? AvatarRenderState ?: return
        @Suppress("UNCHECKED_CAST")
        val humanoidModel = model as HumanoidModel<HumanoidRenderState>

        // setupAnim owns the private arm-pose and attack-animation methods in 26.3.
        // Keep the riding animation on the other parts while using vanilla's state pipeline for arms.
        val originalPoses = humanoidModel.allParts().associateWith { it.snapshotPose() }
        val originalRightArm = humanoidModel.rightArm.snapshotPose()
        val originalLeftArm = humanoidModel.leftArm.snapshotPose()

        applyingRegularAnimation = true
        try {
            humanoidModel.setupAnim(renderState)

            val posedRightArm = humanoidModel.rightArm.snapshotPose()
            val posedLeftArm = humanoidModel.leftArm.snapshotPose()
            originalPoses.forEach { (part, pose) ->
                if (part !== humanoidModel.rightArm && part !== humanoidModel.leftArm) {
                    part.loadPose(pose)
                }
            }

            if (renderState.rightArmPose == HumanoidModel.ArmPose.EMPTY && renderState.swingAnimation <= 0F) {
                humanoidModel.rightArm.loadPose(originalRightArm)
                AnimationUtils.bobModelPart(humanoidModel.rightArm, ageInTicks, 1.0F)
            } else {
                humanoidModel.rightArm.loadPose(posedRightArm)
            }

            if (renderState.leftArmPose == HumanoidModel.ArmPose.EMPTY && renderState.swingAnimation <= 0F) {
                humanoidModel.leftArm.loadPose(originalLeftArm)
                AnimationUtils.bobModelPart(humanoidModel.leftArm, ageInTicks, -1.0F)
            } else {
                humanoidModel.leftArm.loadPose(posedLeftArm)
            }

            // In 26.3 sleeves and hat are children of the arms and head. They
            // inherit their parent's pose; copying it here transforms them twice.
        } finally {
            applyingRegularAnimation = false
        }
    }

    const val RIDING_SITTING_OFFSET = -10.0
    fun animateRoot(stack: PoseStack) {
        val root = relevantPartsByName["body"] ?: return
        val rootOffset = Vec3(-root.x.toDouble(), root.y.toDouble() + RIDING_SITTING_OFFSET, root.z.toDouble()).scale(1/16.0)
        val rotation = Vec3(root.xRot.toDouble(), root.yRot.toDouble(), root.zRot.toDouble())
        stack.translate(rootOffset.x.toFloat(), rootOffset.y.toFloat(), rootOffset.z.toFloat())
        stack.rotate(Axis.ZP.rotation(rotation.z.toFloat()))
        stack.rotate(Axis.YP.rotation(rotation.y.toFloat()))
        stack.rotate(Axis.XP.rotation(rotation.x.toFloat()))
    }
}

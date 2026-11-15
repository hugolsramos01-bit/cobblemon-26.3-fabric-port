/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.entity

import com.cobblemon.mod.common.api.fishing.PokeRods
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import java.awt.Color
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

class PokeBobberRenderState : EntityRenderState() {
    val ballItem = ItemStackRenderState()
    val baitItem = ItemStackRenderState()
    var baitStack = net.minecraft.world.item.ItemStack.EMPTY
    var randomPitch = 0F
    var randomYaw = 0F
    var spinAngle = 0F
    var lineColor = "#FFFFFF"
    var lineDelta = Vec3.ZERO
    var showLine = false
}

@Environment(value = EnvType.CLIENT)
class PokeBobberEntityRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<PokeRodFishingBobberEntity, PokeBobberRenderState>(context) {

    override fun createRenderState() = PokeBobberRenderState()

    override fun extractRenderState(entity: PokeRodFishingBobberEntity, state: PokeBobberRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        val player = entity.playerOwner ?: return

        if (entity.tickCount <= 1) {
            entity.randomYaw = (-180 + Math.random() * 360).toFloat()
            entity.randomPitch = (-40 + Math.random() * 80).toFloat()
        }

        if (!entity.onGround()) {
            val stopRotationAge = 220
            val ageFactor = if (entity.tickCount < stopRotationAge) {
                if (entity.inOpenWater) 1 + entity.tickCount / 15.0 else 1 + entity.tickCount / 100.0
            } else {
                Double.POSITIVE_INFINITY
            }
            if (entity.tickCount < stopRotationAge) {
                entity.lastSpinAngle = (((entity.tickCount + partialTicks) * 20 / ageFactor) % 360).toFloat()
            }
        }

        if (entity.state == PokeRodFishingBobberEntity.State.BOBBING && !entity.onGround()) {
            entity.randomPitch = Mth.lerp(0.04F, entity.randomPitch, 0F)
        }

        state.randomPitch = entity.randomPitch
        state.randomYaw = entity.randomYaw
        state.spinAngle = entity.lastSpinAngle

        val pokeRodId = Identifier.tryParse(entity.entityData.get(PokeRodFishingBobberEntity.POKEROD_ID)) ?: return
        val pokeRod = PokeRods.getPokeRod(pokeRodId) ?: return
        val pokeBall = PokeBalls.getPokeBall(pokeRod.pokeBallId) ?: return
        state.lineColor = pokeRod.lineColor
        state.baitStack = entity.entityData.get(PokeRodFishingBobberEntity.POKEBOBBER_BAIT).copy()

        val itemModelResolver = Minecraft.getInstance().itemModelResolver
        itemModelResolver.updateForTopItem(
            state.ballItem,
            pokeBall.item.defaultInstance,
            ItemDisplayContext.GROUND,
            entity.level(),
            null,
            0
        )
        itemModelResolver.updateForTopItem(
            state.baitItem,
            state.baitStack,
            ItemDisplayContext.GROUND,
            entity.level(),
            null,
            0
        )

        val tickDelta = Minecraft.getInstance().deltaTracker.realtimeDeltaTicks
        val armOffset = if (player.mainArm == HumanoidArm.RIGHT) 1 else -1
        val heldItem = player.mainHandItem.item
        val actualArmOffset = if (heldItem is PokerodItem && heldItem.pokeRodId == pokeRodId) armOffset else -armOffset
        val swingAngle = Mth.sin(Mth.sqrt(player.getSwingAnimation(tickDelta)) * Math.PI)
        val bodyYaw = Mth.lerp(tickDelta.toDouble(), player.yBodyRotO.toDouble(), player.yBodyRot.toDouble()) * (Math.PI / 180.0)
        val sinBodyYaw = Mth.sin(bodyYaw).toDouble()
        val cosBodyYaw = Mth.cos(bodyYaw).toDouble()
        val horizontalOffset = actualArmOffset.toDouble() * 0.35

        val playerLineOrigin = if (!entityRenderDispatcher.options.cameraType.isFirstPerson || player != Minecraft.getInstance().player) {
            val eyeY = Mth.lerp(tickDelta.toDouble(), player.xo, player.x) - cosBodyYaw * horizontalOffset - sinBodyYaw * 0.8
            val y = player.yo + player.eyeHeight.toDouble() + (player.y - player.yo) * tickDelta.toDouble() - 0.45
            val z = Mth.lerp(tickDelta.toDouble(), player.zo, player.z) - sinBodyYaw * horizontalOffset + cosBodyYaw * 0.8
            Vec3(eyeY.toDouble(), (y + if (player.isCrouching) -0.1875 else 0.0).toDouble(), z.toDouble())
        } else {
            val fov = entityRenderDispatcher.options.fov().get().toFloat()
            val camera = entityRenderDispatcher.camera ?: return
            val nearPlane = camera.getNearPlane(fov)
            var offset = nearPlane.getPointOnPlane(actualArmOffset * 0.525F, -0.1F).scale(960.0 / fov)
            offset = offset.yRot(swingAngle * 0.5F)
            offset = offset.xRot(-swingAngle * 0.7F)
            Vec3(
                Mth.lerp(tickDelta.toDouble(), player.xo, player.x) + offset.x,
                Mth.lerp(tickDelta.toDouble(), player.yo, player.y) + offset.y,
                Mth.lerp(tickDelta.toDouble(), player.zo, player.z) + offset.z
            )
        }

        val bobberX = Mth.lerp(tickDelta.toDouble(), entity.xo, entity.x)
        val bobberY = Mth.lerp(tickDelta.toDouble(), entity.yo, entity.y) + 0.25
        val bobberZ = Mth.lerp(tickDelta.toDouble(), entity.zo, entity.z)
        state.lineDelta = Vec3(playerLineOrigin.x - bobberX, playerLineOrigin.y - bobberY, playerLineOrigin.z - bobberZ)
        state.showLine = true
    }

    override fun submit(state: PokeBobberRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, cameraState: CameraRenderState) {
        poseStack.pushPose()
        poseStack.pushPose()
        poseStack.rotate(Axis.XN.rotationDegrees(state.randomPitch))
        poseStack.rotate(Axis.YN.rotationDegrees(state.randomYaw))
        poseStack.rotate(Axis.YN.rotationDegrees(state.spinAngle))
        poseStack.scale(0.7F, 0.7F, 0.7F)

        collector.submitCustomGeometry(poseStack, HOOK_LAYER) { pose, consumer ->
            submitHook(consumer, pose, state.lightCoords)
        }
        state.ballItem.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)

        poseStack.pushPose()
        val berryAdjust = adjustBerry(state.baitStack)
        poseStack.scale(0.8F, 0.8F, 0.8F)
        poseStack.translate(0.20 + berryAdjust.first, -0.5 + berryAdjust.second, 0.0)
        poseStack.rotate(Quaternionf().rotateY(0F))
        state.baitItem.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)
        poseStack.popPose()
        poseStack.popPose()

        if (state.showLine) {
            val color = Color.decode(state.lineColor)
            collector.submitCustomGeometry(poseStack, RenderTypes.lines()) { pose, consumer ->
                submitFishingLine(consumer, pose, state.lineDelta, color)
            }
        }
        poseStack.popPose()
        super.submit(state, poseStack, collector, cameraState)
    }

    private fun adjustBerry(bait: net.minecraft.world.item.ItemStack): Pair<Double, Double> =
        when (BuiltInRegistries.ITEM.getKey(bait.item).toString()) {
            "cobblemon:wacan_berry", "cobblemon:cheri_berry" -> 0.03 to 0.0
            else -> 0.0 to 0.0
        }

    companion object {
        private val TEXTURE = cobblemonResource("textures/item/fishing/bobber_hook.png")
        private val HOOK_LAYER = RenderTypes.entityCutout(TEXTURE)

        private fun submitHook(buffer: VertexConsumer, pose: PoseStack.Pose, light: Int) {
            val scale = 0.75F
            val shiftHook = 0.045F
            vertex(buffer, pose, light, (1F - 0F) * scale + 0.125F + shiftHook, 0F * scale + 0.125F, 0, 1)
            vertex(buffer, pose, light, (1F - 1F) * scale + 0.125F + shiftHook, 0F * scale + 0.125F, 1, 1)
            vertex(buffer, pose, light, (1F - 1F) * scale + 0.125F + shiftHook, 1F * scale + 0.125F, 1, 0)
            vertex(buffer, pose, light, (1F - 0F) * scale + 0.125F + shiftHook, 1F * scale + 0.125F, 0, 0)
            vertex(buffer, pose, light, (1F - 0F) * scale + 0.125F + shiftHook, 1F * scale + 0.125F, 0, 0)
            vertex(buffer, pose, light, (1F - 1F) * scale + 0.125F + shiftHook, 1F * scale + 0.125F, 1, 0)
            vertex(buffer, pose, light, (1F - 1F) * scale + 0.125F + shiftHook, 0F * scale + 0.125F, 1, 1)
            vertex(buffer, pose, light, (1F - 0F) * scale + 0.125F + shiftHook, 0F * scale + 0.125F, 0, 1)
        }

        private fun vertex(buffer: VertexConsumer, pose: PoseStack.Pose, light: Int, x: Float, y: Float, u: Int, v: Int) {
            buffer.addVertex(pose.pose(), x - 0.5F, y - 0.5F, 0F)
                .setColor(255, 255, 255, 255)
                .setUv(u.toFloat(), v.toFloat())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0F, 1F, 0F)
        }

        private fun submitFishingLine(buffer: VertexConsumer, pose: PoseStack.Pose, delta: Vec3, color: Color) {
            for (segment in 0 until 16) {
                val start = linePoint(delta, segment)
                val end = linePoint(delta, segment + 1)
                val dx = (end.x - start.x).toFloat()
                val dy = (end.y - start.y).toFloat()
                val dz = (end.z - start.z).toFloat()
                val length = Mth.sqrt(dx * dx + dy * dy + dz * dz)
                val nx = dx / length
                val ny = dy / length
                val nz = dz / length
                buffer.addVertex(pose.pose(), start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
                    .setColor(color.red, color.green, color.blue, 255)
                    .setNormal(pose, nx, ny, nz)
                buffer.addVertex(pose.pose(), end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
                    .setColor(color.red, color.green, color.blue, 255)
                    .setNormal(pose, nx, ny, nz)
            }
        }

        private fun linePoint(delta: Vec3, index: Int): Vec3 {
            val fraction = index / 16.0
            val nextFraction = (index + 1) / 16.0
            val lowering = 1.0 - nextFraction
            val y = delta.y * (fraction * fraction + fraction) * 0.5 + 0.25 - lowering * 0.1
            return Vec3(delta.x * fraction, y, delta.z * fraction)
        }
    }
}

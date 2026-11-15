/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CampfirePotRenderState : BlockEntityRenderState() {
    var hasPot = false
    var facing = Direction.SOUTH
    var gameTime = 0F
    var potParts: List<BlockStateModelPart> = emptyList()
    var brothTints = intArrayOf()
    var seasonings: List<ItemStackRenderState> = emptyList()
}

class DiscShelfRenderState : BlockEntityRenderState() {
    var facing = Direction.NORTH
    var slots: List<DiscShelfSlot> = emptyList()
}
data class DiscShelfSlot(val index: Int, val texture: Identifier)

fun applyCenteredBlockRotation(pose: PoseStack, degrees: Float) {
    pose.translate(0.5, 0.5, 0.5)
    pose.rotate(Axis.YP.rotationDegrees(degrees))
    pose.translate(-0.5, -0.5, -0.5)
}

fun campfireFrameRotation(facing: Direction): Float = when (facing) {
    Direction.NORTH -> 180F
    Direction.WEST -> 90F
    Direction.EAST -> -90F
    else -> 0F
}
fun discShelfFrameRotation(facing: Direction): Float = when (facing) {
    Direction.SOUTH -> 180F
    Direction.WEST -> 90F
    Direction.EAST -> -90F
    else -> 0F
}

fun applyCampfireSeasoningTransform(pose: PoseStack, gameTime: Float, index: Int, count: Int) {
    val rotation = (gameTime * 1.5F) % 360
    val radians = Math.toRadians((rotation + index * (360F / count)).toDouble())
    val x = cos(radians).toFloat() * 0.4F
    val z = sin(radians).toFloat() * 0.4F
    val jump = sin(gameTime * 0.1F + index * 2) * 0.025F
    pose.scale(0.5F, 0.5F, 0.5F)
    pose.translate(1F + x, 1.24F + jump, 1F + z)
    pose.rotate(Axis.YP.rotationDegrees(-Math.toDegrees(atan2(z.toDouble(), x.toDouble())).toFloat() + 90F))
    pose.rotate(Axis.XP.rotationDegrees(22.5F))
}

fun applyDiscShelfSlotTransform(pose: PoseStack, index: Int) {
    val col = 1 - index % 2
    val x = if (col == 0) 1F / 16F else 9F / 16F
    val y = 1F / 16F + (6 - index / 2) * (2F / 16F)
    pose.translate(x, y, -0.001F)
}

/** Original winding, reversed U coordinates and 0.65 brightness multiplier. */
fun drawDiscShelfSlot(pose: PoseStack.Pose, consumer: VertexConsumer, light: Int) {
    val matrix = pose.pose()
    fun vertex(x: Float, y: Float, u: Float, v: Float) {
        consumer.addVertex(matrix, x, y, 0F).setColor(0.65F, 0.65F, 0.65F, 1F).setUv(u, v)
            .setUv1(0, 0).setUv2(light and 0xFFFF, light shr 16).setNormal(0F, 0F, -1F)
    }
    vertex(0F, 0F, 6F / 16F, 2F / 16F)
    vertex(0F, 2F / 16F, 6F / 16F, 0F)
    vertex(6F / 16F, 2F / 16F, 0F, 0F)
    vertex(6F / 16F, 0F, 0F, 2F / 16F)
}

/** Extract native blockstate parts without retaining a level or block entity in the draw state. */
fun extractCampfirePotParts(modelSet: net.minecraft.client.renderer.block.BlockStateModelSet, state: net.minecraft.world.level.block.state.BlockState): List<BlockStateModelPart> =
    mutableListOf<BlockStateModelPart>().also { modelSet.get(state).collectParts(net.minecraft.util.RandomSource.create(42L), it) }.toList()

fun campfireBrothTints(parts: List<BlockStateModelPart>, color: Int): IntArray {
    val maxTint = parts.flatMap { part ->
        (Direction.entries.flatMap { part.getQuads(it) } + part.getQuads(null)).map { it.materialInfo().tintIndex() }
    }.maxOrNull() ?: -1
    return IntArray(maxTint + 1) { net.minecraft.util.ARGB.opaque(color) }
}

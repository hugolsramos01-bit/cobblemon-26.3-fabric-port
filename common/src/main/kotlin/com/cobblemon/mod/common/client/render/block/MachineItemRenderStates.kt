/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis

class HealingMachineRenderState : BlockEntityRenderState() {
    var yRotation: Float = 0F
    var pokeBalls: List<Pair<Int, ItemStackRenderState>> = emptyList()
}

class FossilAnalyzerRenderState : BlockEntityRenderState() {
    var yRotation: Float = 0F
    var directionOffset: Vec3 = Vec3.ZERO
    var fossils: List<ItemStackRenderState> = emptyList()
}

internal fun applyHealingMachineFrame(pose: PoseStack, yRotation: Float) {
    pose.translate(0.5, 0.5, 0.5)
    pose.rotateDegrees(Axis.YP, -yRotation)
    pose.scale(0.65F, 0.65F, 0.65F)
}

internal fun applyFossilItemTransform(pose: PoseStack, yRotation: Float, offset: Vec3, index: Int) {
    pose.translate(0.5 + offset.x, 0.4 + index * 0.1 + offset.y, 0.5 + offset.z)
    pose.rotateDegrees(Axis.YP, yRotation)
    pose.rotateDegrees(Axis.ZP, 180F)
    pose.rotateDegrees(Axis.XP, 90F)
    pose.scale(0.7F, 0.7F, 0.7F)
}

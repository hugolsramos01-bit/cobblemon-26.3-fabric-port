/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.entity

import com.cobblemon.mod.common.api.riding.Rideable
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.duck.MountedRiderRenderState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

object MountedLivingEntityRenderer {
    fun capture(
        entity: LivingEntity,
        pokemon: PokemonEntity,
        partialTicks: Float
    ): Matrix4f? {
        if (entity.vehicle !is Rideable) return null
        val delegate = pokemon.delegate as? PokemonClientDelegate ?: return null
        val seat = delegate.getSeatLocator(entity) ?: return null
        MountedPokemonAnimationRenderController.setup(pokemon, partialTicks)
        val locator = delegate.locatorStates[seat] ?: return null
        val rotation = locator.matrix.getUnnormalizedRotation(Quaternionf())
        // A hidden/zero-scaled seat bone must not put NaNs into the entire player model.
        if (!rotation.isFinite || rotation.lengthSquared() < 0.000001f) return null
        rotation.normalize()

        val playerPos = Vec3(
            Mth.lerp(partialTicks.toDouble(), entity.xOld, entity.x),
            Mth.lerp(partialTicks.toDouble(), entity.yOld, entity.y),
            Mth.lerp(partialTicks.toDouble(), entity.zOld, entity.z),
        )

        val entityPos = Vec3(
            Mth.lerp(partialTicks.toDouble(), pokemon.xOld, pokemon.x),
            Mth.lerp(partialTicks.toDouble(), pokemon.yOld, pokemon.y),
            Mth.lerp(partialTicks.toDouble(), pokemon.zOld, pokemon.z),
        )

        // The position updater centers the passenger's hitbox on the seat. Undo that
        // world-space position and place the visual model along the seat's animated up axis.
        val matrix = Matrix4f()
            .translation(playerPos.subtract(entityPos).toVector3f().negate())
            .translate(locator.matrix.getTranslation(Vector3f()))
            .rotate(rotation)
            .translate(0f, -entity.bbHeight / 2f + 0.35f, 0f)
        return matrix.takeIf { it.isFinite }
    }

    fun apply(state: LivingEntityRenderState, stack: PoseStack, bodyRot: Float, scale: Float) {
        val captured = (state as MountedRiderRenderState).`cobblemon$getRiderPose`() ?: return
        if (!scale.isFinite() || scale <= 0f) return
        // Vanilla has already applied entity scale before setupRotations. Seat positions
        // are world distances; compensate only their translation, not the rider's scale.
        val matrix = Matrix4f(captured)
            .m30(captured.m30() / scale)
            .m31(captured.m31() / scale)
            .m32(captured.m32() / scale)
        stack.mulPose(matrix)
        // Vanilla applies (180 - bodyRot) next. Cancel that yaw so seat orientation wins.
        stack.rotate(Axis.YP.rotationDegrees(180f + bodyRot))
    }
}

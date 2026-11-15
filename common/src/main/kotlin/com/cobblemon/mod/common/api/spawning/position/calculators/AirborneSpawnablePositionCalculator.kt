/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.position.calculators

import com.cobblemon.mod.common.api.spawning.position.AirborneSpawnablePosition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.isAirCondition

/** Finds visible air with room for an airborne Pokémon, above a nearby surface or around a flying player. */
object AirborneSpawnablePositionCalculator : AreaSpawnablePositionCalculator<AirborneSpawnablePosition> {
    override val name = "airborne"

    override fun fits(input: AreaSpawningInput): Boolean {
        val pos = input.position
        if (!input.zone.isInBounds(pos.x, pos.y, pos.z) || pos.y + 1 >= input.zone.baseY + input.zone.height) return false
        if (!isAirCondition(input.zone.getBlockState(pos)) || !isAirCondition(input.zone.getBlockState(pos.above()))) return false
        if (!input.zone.hasOpenSky(pos)) return false

        val surfaceY = input.zone.surfaceY(pos.x, pos.z)
        if (surfaceY != null) {
            return pos.y in (surfaceY + MIN_HEIGHT_ABOVE_SURFACE)..(surfaceY + MAX_HEIGHT_ABOVE_SURFACE)
        }

        // If the complete vertical sample is already above the local terrain, use the player as the altitude reference.
        val playerY = input.zone.cause.entity?.blockY ?: return false
        return pos.y in (playerY - FALLBACK_ALTITUDE_RANGE)..(playerY + FALLBACK_ALTITUDE_RANGE)
    }

    override fun calculate(input: AreaSpawningInput): AirborneSpawnablePosition {
        return AirborneSpawnablePosition(
            cause = input.cause,
            world = input.world,
            position = input.position.immutable(),
            light = getLight(input),
            skyLight = getSkyLight(input),
            canSeeSky = true,
            influences = input.spawner.copyInfluences(),
            height = getHeight(input, isAirCondition, input.world.maxY - input.position.y - 1, offsetY = 1),
            nearbyBlocks = getNearbyBlocks(input),
            zone = input.zone
        )
    }

    private const val MIN_HEIGHT_ABOVE_SURFACE = 4
    private const val MAX_HEIGHT_ABOVE_SURFACE = 32
    private const val FALLBACK_ALTITUDE_RANGE = 8
}

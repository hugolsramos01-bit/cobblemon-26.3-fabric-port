/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.duck.PlayerDuck
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player

fun Player.shoulderEntityData(isLeft: Boolean): CompoundTag =
    (this as PlayerDuck).`cobblemon$getShoulderPokemon`(isLeft)

fun Player.setShoulderEntityData(isLeft: Boolean, tag: CompoundTag) {
    (this as PlayerDuck).`cobblemon$setShoulderPokemon`(isLeft, tag)
}

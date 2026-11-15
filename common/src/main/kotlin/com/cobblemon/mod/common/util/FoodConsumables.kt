/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import net.minecraft.world.item.component.Consumable
import net.minecraft.world.item.component.Consumables
import net.minecraft.world.item.consume_effects.ConsumeEffect

/** Nutrition belongs to FoodProperties; timing and ordered effects belong to Consumable. */
fun foodConsumable(
    consumeSeconds: Float = Consumable.DEFAULT_CONSUME_SECONDS,
    effects: List<ConsumeEffect> = emptyList()
): Consumable = Consumables.defaultFood().apply {
    consumeSeconds(consumeSeconds)
    effects.forEach { onConsume(it) }
}.build()

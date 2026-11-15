/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.trading.TradeSet

/**
 * Native 26.3 trade pools. Offers load from data/cobblemon/villager_trade;
 * additive tags attach fisherman/wandering offers to the existing vanilla pools.
 */
object CobblemonTradeOffers {
    fun nurseTradeSets(): Int2ObjectMap<ResourceKey<TradeSet>> {
        val pools = Int2ObjectOpenHashMap<ResourceKey<TradeSet>>()
        for (level in 1..5) {
            pools.put(level, ResourceKey.create(
                Registries.TRADE_SET,
                Identifier.fromNamespaceAndPath("cobblemon", "nurse/level_$level")
            ))
        }
        return pools
    }
}

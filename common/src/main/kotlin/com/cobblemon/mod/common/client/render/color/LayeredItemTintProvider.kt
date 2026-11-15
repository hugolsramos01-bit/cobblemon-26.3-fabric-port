/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.color

import com.mojang.serialization.MapCodec
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.util.ARGB
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

/** Temporary code-driven tint bridge until the client item definitions have native tint codecs. */
fun interface LayeredItemTintProvider {
    fun getColor(stack: ItemStack, layer: Int): Int
}

/** Attached in memory before baking, not registered for resource-pack serialization. */
class LayeredItemTintSource(private val provider: LayeredItemTintProvider, private val layer: Int) : ItemTintSource {
    private val codec: MapCodec<LayeredItemTintSource> = MapCodec.unit(this)
    override fun calculate(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?): Int =
        ARGB.opaque(provider.getColor(stack, layer))
    override fun type(): MapCodec<out ItemTintSource> = codec
}

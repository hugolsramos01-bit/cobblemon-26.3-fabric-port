/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.client

import com.cobblemon.mod.common.client.render.color.LayeredItemTintProvider
import com.cobblemon.mod.common.client.render.color.applyCodeDrivenItemTints
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

/** Provisional in-memory tint wiring; native client item assets/codecs remain a separate port step. */
internal fun registerItemTintProvider(provider: LayeredItemTintProvider, vararg items: Item) {
    val itemIds = items.map { BuiltInRegistries.ITEM.getKey(it) }.toSet()
    ModelLoadingPlugin.register { context ->
        context.modifyItemModelBeforeBake().register { model, bakeContext ->
            if (bakeContext.itemId() in itemIds) applyCodeDrivenItemTints(model, provider) else model
        }
    }
}

internal fun registerBlockTintProvider(provider: BlockTintSource, vararg blocks: Block) {
    BlockColorRegistry.register(listOf(provider), *blocks)
}

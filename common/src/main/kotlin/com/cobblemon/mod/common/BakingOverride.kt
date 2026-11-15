/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.client.resources.model.geometry.QuadCollection
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState

/** Retains the former model location/variant alias without encoding a variant in a resource path. */
data class BakingOverrideIdentifier(val location: Identifier, val variant: String) {
    override fun toString() = "$location#$variant"
}

/** An extra JSON model, independent of the blockstate model registry. */
data class BakingOverride(val modelLocation: Identifier, val modelIdentifier: BakingOverrideIdentifier) {
    private val model = BakedOverrideModel { lookup(modelIdentifier) }

    // Renderers may retain this handle: its geometry is resolved again after every resource reload.
    fun getModel(): BakedOverrideModel = model

    companion object {
        private var lookup: (BakingOverrideIdentifier) -> QuadCollection = {
            error("Extra model loader has not been initialized: $it")
        }

        fun bindModelLookup(provider: (BakingOverrideIdentifier) -> QuadCollection) {
            lookup = provider
        }
    }
}

/** The custom tank/mulch JSON geometry is static, as it was in the old baked block model. */
class BakedOverrideModel(private val geometry: () -> QuadCollection) {
    @Suppress("UNUSED_PARAMETER")
    fun getQuads(state: BlockState?, direction: Direction?, random: RandomSource?): List<BakedQuad> =
        geometry().getQuads(direction)
}

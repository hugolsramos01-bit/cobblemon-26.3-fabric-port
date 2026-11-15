/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.client

import com.cobblemon.mod.common.BakingOverride
import com.cobblemon.mod.common.BakingOverrideIdentifier
import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation
import net.minecraft.client.resources.model.geometry.QuadCollection
import net.minecraft.resources.Identifier

/** Paths and aliases used by the former loader; item appearance selection is a separate pipeline. */
internal fun dynamicBakingOverride(resource: Identifier): BakingOverride? {
    val path = resource.path
    if (!path.endsWith(".json") || !(path.startsWith("models/item/egg/") || path.startsWith("models/item/tm/"))) return null
    val model = Identifier.fromNamespaceAndPath(resource.namespace, path.removePrefix("models/").removeSuffix(".json"))
    val alias = Identifier.fromNamespaceAndPath(resource.namespace, model.path.removePrefix("item/"))
    return BakingOverride(model, BakingOverrideIdentifier(alias, "inventory"))
}

object FabricBakingOverrides {
    private val keys = ConcurrentHashMap<BakingOverrideIdentifier, ExtraModelKey<QuadCollection>>()

    fun register() {
        BakingOverride.bindModelLookup { alias ->
            val key = checkNotNull(keys[alias]) { "Unregistered extra model: $alias" }
            val manager = Minecraft.getInstance().modelManager as FabricModelManager
            checkNotNull(manager.getModel(key)) { "Extra model was not baked in the current resource reload: $alias" }
        }
        PreparableModelLoadingPlugin.register<List<BakingOverride>>(
            { state, executor ->
                CompletableFuture.supplyAsync({
                    // Use the incoming reload's resources, never Minecraft's previous active pack set.
                    listOf("egg", "tm").flatMap { folder ->
                        state.resourceManager().listResources("models/item/$folder") { it.path.endsWith(".json") }
                            .keys.mapNotNull(::dynamicBakingOverride)
                    }
                }, executor)
            },
            { dynamicModels, context ->
                (CobblemonBakingOverrides.models + dynamicModels).forEach { descriptor ->
                    val key = keys.computeIfAbsent(descriptor.modelIdentifier) {
                        ExtraModelKey.create { descriptor.modelIdentifier.toString() }
                    }
                    context.addModel(key, SimpleUnbakedExtraModel(descriptor.modelLocation) { resolved, baker ->
                        resolved.bakeTopGeometry(resolved.getTopTextureSlots(), baker, BlockModelRotation.IDENTITY)
                    })
                }
            }
        )
    }
}

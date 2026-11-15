/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.item.Item

/** Set the registry key before a block constructor derives its description and loot. */
fun BlockBehaviour.Properties.withBlockId(id: Identifier): BlockBehaviour.Properties =
    setId(ResourceKey.create(Registries.BLOCK, id))

/** Set the registry key before an item constructor derives its description and model. */
fun Item.Properties.withItemId(id: Identifier): Item.Properties =
    setId(ResourceKey.create(Registries.ITEM, id))

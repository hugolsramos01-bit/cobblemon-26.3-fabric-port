/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.util

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.item.ItemStack

/** Compatibility helper preserving the old empty-stack behavior with the current contextual codec. */
fun ItemStack.saveOptional(registryAccess: HolderLookup.Provider): CompoundTag {
    if (isEmpty) return CompoundTag()
    return ItemStack.CODEC.encodeStart(registryAccess.createSerializationContext(NbtOps.INSTANCE), this)
        .result()
        .orElseGet { CompoundTag() } as? CompoundTag ?: CompoundTag()
}

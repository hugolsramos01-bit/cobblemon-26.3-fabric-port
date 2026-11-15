/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.campfirepot

import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/** Tracks a detached snapshot, so in-place stack/component changes are also detected. */
internal class CookingPotPreviewSyncState {
    private var lastSent: ItemStack? = null
    private var lastSeasoningTag: Identifier? = null

    data class Snapshot(val preview: ItemStack, val seasoningTagId: Identifier?)

    fun snapshotIfChanged(preview: ItemStack, seasoningTagId: Identifier?): Snapshot? {
        if (lastSeasoningTag == seasoningTagId && lastSent?.let { ItemStack.matches(it, preview) } == true) return null
        lastSent = preview.copy()
        lastSeasoningTag = seasoningTagId
        return Snapshot(preview.copy(), seasoningTagId)
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import net.minecraft.world.level.Level

/** Compatibility accessors for the 26.3 world clock API. */
fun Level.dayTime(): Long = overworldClockTime

val Level.moonPhase: Int
    get() = ((overworldClockTime / 24000L).mod(8L)).toInt()

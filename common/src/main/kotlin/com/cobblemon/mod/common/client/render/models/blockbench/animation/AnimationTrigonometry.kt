/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render.models.blockbench.animation

import net.minecraft.util.Mth

// Keep Minecraft's lookup table behavior while adapting its new double angle input.
internal fun animationSin(angle: Float): Float = Mth.sin(angle.toDouble())
internal fun animationCos(angle: Float): Float = Mth.cos(angle.toDouble())

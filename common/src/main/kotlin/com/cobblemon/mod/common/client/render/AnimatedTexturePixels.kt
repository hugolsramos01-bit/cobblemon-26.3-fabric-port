/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import kotlin.math.floor

/** Interpolates the ARGB values returned by NativeImage.getPixel in 26.3. */
internal fun interpolateTexturePixel(first: Int, second: Int, fraction: Float): Int {
    val firstAlpha = (first ushr 24 and 255) / 255F
    val secondAlpha = (second ushr 24 and 255) / 255F
    fun channel(shift: Int): Int {
        val a = (first ushr shift and 255) / 255F
        val b = (second ushr shift and 255) / 255F
        // Keep opaque RGB when fading to/from a transparent pixel to avoid black fringes.
        val value = when {
            firstAlpha == 0F && secondAlpha == 1F -> b
            firstAlpha == 1F && secondAlpha == 0F -> a
            else -> a + (b - a) * fraction
        }
        return floor(value * 255).toInt()
    }
    val alpha = floor((firstAlpha + (secondAlpha - firstAlpha) * fraction) * 255).toInt()
    return (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

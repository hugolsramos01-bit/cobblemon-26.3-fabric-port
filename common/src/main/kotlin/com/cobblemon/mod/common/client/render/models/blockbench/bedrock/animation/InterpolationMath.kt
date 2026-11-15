/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation.Companion.X_AXIS
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation.Companion.Y_AXIS
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation.Companion.Z_AXIS
import kotlin.math.floor
import net.minecraft.world.phys.Vec3

/**
 * Interpolates a vector based on a Catmull-Rom spline.
 * Frame A to D should be in order based on time. Frame A should be the keyframe before frame B, which should be the keyframe before C, and so on.
 */
fun catmullromLerp(frameA: BedrockAnimationKeyFrame?, frameB: BedrockAnimationKeyFrame, frameC: BedrockAnimationKeyFrame, frameD: BedrockAnimationKeyFrame?, time: Double, runtime: MoLangRuntime): Vec3 {
    return Vec3(
        catmullromLerp(frameA, frameB, frameC, frameD, X_AXIS, time, runtime),
        catmullromLerp(frameA, frameB, frameC, frameD, Y_AXIS, time, runtime),
        catmullromLerp(frameA, frameB, frameC, frameD, Z_AXIS, time, runtime)
    )
}
/**
 * Gets the interpolation alpha for a value between two points
 *
 * @return A linearly interpolated value between 0 and 1
 */
fun linearLerpAlpha(before: Double, after: Double, value: Double): Double {
    return (value - before) / (after - before)
}

/**
 * Interpolates a value based on a Catmull-Rom spline on a given axis.
 * Frame A to D should be in order based on time. Frame A should be the keyframe before frame B, which should be the keyframe before C, and so on.
 */
fun catmullromLerp(frameA: BedrockAnimationKeyFrame?, frameB: BedrockAnimationKeyFrame, frameC: BedrockAnimationKeyFrame, frameD: BedrockAnimationKeyFrame?, axis: Int, time: Double, runtime: MoLangRuntime) : Double {
    val frameAData = frameA?.post?.resolve(time, runtime)
    val frameBData = frameB.post.resolve(time, runtime)
    val frameCData = frameC.pre.resolve(time, runtime)
    val frameDData = frameD?.pre?.resolve(time, runtime)
    val count = 2 + (if (frameAData != null) 1 else 0) + (if (frameDData != null) 1 else 0)
    val alpha = (linearLerpAlpha(frameB.time, frameC.time, time) + if (frameA != null) 1 else 0) / (count - 1)
    val p = (count - 1) * alpha
    val index = floor(p).toInt()
    val weight = p - index
    fun valueAt(i: Int): Double {
        if (i < 0 || i >= count) throw IndexOutOfBoundsException("Spline point index: $i")
        val offset = if (frameAData != null) 1 else 0
        return when {
            frameAData != null && i == 0 -> frameAData.get(axis)
            i == offset -> frameBData.get(axis)
            i == offset + 1 -> frameCData.get(axis)
            else -> checkNotNull(frameDData).get(axis)
        }
    }
    val p0 = if (index == 0) index else index - 1
    val p2 = if (index > count - 2) count - 1 else index + 1
    val p3 = if (index > count - 3) count - 1 else index + 2
    return catmullrom(weight, valueAt(p0), valueAt(index), valueAt(p2), valueAt(p3))
}

fun Vec3.get(axis: Int) = when (axis) {
    0 -> x
    1 -> y
    else -> z
}

private fun catmullrom(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
    val v0 = (p2 - p0) * 0.5
    val v1 = (p3 - p1) * 0.5
    val t2 = t * t
    val t3 = t * t2
    return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * t + p1
}

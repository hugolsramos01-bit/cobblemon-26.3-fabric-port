/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/
 */
package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.client.render.models.blockbench.LayerVertexConsumers
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType

/** One resolved drawing pass. The texture is already part of the native RenderType, not a deferred supplier. */
data class FrozenModelLayer(val renderType: RenderType, val geometry: FrozenModelGeometry) {
    fun submit(pose: PoseStack, collector: SubmitNodeCollector) {
        val snapshot = geometry
        collector.submitCustomGeometry(pose, renderType) { capturedPose, consumer -> snapshot.draw(capturedPose, consumer) }
    }
}

/** Records layers in request order, including duplicate passes. */
class LayerGeometryCapture internal constructor(
    internal val reuseBaseGeometry: Boolean
) : LayerVertexConsumers {
    constructor() : this(false)

    private data class Pass(
        val renderType: RenderType,
        val capture: ModelGeometryCapture?,
        val sharedColor: Int = -1
    )

    private val passes = mutableListOf<Pass>()

    override fun getBuffer(renderType: RenderType): VertexConsumer = ModelGeometryCapture().also {
        passes.add(Pass(renderType, it))
    }

    internal fun addSharedPass(renderType: RenderType, color: Int) {
        passes.add(Pass(renderType, capture = null, sharedColor = color))
    }

    fun freeze(): List<FrozenModelLayer> = freeze(null)

    internal fun freeze(baseGeometry: FrozenModelGeometry?): List<FrozenModelLayer> = passes.map { pass ->
        val geometry = pass.capture?.freeze()
            ?: checkNotNull(baseGeometry) { "Shared layer requires the frozen base geometry" }.withColor(pass.sharedColor)
        FrozenModelLayer(pass.renderType, geometry)
    }
}

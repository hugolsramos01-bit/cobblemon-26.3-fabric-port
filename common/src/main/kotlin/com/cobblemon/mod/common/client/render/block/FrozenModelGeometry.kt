/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer

/** CPU-only copy of emitted geometry; no model, entity or mutable pose is retained. */
data class FrozenModelVertex(
    val x: Float, val y: Float, val z: Float,
    val color: Int, val u: Float, val v: Float,
    val overlay: Int, val light: Int, val secondaryU: Float, val secondaryV: Float,
    val normalX: Float, val normalY: Float, val normalZ: Float, val lineWidth: Float
)

class FrozenModelGeometry private constructor(
    private val vertices: List<FrozenModelVertex>,
    private val colorOverride: Int?
) {
    constructor(vertices: List<FrozenModelVertex>) : this(vertices.toList(), null)

    internal fun withColor(color: Int) = FrozenModelGeometry(vertices, color)

    fun isEmpty() = vertices.isEmpty()
    fun visitPositions(visitor: (Float, Float, Float) -> Unit) {
        vertices.forEach { visitor(it.x, it.y, it.z) }
    }
    fun draw(pose: PoseStack.Pose, consumer: VertexConsumer) {
        for (vertex in vertices) {
            consumer.addVertex(pose, vertex.x, vertex.y, vertex.z).setColor(colorOverride ?: vertex.color)
                .setUv(vertex.u, vertex.v).setOverlay(vertex.overlay).setLight(vertex.light)
                .setUv3(vertex.secondaryU, vertex.secondaryV)
                .setNormal(pose, vertex.normalX, vertex.normalY, vertex.normalZ).setLineWidth(vertex.lineWidth)
        }
    }
}

/** Implements the native vertex contract so ModelPart/quad rendering can be captured during extract. */
class ModelGeometryCapture : VertexConsumer {
    private val vertices = mutableListOf<FrozenModelVertex>()
    private var pending = false
    private var x = 0F; private var y = 0F; private var z = 0F
    private var color = -1; private var u = 0F; private var v = 0F
    private var overlay = 0; private var light = 0
    private var secondaryU = 0F; private var secondaryV = 0F
    private var normalX = 0F; private var normalY = 0F; private var normalZ = 0F
    private var lineWidth = 1F

    private fun flush() {
        if (pending) vertices.add(FrozenModelVertex(x, y, z, color, u, v, overlay, light, secondaryU, secondaryV, normalX, normalY, normalZ, lineWidth))
        pending = false
    }
    fun freeze(): FrozenModelGeometry { flush(); return FrozenModelGeometry(vertices) }

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        flush(); this.x = x; this.y = y; this.z = z; pending = true
        return this
    }
    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = setColor((alpha shl 24) or (red shl 16) or (green shl 8) or blue)
    override fun setColor(color: Int): VertexConsumer { this.color = color; return this }
    override fun setUv(u: Float, v: Float): VertexConsumer { this.u = u; this.v = v; return this }
    override fun setUv1(u: Int, v: Int): VertexConsumer { overlay = (u and 0xFFFF) or (v shl 16); return this }
    override fun setUv2(u: Int, v: Int): VertexConsumer { light = (u and 0xFFFF) or (v shl 16); return this }
    override fun setUv3(u: Float, v: Float): VertexConsumer { secondaryU = u; secondaryV = v; return this }
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer { normalX = x; normalY = y; normalZ = z; return this }
    override fun setLineWidth(width: Float): VertexConsumer { lineWidth = width; return this }
}

/** Snapshot a berry model's current positioning, restoring the shared repository model afterwards. */
fun captureBerryModel(model: net.minecraft.client.model.geom.ModelPart, position: net.minecraft.world.phys.Vec3, rotation: net.minecraft.world.phys.Vec3, pose: PoseStack, capture: ModelGeometryCapture, light: Int) {
    val previous = model.storePose()
    try {
        model.setPos(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
        model.setRotation(Math.toRadians(180 - rotation.x).toFloat(), Math.toRadians(180 + rotation.y).toFloat(), Math.toRadians(rotation.z).toFloat())
        model.render(pose, capture, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
    } finally {
        model.loadPose(previous)
    }
}

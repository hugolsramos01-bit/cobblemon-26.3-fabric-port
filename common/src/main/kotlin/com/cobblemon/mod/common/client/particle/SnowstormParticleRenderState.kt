/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.particle

import com.mojang.blaze3d.vertex.VertexConsumer
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterials
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.state.level.QuadParticleRenderState
import org.joml.Vector3f
import org.joml.Vector4f

/** Rectangular Bedrock quads submitted through Minecraft's particle feature renderer. */
class SnowstormParticleRenderState : QuadParticleRenderState() {
    private data class Quad(val vertices: FloatArray, val red: Float, val green: Float, val blue: Float, val alpha: Float, val light: Int)
    private val quads = linkedMapOf<SingleQuadParticle.Layer, MutableList<Quad>>()

    fun addQuad(layer: SingleQuadParticle.Layer, positions: Array<Vector3f>, minU: Float, maxU: Float, minV: Float, maxV: Float, tint: Vector4f, light: Int) {
        val vertices = FloatArray(20)
        positions.forEachIndexed { index, position ->
            val offset = index * 5
            vertices[offset] = position.x
            vertices[offset + 1] = position.y
            vertices[offset + 2] = position.z
            vertices[offset + 3] = if (index < 2) maxU else minU
            vertices[offset + 4] = if (index == 0 || index == 3) maxV else minV
        }
        quads.getOrPut(layer) { mutableListOf() }.add(Quad(vertices, tint.x, tint.y, tint.z, tint.w, light))
    }

    override fun layers(): Set<SingleQuadParticle.Layer> =
        listOf(ParticleMaterials.ADD, ParticleMaterials.ALPHA, ParticleMaterials.BLEND, ParticleMaterials.OPAQUE)
            .filterTo(linkedSetOf()) { it in quads }
    override fun isEmpty() = quads.isEmpty()
    override fun clear() { quads.clear() }

    override fun buildLayer(layer: SingleQuadParticle.Layer, consumer: VertexConsumer) {
        quads[layer]?.forEach { quad ->
            repeat(4) { index ->
                val offset = index * 5
                consumer.addVertex(quad.vertices[offset], quad.vertices[offset + 1], quad.vertices[offset + 2])
                    .setUv(quad.vertices[offset + 3], quad.vertices[offset + 4])
                    .setColor(quad.red, quad.green, quad.blue, quad.alpha)
                    .setLight(quad.light)
            }
        }
    }

    override fun submit(collector: SubmitNodeCollector, camera: CameraRenderState) {
        if (!isEmpty) collector.submitQuadParticleGroup(this)
    }
}

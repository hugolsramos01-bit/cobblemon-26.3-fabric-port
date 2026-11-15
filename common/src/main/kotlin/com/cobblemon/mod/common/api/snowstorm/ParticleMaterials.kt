/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.api.snowstorm

import com.mojang.renderpearl.api.pipeline.BlendFactor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier
import java.util.Optional

object ParticleMaterials {
    val SNOWSTORM = ParticleRenderType("cobblemon:snowstorm", "S")

    val ALPHA = layer("alpha", true, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
    val ADD = layer("add", false, BlendFactor.SRC_ALPHA, BlendFactor.ONE)
    val BLEND = layer("blend", false, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
    val OPAQUE = layer("opaque", true, BlendFactor.SRC_COLOR, BlendFactor.ZERO)

    private fun layer(name: String, cull: Boolean, source: BlendFactor, destination: BlendFactor): SingleQuadParticle.Layer {
        // Copy vanilla particle shaders, bindings, vertex format and alpha threshold.
        val base = RenderPipelines.TRANSLUCENT_PARTICLE
        val depth = requireNotNull(base.depthStencilState)
        val snippet = RenderPipeline.Snippet(
            base.shaders, Optional.of(base.shaderDefines), Optional.of(base.bindGroupLayouts),
            base.colorTargetStates.toTypedArray(), base.colorTargetStates.size,
            Optional.of(depth), Optional.of(base.polygonMode), Optional.of(base.isCull),
            base.vertexFormatBindings.toTypedArray(), Optional.of(base.primitiveTopology), base.pushConstantSize()
        )
        val pipeline = RenderPipeline.builder(snippet)
            .withLocation(Identifier.fromNamespaceAndPath("cobblemon", "pipeline/particle_$name"))
            .withCull(cull)
            .withColorTargetState(ColorTargetState(BlendFunction(source, destination)))
            .withDepthStencilState(DepthStencilState(depth.depthTest(), true, depth.depthBiasScaleFactor(), depth.depthBiasConstant()))
            .build()
        // Preserve depth writes and exact legacy blend equations in the direct pass.
        // OIT accumulation would replace the material's blending behavior.
        return SingleQuadParticle.Layer(false, TextureAtlas.LOCATION_PARTICLES, pipeline)
    }
}

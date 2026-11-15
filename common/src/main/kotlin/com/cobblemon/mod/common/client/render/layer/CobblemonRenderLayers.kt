/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import java.util.Optional
import java.util.function.BiFunction
import java.util.function.Function

object CobblemonRenderLayers {
    // The old berry layer used the block cutout shader but explicitly disabled culling.
    // Copy the complete pipeline contract, including its shader defines and bind groups;
    // a vanilla entity cutout pipeline has a different vertex layout and lighting shader.
    private val berryPipeline = RenderPipelines.CUTOUT_BLOCK.let { pipeline ->
        RenderPipeline.builder(
            RenderPipeline.Snippet(
                pipeline.shaders,
                Optional.of(pipeline.shaderDefines),
                Optional.of(pipeline.bindGroupLayouts),
                pipeline.colorTargetStates.toTypedArray(),
                pipeline.colorTargetStates.size,
                Optional.ofNullable(pipeline.depthStencilState),
                Optional.of(pipeline.polygonMode),
                Optional.of(false),
                pipeline.vertexFormatBindings.toTypedArray(),
                Optional.of(pipeline.primitiveTopology),
                pipeline.pushConstantSize()
            )
        )
            .withLocation(cobblemonResource("pipeline/berries"))
            .build()
    }

    val BERRY_LAYER: RenderType = RenderType.create(
        "berries",
        RenderSetup.builder(berryPipeline)
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS) {
                RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST, true)
            }
            .useLightmap()
            .affectsCrumbling()
            .setOutline(OutlineProperty.AFFECTS_OUTLINE)
            .createRenderSetup()
    )

    val ENTITY_TRANSLUCENT: BiFunction<Identifier, Boolean, RenderType> =
        Util.memoize { texture: Identifier, affectsOutline: Boolean ->
            RenderType.create(
                "entity_translucent",
                RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                    .withTexture("Sampler0", texture) {
                        RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST, false)
                    }
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(if (affectsOutline) OutlineProperty.AFFECTS_OUTLINE else OutlineProperty.NONE)
                    .createRenderSetup()
            )
        }

    val ENTITY_CUTOUT: Function<Identifier, RenderType> = Util.memoize { texture: Identifier ->
        RenderType.create(
            "entity_cutout",
            // Culling was enabled by the old composite state's default. The similarly
            // named ENTITY_CUTOUT pipeline disables it, so use the culling variant.
            RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT_CULL)
                .withTexture("Sampler0", texture) {
                    RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST, false)
                }
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
        )
    }
}

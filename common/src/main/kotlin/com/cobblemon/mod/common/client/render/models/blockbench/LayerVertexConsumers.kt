/*
 * Copyright (C) 2023 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. https://mozilla.org/MPL/2.0/
 */
package com.cobblemon.mod.common.client.render.models.blockbench

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.rendertype.RenderType

/** Supplies eager consumers while a model's layer texture/pose is being resolved during extraction. */
fun interface LayerVertexConsumers {
    fun getBuffer(renderType: RenderType): VertexConsumer
}

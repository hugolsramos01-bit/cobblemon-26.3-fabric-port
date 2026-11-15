/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns generated textures used by deferred render submissions. Textures registered in one frame
 * remain available until [beginFrame] is called for the following frame.
 */
object TransientRenderTextures {
    private val nextId = AtomicLong()
    private val currentFrame = mutableListOf<Identifier>()

    fun register(texture: DynamicTexture): Identifier {
        val identifier = Identifier.fromNamespaceAndPath(
            "cobblemon",
            "transient/interpolated/${nextId.getAndIncrement()}"
        )
        Minecraft.getInstance().textureManager.register(identifier, texture)
        currentFrame += identifier
        return identifier
    }

    /** Called once before rendering a new frame, after the previous frame's submissions were flushed. */
    @JvmStatic
    fun beginFrame() {
        currentFrame.forEach(Minecraft.getInstance().textureManager::release)
        currentFrame.clear()
    }

    /** Releases textures when the client shuts down. */
    @JvmStatic
    fun close() {
        val textureManager = Minecraft.getInstance().textureManager
        currentFrame.forEach(textureManager::release)
        currentFrame.clear()
    }
}

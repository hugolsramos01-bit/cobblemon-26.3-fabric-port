/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.atlas

import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

class CobblemonAtlas(val textureAtlas: TextureAtlas) {
    @Suppress("UNUSED_PARAMETER")
    constructor(textureManager: TextureManager, atlasId: Identifier, sourcePath: Identifier) : this(
        Minecraft.getInstance().atlasManager.getAtlasOrThrow(atlasId)
    )

    fun getSprite(identifier: Identifier): TextureAtlasSprite = textureAtlas.getSprite(identifier)
}

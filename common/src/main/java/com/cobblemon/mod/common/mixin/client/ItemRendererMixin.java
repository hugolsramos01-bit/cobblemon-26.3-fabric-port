/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/*
    I think it would be nice to maybe use the existing mixin we have for custom item rendering [BuiltinModelItemRendererMixin]
    Obviously that would require changes. Potentially each item renderer defines what modes it overrides for?
 */
// 26.3's item-definition and ItemModelResolver pipeline replaced ItemRenderer/BakedModel entirely.
// Keep the legacy target optional until dynamic Poké Ball/phone/TM selection is ported to item definitions.
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.entity.ItemRenderer", remap = false)
public abstract class ItemRendererMixin {
}

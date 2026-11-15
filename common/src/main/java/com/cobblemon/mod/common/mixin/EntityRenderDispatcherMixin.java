/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.client.CobblemonClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.PlayerModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Shadow private Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>> playerRenderers;

    @org.spongepowered.asm.mixin.injection.Inject(method = "onResourceManagerReload", at = @org.spongepowered.asm.mixin.injection.At("TAIL"))
    public void resourceManagerReloadHook(ResourceManager resourceManager, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CobblemonClient.INSTANCE.onAddLayer(this.playerRenderers);
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.entity.MountedLivingEntityRenderer;
import com.cobblemon.mod.common.duck.MountedRiderRenderState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void cobblemon$captureRiderPose(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        // Always clear the attachment when unmounted, including when a renderer reuses a state.
        ((MountedRiderRenderState) state).cobblemon$setRiderPose(
                entity.getVehicle() instanceof PokemonEntity pokemon
                        ? MountedLivingEntityRenderer.INSTANCE.capture(entity, pokemon, partialTick) : null);
    }

    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void cobblemon$alignRiderToSeat(LivingEntityRenderState state, PoseStack stack, float bodyRot, float scale, CallbackInfo ci) {
        MountedLivingEntityRenderer.INSTANCE.apply(state, stack, bodyRot, scale);
    }
}

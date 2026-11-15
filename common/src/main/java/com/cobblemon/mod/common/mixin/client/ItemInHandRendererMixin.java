/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.client.render.player.FirstPersonRecallBallRenderer;
import com.cobblemon.mod.common.item.PokedexItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "HEAD"), cancellable = true)
    private void cobblemon$renderArmWithItem(PlayerRenderState playerRenderState, FirstPersonHandsAndItemsRenderState handsState, float partialTick, float swingProgress, InteractionHand interactionHand, float swing, ItemStack itemStack, float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof PokedexItem) {
            ci.cancel();
        }
        else if (interactionHand == InteractionHand.MAIN_HAND && FirstPersonRecallBallRenderer.INSTANCE.render(poseStack, collector, packedLight, partialTick)) {
            ci.cancel();
        }
        else if (player instanceof OrientationControllable controllable && controllable.getOrientationController().isActive()) {

        }
    }

    @Redirect(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"))
    private void cobblemon$renderHandswithItems(PoseStack instance, Axis axis, float degrees) {
        if (!(Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof OrientationControllable controllable && controllable.getOrientationController().isActive())) {
            instance.rotateDegrees(axis, degrees);
        }
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.client.render.player.MountedPlayerRenderer;
import com.cobblemon.mod.common.client.render.player.RecallArmAnimationTracker;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
    private boolean cobblemon$handlingRideAnimation;

    @Shadow @Final
    public ModelPart head;
    @Shadow @Final
    public ModelPart hat;
    @Shadow @Final
    public ModelPart rightArm;
    @Shadow @Final
    public ModelPart leftArm;
    @Shadow @Final
    public ModelPart rightLeg;
    @Shadow @Final
    public ModelPart leftLeg;
    @Shadow @Final
    public ModelPart body;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    public void cobblemon$animateRiders(
        HumanoidRenderState renderState,
        CallbackInfo ci
    ) {
        if (cobblemon$handlingRideAnimation) return;

        MountedPlayerRenderer.shouldApplyRootAnimation = false;

        if (!(renderState instanceof AvatarRenderState avatarState)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = minecraft.level.getEntity(avatarState.id);
        if (!(entity instanceof AbstractClientPlayer player)) return;

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof PokemonEntity pokemonEntity && player instanceof OrientationControllable) {
            if (MountedPlayerRenderer.relevantPartsByName.size() <= 1) {
                MountedPlayerRenderer.relevantPartsByName.put("arm_left", leftArm);
                MountedPlayerRenderer.relevantPartsByName.put("arm_right", rightArm);
                MountedPlayerRenderer.relevantPartsByName.put("leg_left", leftLeg);
                MountedPlayerRenderer.relevantPartsByName.put("leg_right", rightLeg);
                MountedPlayerRenderer.relevantPartsByName.put("head", head);
                MountedPlayerRenderer.relevantPartsByName.put("hat", hat);
                MountedPlayerRenderer.relevantPartsByName.put("torso", body);
            }

            var shouldRotatePlayerHead = pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) ->
                    behaviour.shouldRotateRiderHead(settings, state, pokemonEntity)
            );

            // If the player is on a rollable ride then use the ride local rotations to rotate the head
            // This is to allow the player head to face the correct direction when "freelooking"
            // TODO: find a way for this to work on built jars without borking head rotations.
//            if (entity instanceof RidePassenger playerRotater &&
//                vehicle instanceof OrientationControllable controller &&
//                controller.getOrientationController().isActive()) {
//                this.head.xRot = playerRotater.cobblemon$getRideXRot();
//                headPitch = playerRotater.cobblemon$getRideXRot();
//                this.head.yRot = playerRotater.cobblemon$getRideYRot();
//                netHeadYaw = playerRotater.cobblemon$getRideYRot();
//            }

            if (!shouldRotatePlayerHead) {
                renderState.yRot = 0f;
                renderState.xRot = 0f;
                if (this.head != null) {
                    this.head.yRot = 0f;
                    this.head.xRot = 0f;
                }
            }

            MountedPlayerRenderer.shouldApplyRootAnimation = true;
            cobblemon$handlingRideAnimation = true;
            try {
                MountedPlayerRenderer.INSTANCE.animate(
                    (HumanoidModel<?>)(Object)this,
                    pokemonEntity,
                    player,
                    renderState.yRot,
                    renderState.xRot,
                    renderState.ageInTicks,
                    renderState.walkAnimationPos,
                    renderState.walkAnimationSpeed
                );
            } finally {
                cobblemon$handlingRideAnimation = false;
            }
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    public void cobblemon$poseRecallArm(HumanoidRenderState renderState, CallbackInfo ci) {
        if (cobblemon$handlingRideAnimation || !(renderState instanceof AvatarRenderState avatarState)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = minecraft.level.getEntity(avatarState.id);
        if (!(entity instanceof AbstractClientPlayer player) || player.getVehicle() instanceof PokemonEntity) return;

        float raise = RecallArmAnimationTracker.INSTANCE.raiseFactor(player.getUUID());
        if (raise <= 0f) return;
        this.rightArm.xRot = Mth.lerp(raise, this.rightArm.xRot, (float) (-Math.PI / 2.0));
        this.rightArm.yRot = Mth.lerp(raise, this.rightArm.yRot, 0f);
        this.rightArm.zRot = Mth.lerp(raise, this.rightArm.zRot, 0f);
    }
}

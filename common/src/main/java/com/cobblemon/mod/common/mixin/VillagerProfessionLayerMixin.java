/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonVillagerProfessions;
import com.mojang.blaze3d.vertex.PoseStack;
import kotlin.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLayer.class)
public abstract class VillagerProfessionLayerMixin {
    @Inject(method = "renderColoredCutoutModel", at = @At(value = "HEAD"), cancellable = true)
    private static <S extends LivingEntityRenderState> void cobblemon$renderVillagerProfessionLayer(Model<? super S> entityModel, Identifier resourceLocation, PoseStack poseStack, SubmitNodeCollector collector, int light, S state, int overlay, int color, CallbackInfo ci) {
        if (resourceLocation.getNamespace().equals(Cobblemon.MODID)) {
            VillagerProfession profession = null;
            String professionKey = null;
            boolean isZombie = false;

            if (state instanceof VillagerRenderState villager) {
                profession = villager.getVillagerData().profession().value();
                professionKey = villager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().getPath()).orElse(null);
            }
            if (state instanceof ZombieVillagerRenderState zombieVillager) {
                profession = zombieVillager.getVillagerData().profession().value();
                professionKey = zombieVillager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().getPath()).orElse(null);
                isZombie = true;
            }

            Pair<String, String[]> override = CobblemonVillagerProfessions.INSTANCE.getNameTagOverride(profession);
            if (profession != null && override != null && state.nameTag != null) {
                String entityName = ChatFormatting.stripFormatting(state.nameTag.getString());
                boolean nameMatches = false;
                for (String name: override.getSecond())
                    if (entityName.endsWith(name)) {
                        nameMatches = true;
                        break;
                }
                if (nameMatches && professionKey != null && resourceLocation.equals(getProfessionResourceLocation(resourceLocation.getNamespace(), professionKey, isZombie))) {
                    Identifier textureLocation = getProfessionResourceLocation(resourceLocation.getNamespace(), override.getFirst(), isZombie);
                    cobblemon$invokeRenderColoredCutoutModel(entityModel, textureLocation, poseStack, collector, light, state, overlay, color);
                    ci.cancel();
                }
            }
        }
    }

    private static Identifier getProfessionResourceLocation(String namespace, String profession, Boolean isZombie) {
        return Identifier.fromNamespaceAndPath(namespace, "textures/entity/" + (isZombie ? "zombie_villager" : "villager") + "/profession/" + profession + ".png");
    }

    @Invoker("renderColoredCutoutModel")
    private static <S extends LivingEntityRenderState> void cobblemon$invokeRenderColoredCutoutModel(Model<? super S> model, Identifier texture, PoseStack poseStack, SubmitNodeCollector collector, int light, S state, int overlay, int color) {
        throw new AssertionError();
    }
}

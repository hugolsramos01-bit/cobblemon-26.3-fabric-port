/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.item.LeftoversCreatedEvent;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(
            method = "finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN")
    )
    private void cobblemon$createLeavesLeftovers(Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack consumed = (ItemStack) (Object) this;
        if (level.isClientSide() || !(user instanceof ServerPlayer player)) return;
        if (!consumed.is(CobblemonItemTags.LEAVES_LEFTOVERS)
                || level.getRandom().nextDouble() >= Cobblemon.config.getAppleLeftoversChance()) return;

        ItemStack leftovers = new ItemStack(CobblemonItems.LEFTOVERS);
        CobblemonEvents.LEFTOVERS_CREATED.postThen(
                new LeftoversCreatedEvent(player, leftovers),
                event -> null,
                event -> {
                    ItemStack eventItem = event.getLeftovers();
                    if (!player.addItem(eventItem)) {
                        var itemPos = player.getLookAngle().scale(0.5f).add(player.position());
                        level.addFreshEntity(new ItemEntity(level, itemPos.x(), itemPos.y(), itemPos.z(), eventItem));
                    }
                    return null;
                }
        );
    }
}

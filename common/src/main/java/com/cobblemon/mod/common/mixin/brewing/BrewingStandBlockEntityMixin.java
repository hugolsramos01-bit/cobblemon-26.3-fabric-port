/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.brewing;

import com.cobblemon.mod.common.CobblemonRecipeTypes;
import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandInput;
import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
@Mixin(BrewingStandBlockEntity.class)
public class BrewingStandBlockEntityMixin {

	@WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;doBrew(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)V"))
	private static void cobblemon$doBrew(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, Operation<Void> original) {
		var recipe = cobblemon$fetchBrewingRecipe(stand, level);
		if(recipe == null) {
			original.call(level, pos, stand);
			return;
		}
		// Keep vanilla's three-bottle behavior while applying Cobblemon's custom recipe output.
		for(int i = 0; i < 3; ++i) {
			if (!stand.getItem(i).isEmpty()){
				stand.setItem(i, recipe.getResult().create());
			}
		}

		ItemStack itemStack = stand.getItem(3);
		itemStack.shrink(1);
		ItemStackTemplate remainder = itemStack.getItem().getCraftingRemainder();
		if (remainder != null) {
			ItemStack itemStack2 = remainder.create();
			if (itemStack.isEmpty()) {
				itemStack = itemStack2;
			} else {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), itemStack2);
			}
		}

		stand.setItem(3, itemStack);
		level.levelEvent(LevelEvent.SOUND_BREWING_STAND_BREW, pos, 0);
	}

	@WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;isBrewable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)Z"))
	private static boolean cobblemon$isBrewable(ServerLevel level, BrewingStandBlockEntity stand, Operation<Boolean> original) {
		return cobblemon$fetchBrewingRecipe(stand, level) != null || original.call(level, stand);
	}

	@Unique
	private static BrewingStandRecipe cobblemon$fetchBrewingRecipe(BrewingStandBlockEntity stand, ServerLevel level) {
		ItemStack ingredient = stand.getItem(3);
		var bottles = java.util.List.of(stand.getItem(0), stand.getItem(1), stand.getItem(2));

		boolean allBottlesEmpty = true;
		for (ItemStack bottle : bottles) {
			if (!bottle.isEmpty()) {
				allBottlesEmpty = false;
				break;
			}
		}

		if (ingredient.isEmpty() || allBottlesEmpty) {
			return null;
		}

		BrewingStandInput input = new BrewingStandInput(ingredient, bottles);
		RecipeManager recipeManager = level.getServer().getRecipeManager();

		Optional<RecipeHolder<BrewingStandRecipe>> recipeHolder =
				recipeManager.getRecipeFor(CobblemonRecipeTypes.INSTANCE.getBREWING_STAND(), input, level);

		return recipeHolder.map(RecipeHolder::value).orElse(null);
	}

	@Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
	private void cobblemon$canPlaceItem(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack == null || stack.isEmpty()) return;

		BrewingStandBlockEntity self = (BrewingStandBlockEntity) (Object) this;
		ServerLevel level = self.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;

		if (level != null) {

			var rm = level.getServer().getRecipeManager();

			//Input slot
			if (slot == 3) {
				if (BrewingStandRecipe.Companion.isInput(stack, rm)) {
					cir.setReturnValue(true);
					return;
				}
			}
			// Output slots
			if (slot >= 0 && slot < 3) {
				if (BrewingStandRecipe.Companion.isBottle(stack, rm)) {
					if (self.getItem(slot).isEmpty()) {
						cir.setReturnValue(true);
					} else {
						cir.setReturnValue(false);
					}
				}
			}
		}
	}
}

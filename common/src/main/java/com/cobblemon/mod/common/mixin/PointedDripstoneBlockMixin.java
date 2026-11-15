/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.api.tags.CobblemonBlockTags;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PointedDripstoneBlock.class)
public abstract class PointedDripstoneBlockMixin {

    @WrapOperation(
            method = "canGrow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/SpeleothemBlock;canGrow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean cobblemon$validateMoonStoneDripstone(PointedDripstoneBlock instance, LevelReader level, BlockPos pos, Operation<Boolean> original) {
        return original.call(instance, level, pos) || level.getBlockState(pos.above()).is(CobblemonBlockTags.DRIPSTONE_GROWABLE);
    }

}

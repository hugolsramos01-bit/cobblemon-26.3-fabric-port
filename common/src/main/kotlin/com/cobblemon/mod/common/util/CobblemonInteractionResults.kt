/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack

/** Keeps the old item-use return semantics in one place during the 26.3 port. */
object CobblemonInteractionResults {
    fun success(stack: ItemStack): InteractionResult = InteractionResult.SUCCESS.heldItemTransformedTo(stack)

    fun consume(stack: ItemStack): InteractionResult = InteractionResult.CONSUME.heldItemTransformedTo(stack)

    fun fail(stack: ItemStack): InteractionResult = InteractionResult.FAIL

    fun pass(stack: ItemStack): InteractionResult = InteractionResult.PASS

    fun sidedSuccess(stack: ItemStack, clientSide: Boolean): InteractionResult =
        (if (clientSide) InteractionResult.SUCCESS else InteractionResult.CONSUME).heldItemTransformedTo(stack)

    fun sidedSuccess(clientSide: Boolean): InteractionResult =
        if (clientSide) InteractionResult.SUCCESS else InteractionResult.CONSUME

    fun withStack(result: InteractionResult, stack: ItemStack): InteractionResult =
        if (result is InteractionResult.Success) result.heldItemTransformedTo(stack) else result
}

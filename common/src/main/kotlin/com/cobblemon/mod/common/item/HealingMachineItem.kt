/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.item

import java.util.function.Consumer
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.block.Block

internal fun appendHealingMachineTooltip(tooltip: Consumer<Component>) {
    tooltip.accept(Component.translatable("block.cobblemon.healing_machine.tooltip1").withStyle(ChatFormatting.GRAY))
    tooltip.accept(Component.translatable("block.cobblemon.healing_machine.tooltip2").withStyle(ChatFormatting.GRAY))
}

class HealingMachineItem(block: Block, properties: Properties) : BlockItem(block, properties.useBlockDescriptionPrefix()) {
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: TooltipDisplay,
        tooltip: Consumer<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, display, tooltip, flag)
        appendHealingMachineTooltip(tooltip)
    }
}

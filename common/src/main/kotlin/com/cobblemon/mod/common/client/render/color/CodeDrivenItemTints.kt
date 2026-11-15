/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.color

import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.renderer.item.CompositeModel
import net.minecraft.client.renderer.item.ConditionalItemModel
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.RangeSelectItemModel
import net.minecraft.client.renderer.item.SelectItemModel
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty

/** Preserve model selection/transforms while replacing the three legacy Cobblemon tint layers. */
fun applyCodeDrivenItemTints(model: ItemModel.Unbaked, provider: LayeredItemTintProvider): ItemModel.Unbaked = when (model) {
    is CuboidItemModelWrapper.Unbaked -> {
        val tints = List<ItemTintSource>(maxOf(3, model.tints().size)) { index ->
            if (index < 3) LayeredItemTintSource(provider, index) else model.tints()[index]
        }
        CuboidItemModelWrapper.Unbaked(model.model(), model.transformation(), tints)
    }
    is CompositeModel.Unbaked -> CompositeModel.Unbaked(model.models().map { applyCodeDrivenItemTints(it, provider) }, model.transformation())
    is ConditionalItemModel.Unbaked -> ConditionalItemModel.Unbaked(model.transformation(), model.property(),
        applyCodeDrivenItemTints(model.onTrue(), provider), applyCodeDrivenItemTints(model.onFalse(), provider))
    is RangeSelectItemModel.Unbaked -> RangeSelectItemModel.Unbaked(model.transformation(), model.property(), model.scale(),
        model.entries().map { RangeSelectItemModel.Entry(it.threshold(), applyCodeDrivenItemTints(it.model(), provider)) },
        model.fallback().map { applyCodeDrivenItemTints(it, provider) })
    is SelectItemModel.Unbaked -> SelectItemModel.Unbaked(model.transformation(), tintSwitch(model.unbakedSwitch(), provider),
        model.fallback().map { applyCodeDrivenItemTints(it, provider) })
    else -> model
}

private fun <T : Any, P : SelectItemModelProperty<T>> tintSwitch(source: SelectItemModel.UnbakedSwitch<P, T>, provider: LayeredItemTintProvider): SelectItemModel.UnbakedSwitch<P, T> =
    SelectItemModel.UnbakedSwitch(source.property(), source.cases().map {
        SelectItemModel.SwitchCase(it.values(), applyCodeDrivenItemTints(it.model(), provider))
    })

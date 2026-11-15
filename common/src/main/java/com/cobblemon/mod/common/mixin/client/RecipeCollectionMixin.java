/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

@Mixin(RecipeCollection.class)
public class RecipeCollectionMixin {
    // The legacy canCraft hook was removed with the RecipeDisplay recipe-book API in 26.3;
    // vanilla RecipeCollection.selectRecipes now computes these states from display placement data.
}

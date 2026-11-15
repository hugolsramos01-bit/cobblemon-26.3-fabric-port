/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.CobblemonRecipeCategories;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow private ClientRecipeBook book;
    @Shadow private int width;
    @Shadow private int xOffset;
    @Shadow private int height;
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;

    @Inject(method = "updateTabs", at = @At("HEAD"), cancellable = true)
    private void cobblemon$updateCookingTabs(boolean animate, CallbackInfo ci) {
        ExtendedRecipeBookCategory search = CobblemonRecipeCategories.COOKING_POT_SEARCH.toVanillaCategory();
        if (tabButtons.stream().noneMatch(tab -> tab.getCategory() == search)) return;
        int x = (width - 147) / 2 - xOffset - 30;
        int y = (height - 166) / 2 + 3;
        int index = 0;
        for (RecipeBookTabButton tab : tabButtons) {
            ExtendedRecipeBookCategory category = tab.getCategory();
            if (category == search || category instanceof SearchRecipeBookCategory) {
                tab.visible = true;
                tab.setPosition(x, y + 27 * index++);
            } else if (tab.updateVisibility(book)) {
                tab.setPosition(x, y + 27 * index++);
                tab.startAnimation(book, animate);
            }
        }
        ci.cancel();
    }
}

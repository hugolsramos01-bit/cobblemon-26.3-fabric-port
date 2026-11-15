/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.mixin.client;

import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

/** Protect server tags while JEI 31.9.0.58 builds its vanilla recipe fallback. */
@Pseudo
@Mixin(targets = "mezz.jei.common.recipes.VanillaClientRecipeLoader", remap = false)
public abstract class JeiVanillaClientRecipeLoaderMixin {
    @Redirect(
        method = "loadVanillaRecipeRegistry",
        at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V", ordinal = 0),
        require = 1,
        remap = false
    )
    private static void cobblemon$preserveServerTags(
        List<Registry.PendingTags<?>> pendingTags,
        Consumer<? super Registry.PendingTags<?>> apply
    ) {
        // These pending tags belong to the live client registry. Applying vanilla-only
        // contents discards synchronized mod/datapack tags and breaks recipe packets
        // and item tag checks. JEI already built temporary lookups for recipe loading.
        // The second forEach, for JEI's newly created world registries, still applies.
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.api.snowstorm.ParticleMaterials;
import com.cobblemon.mod.common.client.particle.SnowstormParticleGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleGroup;

@Mixin(ParticleEngine.class)
final class ParticleManagerMixin {
    @Mutable
    @Final
    @Shadow
    private static List<ParticleRenderType> RENDER_ORDER;

    @Inject(at = @At("RETURN"), method = "<clinit>")
    private static void lodestone$addTypes(CallbackInfo ci) {
        // Fabric's particle group registry sorts this list during initialization, so it must stay mutable.
        RENDER_ORDER = new ArrayList<>(RENDER_ORDER);
        RENDER_ORDER.add(ParticleMaterials.INSTANCE.getSNOWSTORM());
    }

    @Inject(method = "createParticleGroup", at = @At("HEAD"), cancellable = true)
    private void cobblemon$createSnowstormGroup(ParticleRenderType type, CallbackInfoReturnable<ParticleGroup<?>> cir) {
        if (type.equals(ParticleMaterials.INSTANCE.getSNOWSTORM())) {
            cir.setReturnValue(new SnowstormParticleGroup((ParticleEngine) (Object) this));
        }
    }
}

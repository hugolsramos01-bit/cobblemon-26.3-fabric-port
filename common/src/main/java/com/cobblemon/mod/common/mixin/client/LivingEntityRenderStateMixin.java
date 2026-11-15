/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.duck.MountedRiderRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements MountedRiderRenderState {
    @Unique private Matrix4f cobblemon$riderPose;

    @Override
    public @Nullable Matrix4fc cobblemon$getRiderPose() {
        return cobblemon$riderPose;
    }

    @Override
    public void cobblemon$setRiderPose(@Nullable Matrix4fc pose) {
        cobblemon$riderPose = pose == null ? null : new Matrix4f(pose);
    }
}

/*
 * Copyright (C) 2026 Cobblemon Contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package com.cobblemon.mod.common.duck;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

/** Frame-local seat transform; rendering must not re-read the live mount or passenger. */
public interface MountedRiderRenderState {
    @Nullable Matrix4fc cobblemon$getRiderPose();
    void cobblemon$setRiderPose(@Nullable Matrix4fc pose);
}

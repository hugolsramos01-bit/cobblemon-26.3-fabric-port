/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState

class BerryBlockEntityRenderState : BlockEntityRenderState() {
    var berryGeometry = FrozenModelGeometry(emptyList())
    var mulchGeometry = FrozenModelGeometry(emptyList())
}

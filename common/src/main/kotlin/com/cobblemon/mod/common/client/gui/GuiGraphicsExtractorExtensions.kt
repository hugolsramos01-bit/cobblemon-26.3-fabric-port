/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle

/** Returns the active clip so deferred GUI pictures stay inside their widget's scissor. */
fun GuiGraphicsExtractor.getScissorArea(): ScreenRectangle {
    val viewport = ScreenRectangle(0, 0, guiWidth(), guiHeight())
    val clip = scissorStack.peek() ?: return viewport
    // Animated battle tiles can be entirely off screen. A deferred PIP must not
    // reach the GPU with a clip that becomes zero-sized after viewport clipping.
    return clip.intersection(viewport) ?: ScreenRectangle(0, 0, 0, 0)
}

/** Returns a positive-area child clip after applying the current clip and GUI viewport. */
fun GuiGraphicsExtractor.getVisibleScissorArea(rectangle: ScreenRectangle): ScreenRectangle? {
    val visible = rectangle.intersection(getScissorArea()) ?: return null
    return visible.takeIf { it.width() > 0 && it.height() > 0 }
}

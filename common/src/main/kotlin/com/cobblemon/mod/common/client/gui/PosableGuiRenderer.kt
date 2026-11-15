/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.client.render.block.FrozenModelGeometry
import com.cobblemon.mod.common.client.render.block.FrozenModelLayer
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.texture.OverlayTexture
import org.joml.Matrix4f
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.joml.Vector3f
import kotlin.math.ceil
import kotlin.math.floor

/** Immutable item render data attached to a posable GUI model for the same PIP pass. */
data class PosableGuiItemRenderState(
    val item: ItemStackRenderState,
    val pose: Matrix4f,
    val light: Int
)

/** Immutable frame data for rendering one posable model into the GUI's picture-in-picture pipeline. */
class PosableGuiRenderState(
    val texture: Identifier,
    val geometry: FrozenModelGeometry,
    val layers: List<FrozenModelLayer>,
    val items: List<PosableGuiItemRenderState>,
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int,
    private val scissor: ScreenRectangle,
    val modelPose: Matrix4f
) : PictureInPictureRenderState {
    private val poseMatrix: Matrix3x2fc = Matrix3x2f()
    override fun x0() = left
    override fun y0() = top
    override fun x1() = right
    override fun y1() = bottom
    override fun scale() = 1F
    override fun scissorArea(): ScreenRectangle = scissor
    override fun bounds() = PictureInPictureRenderState.getBounds(left, top, right, bottom, poseMatrix, scissor)
    override fun pose(): Matrix3x2fc = poseMatrix
}

/** Renders captured Cobblemon model geometry through Minecraft's deferred GUI pipeline. */
class PosableGuiRenderer : PictureInPictureRenderer<PosableGuiRenderState>() {
    override fun getRenderStateClass() = PosableGuiRenderState::class.java

    override fun renderToTexture(state: PosableGuiRenderState, pose: PoseStack, collector: SubmitNodeCollector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        val geometry = state.geometry
        pose.pushPose()
        try {
            // Native PIP starts at (width / 2, height), with inverted depth.
            // Preserve the authored GUI coordinates rather than centering each model:
            // profile/portrait translations position the model inside its caller's panel.
            pose.translate(
                -state.x0() - (state.x1() - state.x0()) / 2F,
                -state.y1().toFloat(),
                0F
            )
            pose.scale(1F, 1F, -1F)
            pose.mulPose(state.modelPose)
            if (!geometry.isEmpty()) {
                collector.submitCustomGeometry(pose, RenderTypes.entityCutout(state.texture)) { capturedPose, consumer ->
                    geometry.draw(capturedPose, consumer)
                }
            }
            state.layers.forEach { it.submit(pose, collector) }
            for (item in state.items) {
                pose.pushPose()
                try {
                    pose.mulPose(item.pose)
                    item.item.submit(pose, collector, item.light, OverlayTexture.NO_OVERLAY, 0)
                } finally {
                    pose.popPose()
                }
            }
        } finally {
            pose.popPose()
        }
    }

    override fun getTextureLabel() = "cobblemon:posable_gui_model"
}

/** Adds a captured model to this frame without retaining mutable entity or model state. */
fun GuiRenderState.submitPosableModel(
    texture: Identifier,
    geometry: FrozenModelGeometry,
    layers: List<FrozenModelLayer>,
    items: List<PosableGuiItemRenderState> = emptyList(),
    scissorArea: ScreenRectangle,
    pose: Matrix3x2fc
) {
    // Bake GUI scaling into the PIP pass, so its texture is allocated at the actual
    // screen resolution instead of enlarging a small texture after rendering.
    val modelPose = Matrix4f()
        .m00(pose.m00()).m01(pose.m01())
        .m10(pose.m10()).m11(pose.m11())
        .m30(pose.m20()).m31(pose.m21())
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    val point = Vector3f()
    fun include(x: Float, y: Float, z: Float) {
        modelPose.transformPosition(point.set(x, y, z))
        minX = minOf(minX, point.x)
        minY = minOf(minY, point.y)
        maxX = maxOf(maxX, point.x)
        maxY = maxOf(maxY, point.y)
    }
    geometry.visitPositions(::include)
    layers.forEach { it.geometry.visitPositions(::include) }
    for (item in items) {
        item.item.visitExtents { extent ->
            val itemPoint = item.pose.transformPosition(Vector3f(extent))
            include(itemPoint.x, itemPoint.y, itemPoint.z)
        }
    }
    if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) return
    // Include a pixel of rasterization padding, then crop to the caller's scissor.
    val left = maxOf(floor(minX).toInt() - 1, scissorArea.left())
    val top = maxOf(floor(minY).toInt() - 1, scissorArea.top())
    val right = minOf(ceil(maxX).toInt() + 1, scissorArea.right())
    val bottom = minOf(ceil(maxY).toInt() + 1, scissorArea.bottom())
    if (right <= left || bottom <= top) return
    addPicturesInPictureState(
        PosableGuiRenderState(
            texture, geometry, layers, items.toList(), left, top, right, bottom,
            scissorArea, modelPose
        )
    )
}

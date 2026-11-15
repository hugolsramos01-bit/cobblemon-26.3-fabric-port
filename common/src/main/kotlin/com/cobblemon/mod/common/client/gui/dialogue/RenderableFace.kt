/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.dialogue

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.drawPosablePortrait
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.entity.npc.NPCEntity
import java.util.UUID
import kotlin.math.atan
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Some time of face that can be rendered in a dialogue.
 *
 * @author Hiroku
 * @since January 1st, 2024
 */
sealed interface RenderableFace {
    val isLeftSide: Boolean
    val struct: QueryStruct
    fun render(GuiGraphicsExtractor: GuiGraphicsExtractor, partialTicks: Float, x0: Int, y0: Int, x1: Int, y1: Int)
}

class PlayerRenderableFace(val playerId: UUID, override val isLeftSide: Boolean) : RenderableFace {
    override val struct: QueryStruct = QueryStruct(hashMapOf())
    override fun render(GuiGraphicsExtractor: GuiGraphicsExtractor, partialTicks: Float, x0: Int, y0: Int, x1: Int, y1: Int) {
        val entity = Minecraft.getInstance().level?.getPlayerByUUID(playerId) ?: return
        // All of the maths below is shamelessly stolen from InventoryScreen.drawEntity.
        // the -20 and 5 divided by 40 are for configuring the yaw and pitch tilt of the body and head respectively.
        // For more information, pray for divine inspiration or something idk.
        val f = atan((-40 / 40.0f).toDouble()).toFloat()
        val g = atan((-10 / 40.0f).toDouble()).toFloat()
        val quaternionf = Quaternionf().rotateZ(Math.PI.toFloat())
        val quaternionf2 = Quaternionf().rotateX(g * 20.0f * (Math.PI.toFloat() / 180))
        quaternionf.mul(quaternionf2)
        // Render state extraction replaces the old direct-entity inventory renderer in 26.3.
        // Apply the same portrait angles to the snapshot so the live player entity is untouched.
        val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(entity)
        val renderState = renderer.createRenderState(entity, partialTicks) as? LivingEntityRenderState ?: return
        renderState.bodyRot = 180.0F + f * 20.0F
        renderState.yRot = (180.0F + f * 40.0F) * if (isLeftSide) 1 else -1
        renderState.xRot = 0F
        renderState.shadowPieces.clear()
        renderState.outlineColor = 0
        val size = 37F
        GuiGraphicsExtractor.entity(
            renderState,
            size,
            Vector3f(0F, renderState.boundingBoxHeight / 2F + 0.0625F, 0F),
            quaternionf,
            quaternionf2,
            x0,
            y0,
            x1,
            y1
        )
    }
}

class ReferenceRenderableFace(val entity: PosableEntity, override val isLeftSide: Boolean): RenderableFace {
    val state = entity.delegate as PosableState
    override val struct: QueryStruct
        get() = state.runtime.environment.query
    override fun render(GuiGraphicsExtractor: GuiGraphicsExtractor, partialTicks: Float, x0: Int, y0: Int, x1: Int, y1: Int) {
        val state = this.state
        if (state is PokemonClientDelegate) {
            state.currentAspects = state.currentEntity.pokemon.aspects
            drawPosablePortrait(
                identifier = state.currentEntity.pokemon.species.resourceIdentifier,
                contextScale = state.currentEntity.pokemon.form.baseScale,
                context = GuiGraphicsExtractor,
                state = state,
                reversed = !isLeftSide,
                partialTicks = 0F // It's already being rendered potentially so we don't need to tick the state.
            )
        } else if (state is NPCClientDelegate) {
            entity as NPCEntity
            state.currentAspects = entity.aspects
            val limbSwing = entity.walkAnimation.position(partialTicks)
            val limbSwingAmount = entity.walkAnimation.speed(partialTicks)
            drawPosablePortrait(
                identifier = state.npcEntity.resourceIdentifier,
                context = GuiGraphicsExtractor,
                state = state,
                reversed = !isLeftSide,
                partialTicks = 0F, // It's already being rendered potentially so we don't need to tick the state.
                limbSwing = limbSwing,
                limbSwingAmount = limbSwingAmount,
                ageInTicks = entity.age.toFloat(),
            )
        }
    }
}

class ArtificialRenderableFace(
    val modelType: String,
    val identifier: Identifier,
    val aspects: Set<String>,
    override val isLeftSide: Boolean
): RenderableFace {
    val state = FloatingState()
    override val struct: QueryStruct
        get() = state.runtime.environment.query

    override fun render(GuiGraphicsExtractor: GuiGraphicsExtractor, partialTicks: Float, x0: Int, y0: Int, x1: Int, y1: Int) {
        val state = this.state
        state.currentAspects = aspects
        if (modelType == "pokemon") {
            val species = PokemonSpecies.getByIdentifier(identifier) ?: run {
                Cobblemon.LOGGER.error("Unable to find species for $identifier for a dialogue face. Defaulting to first species.")
                PokemonSpecies.species.first()
            }
            drawPosablePortrait(
                identifier = species.resourceIdentifier,
                context = GuiGraphicsExtractor,
                contextScale = species.getForm(aspects).baseScale,
                state = state,
                reversed = !isLeftSide,
                partialTicks = partialTicks
            )
        } else if (modelType == "npc") {
            drawPosablePortrait(
                identifier = identifier,
                context = GuiGraphicsExtractor,
                state = state,
                reversed = !isLeftSide,
                partialTicks = partialTicks
            )
        }
    }
}

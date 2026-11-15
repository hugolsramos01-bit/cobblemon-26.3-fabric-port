/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.isPokemonEntity
import com.cobblemon.mod.common.util.shoulderEntityData
import com.mojang.blaze3d.vertex.PoseStack
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

class PokemonOnShoulderRenderer(renderLayerParent: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    RenderLayer<AvatarRenderState, PlayerModel>(renderLayerParent) {

    val context = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
    }

    private val heldItemRenderer = HeldItemRenderer()

    var leftState = FloatingState()
    var lastRenderedLeft: ShoulderData? = null
    var rightState = FloatingState()
    var lastRenderedRight: ShoulderData? = null

    override fun submit(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        state: AvatarRenderState,
        limbSwing: Float,
        limbSwingAmount: Float
    ) {
        val player = Minecraft.getInstance().level?.getEntity(state.id) as? Player ?: return
        val realPartialTicks = Minecraft.getInstance().deltaTracker.realtimeDeltaTicks
        renderShoulder(matrixStack, collector, packedLight, player, state, realPartialTicks, state.walkAnimationPos, state.walkAnimationSpeed, true)
        renderShoulder(matrixStack, collector, packedLight, player, state, realPartialTicks, state.walkAnimationPos, state.walkAnimationSpeed, false)
    }

    fun configureState(
        state: FloatingState,
        model: PosableModel,
        leftShoulder: Boolean,
        shoulderData: ShoulderData
    ): FloatingState {
        state.currentModel = model
        state.setPoseToFirstSuitable(if (leftShoulder) PoseType.SHOULDER_LEFT else PoseType.SHOULDER_RIGHT)

        state.runtime.environment.query.addFunction("is_holding_item") {
            return@addFunction DoubleValue(shoulderData.shownItem.let {
                !it.isEmpty && !it.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && !it.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)
            })
        }
        state.runtime.environment.query.addFunction("is_wearing_hat") {
            return@addFunction DoubleValue(
                shoulderData.shownItem.`is`(
                    CobblemonItemTags.WEARABLE_HAT_ITEMS
                )
            )
        }
        state.runtime.environment.query.addFunction("is_wearing_face") {
            return@addFunction DoubleValue(
                shoulderData.shownItem.`is`(
                    CobblemonItemTags.WEARABLE_FACE_ITEMS
                )
            )
        }

        return state
    }

    private fun renderShoulder(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        livingEntity: Player,
        avatarState: AvatarRenderState,
        partialTicks: Float,
        limbSwing: Float,
        limbSwingAmount: Float,
        pLeftShoulder: Boolean
    ) {
        val compoundTag = livingEntity.shoulderEntityData(pLeftShoulder)
        if (compoundTag.isPokemonEntity()) {
            val cache = playerCache.getOrPut(livingEntity.uuid) { ShoulderCache() }
            val shoulderData = shoulderDataFrom(compoundTag)
            if (pLeftShoulder) cache.lastKnownLeft = shoulderData else cache.lastKnownRight = shoulderData

            if (shoulderData == null) {
                // Nothing to do
                return
            }

            matrixStack.pushPose()
            var state = FloatingState()
            state.currentAspects = shoulderData.aspects
            val model = VaryingModelRepository.getPoser(shoulderData.species.resourceIdentifier, state)
            model.context = context
            context.put(RenderContext.SPECIES, shoulderData.species.resourceIdentifier)
            context.put(RenderContext.ASPECTS, shoulderData.aspects)
            val scale = shoulderData.form.baseScale * shoulderData.scaleModifier
            val width = shoulderData.form.hitbox.width * shoulderData.scaleModifier
            val widthOffset = width / 2 - 0.7
            val chestItem = livingEntity.getItemBySlot(EquipmentSlot.CHEST)
            val equippable = chestItem.get(DataComponents.EQUIPPABLE)
            val isChestArmor = equippable != null && equippable.slot() == EquipmentSlot.CHEST
            val armorHeightOffset = if (isChestArmor && !isArmorOffsetBlacklisted(chestItem)) {
                ARMOR_HEIGHT_OFFSET
            } else {
                0F
            }
            // Model submission is deferred in 26.3; establish the player's animated
            // body pose before reading its shoulder attachment transform.
            parentModel.setupAnim(avatarState)
            parentModel.body.translateAndRotate(matrixStack)
            matrixStack.translate(
                if (pLeftShoulder) -widthOffset else widthOffset,
                -1.5 * scale - armorHeightOffset,
                0.0
            )

            matrixStack.scale(scale, scale, scale)
            matrixStack.translate(0F, 1.5F, 0F)

            state = if (pLeftShoulder && !shoulderData.matches(lastRenderedLeft)) {
                leftState = configureState(state, model, true, shoulderData)
                lastRenderedLeft = shoulderData
                leftState
            } else if (!pLeftShoulder && !shoulderData.matches(lastRenderedRight)) {
                rightState = configureState(state, model, false, shoulderData)
                lastRenderedRight = shoulderData
                rightState
            } else {
                if (pLeftShoulder) leftState else rightState
            }
            state.updatePartialTicks(partialTicks)
            context.put(RenderContext.POSABLE_STATE, state)
            state.currentModel = model
            model.applyAnimations(
                entity = null,
                state = state,
                headYaw = avatarState.yRot,
                headPitch = avatarState.xRot,
                limbSwing = limbSwing,
                limbSwingAmount = limbSwingAmount,
                ageInTicks = avatarState.ageInTicks
            )
            val baseCapture = com.cobblemon.mod.common.client.render.block.ModelGeometryCapture()
            val layerCapture = com.cobblemon.mod.common.client.render.block.LayerGeometryCapture()
            model.setLayerContext(layerCapture, state, VaryingModelRepository.getLayers(shoulderData.species.resourceIdentifier, state))
            // Capture model-local vertices. The player's shoulder pose is applied once
            // by the deferred submission below, including the layer passes.
            model.render(context, PoseStack(), baseCapture, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
            model.resetLayerContext()
            val texture = VaryingModelRepository.getTexture(shoulderData.species.resourceIdentifier, state)
            val baseGeometry = baseCapture.freeze()
            collector.submitCustomGeometry(matrixStack, RenderTypes.entityCutout(texture)) { pose, consumer ->
                baseGeometry.draw(pose, consumer)
            }
            layerCapture.freeze().forEach { it.submit(matrixStack, collector) }

            heldItemRenderer.renderOnModel(
                shoulderData.shownItem,
                state,
                matrixStack,
                collector,
                packedLight
            )

            model.setDefault()
            matrixStack.popPose()
        } else {
            val entry = playerCache.getOrPut(livingEntity.uuid) { ShoulderCache() }
            if (pLeftShoulder) entry.lastKnownLeft = null else entry.lastKnownRight = null
        }
    }

    private data class ShoulderCache(
        var lastKnownLeft: ShoulderData? = null,
        var lastKnownRight: ShoulderData? = null
    )

    class ShoulderData(
        val uuid: UUID,
        val species: Species,
        val form: FormData,
        val aspects: Set<String>,
        val scaleModifier: Float,
        val shownItem: ItemStack,
    ) {
        fun matches(other: ShoulderData?): Boolean = other != null
                && uuid == other.uuid
                && species == other.species
                && form == other.form
                && aspects == other.aspects
                && scaleModifier == other.scaleModifier
                && shownItem.item == other.shownItem.item
    }

    companion object {

        private const val ARMOR_HEIGHT_OFFSET = 1F / 16F
        private val playerCache = hashMapOf<UUID, ShoulderCache>()

        private fun isArmorOffsetBlacklisted(itemStack: ItemStack): Boolean {
            val itemIdentifier = BuiltInRegistries.ITEM.getKey(itemStack.item)
            return itemIdentifier in Cobblemon.config.shoulderMountArmorOffsetBlacklist
        }

        private fun extractUuid(shoulderNbt: CompoundTag): UUID {
            if (!shoulderNbt.contains(DataKeys.SHOULDER_UUID)) {
                return shoulderNbt.getCompound(DataKeys.POKEMON).orElseThrow().read(DataKeys.POKEMON_UUID, UUIDUtil.CODEC).orElseThrow()
            }
            return shoulderNbt.read(DataKeys.SHOULDER_UUID, UUIDUtil.CODEC).orElseThrow()
        }

        private fun extractData(shoulderNbt: CompoundTag, pokemonUUID: UUID): ShoulderData? {
            // To not crash with existing ones, this will still have the aspect issue
            if (!shoulderNbt.contains(DataKeys.SHOULDER_SPECIES)) {
                return Pokemon.CLIENT_CODEC.decode(NbtOps.INSTANCE, shoulderNbt.getCompound(DataKeys.POKEMON).orElseThrow())
                    .map { it.first }
                    .mapOrElse({
                        ShoulderData(
                            pokemonUUID,
                            it.species,
                            it.form,
                            it.aspects,
                            it.effectiveScale,
                            it.heldItem
                        )
                    }, { null })
            }
            val species =
                PokemonSpecies.getByIdentifier(Identifier.parse(shoulderNbt.getStringOr(DataKeys.SHOULDER_SPECIES, "")))
                    ?: return null
            val formName = shoulderNbt.getStringOr(DataKeys.SHOULDER_FORM, "")
            val form = species.forms.firstOrNull { it.name == formName } ?: species.standardForm
            val aspects = shoulderNbt.getListOrEmpty(DataKeys.SHOULDER_ASPECTS).mapNotNull { it.asString().orElse(null) }.toSet()
            val scaleModifier = shoulderNbt.getFloatOr(DataKeys.SHOULDER_SCALE_MODIFIER, 1F)
            val shownItem = Minecraft.getInstance().level?.registryAccess()
                ?.let { access ->
                    ItemStack.CODEC.parse(
                        access.createSerializationContext(NbtOps.INSTANCE),
                        shoulderNbt.getCompoundOrEmpty(DataKeys.SHOULDER_ITEM)
                    ).result().orElse(ItemStack.EMPTY)
                }
                ?: ItemStack.EMPTY
            return ShoulderData(pokemonUUID, species, form, aspects, scaleModifier, shownItem)
        }

        /**
         * Checks if a player has shoulder data cached.
         *
         * @param player The player being checked.
         * @return A [Pair] with [Pair.first] and [Pair.second] being the respective shoulder.
         */
        @JvmStatic
        fun shoulderDataOf(player: Player): Pair<ShoulderData?, ShoulderData?> {
            val cache = playerCache[player.uuid] ?: return Pair(null, null)
            return Pair(cache.lastKnownLeft, cache.lastKnownRight)
        }

        /**
         * extracts shoulder data from compound tag.
         *
         * @param compoundTag The tag being checked
         * @return [ShoulderData] or null
         */
        @JvmStatic
        fun shoulderDataFrom(compoundTag: CompoundTag): ShoulderData? {
            if (compoundTag.isPokemonEntity()) {
                val uuid = this.extractUuid(compoundTag)
                return this.extractData(compoundTag, uuid)
            }
            return null
        }
    }
}

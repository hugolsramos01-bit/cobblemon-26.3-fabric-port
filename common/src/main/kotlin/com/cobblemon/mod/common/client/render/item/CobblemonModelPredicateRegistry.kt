/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.client.pot.CookingQuality
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.mixin.accessor.ConditionalItemModelPropertiesAccessor
import com.cobblemon.mod.common.mixin.accessor.RangeSelectItemModelPropertiesAccessor
import com.cobblemon.mod.common.mixin.accessor.SelectItemModelPropertiesAccessor
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.MapCodec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

object CobblemonModelPredicateRegistry {
    private val castProperty = object : ConditionalItemModelProperty {
        private val codec = MapCodec.unit(this)

        override fun get(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?, seed: Int, displayContext: ItemDisplayContext): Boolean {
            val player = entity as? Player ?: return false
            val fishing = player.fishing as? PokeRodFishingBobberEntity ?: return false
            val rodId = fishing.entityData.get(PokeRodFishingBobberEntity.POKEROD_ID)

            val isMainHand = stack == player.mainHandItem
            var isOffHand = stack == player.offhandItem
            val mainHandItem = player.mainHandItem.item
            val isFishingWithMainHand = mainHandItem is PokerodItem && rodId == mainHandItem.pokeRodId.toString()

            if (isFishingWithMainHand) {
                isOffHand = false
            }

            return (isMainHand && isFishingWithMainHand) || isOffHand
        }

        override fun type(): MapCodec<out ConditionalItemModelProperty> = codec
    }

    private val ponigiriOverlayProperty = object : RangeSelectItemModelProperty {
        private val codec = MapCodec.unit(this)

        override fun get(stack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float {
            val id = stack.get(CobblemonItemComponents.INGREDIENT)
                ?.ingredientIds
                ?.firstOrNull()
                ?.toString()
                ?: return 0.0f

            return when (id) {
                "minecraft:potato" -> 0.01f
                "minecraft:beetroot" -> 0.02f
                "minecraft:carrot" -> 0.03f
                "minecraft:dried_kelp" -> 0.04f
                "cobblemon:medicinal_leek" -> 0.05f
                "minecraft:red_mushroom" -> 0.06f
                "minecraft:brown_mushroom" -> 0.07f
                "minecraft:pumpkin" -> 0.08f
                "cobblemon:rice" -> 0.09f
                "minecraft:chicken" -> 0.10f
                "minecraft:cod" -> 0.11f
                "minecraft:mutton" -> 0.12f
                "minecraft:porkchop" -> 0.13f
                "minecraft:rabbit" -> 0.14f
                "minecraft:salmon" -> 0.15f
                "minecraft:beef" -> 0.16f
                "minecraft:egg" -> 0.17f
                "minecraft:poisonous_potato" -> 0.18f
                "minecraft:rotten_flesh" -> 0.19f
                "minecraft:golden_carrot" -> 0.20f
                "cobblemon:vivichoke" -> 0.21f
                "cobblemon:tasty_tail" -> 0.22f
                "minecraft:sweet_berries" -> 0.23f
                else -> 0.0f
            }
        }

        override fun type(): MapCodec<out RangeSelectItemModelProperty> = codec
    }

    private val aprijuiceQualityProperty = object : RangeSelectItemModelProperty {
        private val codec = MapCodec.unit(this)

        override fun get(stack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float {
            val rideBoostsComponent = stack.get(CobblemonItemComponents.RIDE_BOOST) ?: return 0.0f
            return when (rideBoostsComponent.getQuality()) {
                CookingQuality.LOW -> 0.0f
                CookingQuality.MEDIUM -> 0.1f
                CookingQuality.HIGH -> 0.2f
            }
        }

        override fun type(): MapCodec<out RangeSelectItemModelProperty> = codec
    }

    private val tmTypeProperty = object : SelectItemModelProperty<String> {
        private val codec = MapCodec.unit(this)
        private val propertyType = SelectItemModelProperty.Type.create(codec, com.mojang.serialization.Codec.STRING)

        override fun get(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?, seed: Int, displayContext: ItemDisplayContext): String =
            TMMoveComponent.getTMMove(stack)?.elementalType?.showdownId ?: "normal"

        override fun valueCodec() = com.mojang.serialization.Codec.STRING

        override fun type(): SelectItemModelProperty.Type<out SelectItemModelProperty<String>, String> = propertyType
    }

    fun registerPredicates() {
        val conditionalIdMapper = ConditionalItemModelPropertiesAccessor.getIdMapper()
        conditionalIdMapper.put(cobblemonResource("cast"), castProperty.type())
        val idMapper = RangeSelectItemModelPropertiesAccessor.getIdMapper()
        idMapper.put(cobblemonResource("ponigiri_overlay"), ponigiriOverlayProperty.type())
        idMapper.put(cobblemonResource("aprijuice_quality"), aprijuiceQualityProperty.type())
        val selectIdMapper = SelectItemModelPropertiesAccessor.getIdMapper()
        selectIdMapper.put(cobblemonResource("tm_type"), tmTypeProperty.type())
    }
}

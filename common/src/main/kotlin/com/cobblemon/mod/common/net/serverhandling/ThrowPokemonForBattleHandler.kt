/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.server.ThrowPokemonForBattlePacket
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.traceFirstEntityCollision
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext

/** Creates a visual Poké Ball throw that starts a battle only when it hits a valid wild Pokémon. */
object ThrowPokemonForBattleHandler : ServerNetworkPacketHandler<ThrowPokemonForBattlePacket> {
    override fun handle(packet: ThrowPokemonForBattlePacket, server: MinecraftServer, player: ServerPlayer) {
        if (player.isSpectator || BattleRegistry.getBattleByParticipatingPlayer(player) != null) return

        val party = player.party()
        val pokemon = party.get(packet.slot) ?: return
        if (pokemon.isFainted() || pokemon.entity?.isBusy == true) return

        val maxDistance = Cobblemon.config.battleWildMaxDistance
        val target = player.traceFirstEntityCollision(
            entityClass = LivingEntity::class.java,
            ignoreEntity = player,
            maxDistance = maxDistance,
            collideBlock = ClipContext.Fluid.NONE
        ) as? PokemonEntity ?: return
        if (!target.pokemon.isWild() || !target.canBattle(player)) return

        val projectile = EmptyPokeBallEntity(pokemon.caughtBall, player.level(), player).apply {
            isBattleChallengeProjectile = true
            battleChallengeLeadPokemon = pokemon.uuid
            shootFromRotation(player, player.xRot, player.yRot, 0.0F, pokemon.caughtBall.throwPower, 1.0F)
            setPos(position().add(deltaMovement.normalize().scale(1.0)))
            owner = player
        }
        player.level().addFreshEntity(projectile)
    }
}

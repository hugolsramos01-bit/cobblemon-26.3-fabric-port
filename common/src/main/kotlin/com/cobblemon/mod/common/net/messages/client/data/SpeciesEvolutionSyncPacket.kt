/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.util.readList

import com.cobblemon.mod.common.util.writeCollection

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.adapters.RegistryAccessResolver
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import com.google.gson.reflect.TypeToken
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.Identifier

/**
 * Carries the evolutions/preEvolution of every [Species] (and any [FormData] overrides), sent right after
 * [SpeciesRegistrySyncPacket] so that every species is already registered by the time this is decoded. This avoids
 * the cross-species lookup ordering hazards that come with resolving evolutions during the species sync itself.
 */
class SpeciesEvolutionSyncPacket(entries: Collection<SpeciesEvolutionEntry>) : DataRegistrySyncPacket<SpeciesEvolutionEntry, SpeciesEvolutionSyncPacket>(entries) {

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: SpeciesEvolutionEntry) {
        RegistryAccessResolver.withAccess(buffer.registryAccess()) {
            // Complete serialization before writing this entry. An exception must abort
            // the packet, never leave a partial entry behind a successful count prefix.
            val evolutionsJson = PokemonSpecies.gson.toJson(entry.evolutions, EVOLUTION_SET_TYPE)
            val preEvolutionJson = entry.preEvolution?.let { PokemonSpecies.gson.toJson(it, PreEvolution::class.java) }
            val formJson = entry.formOverrides.map { override ->
                Triple(
                    override.formName,
                    override.evolutions?.let { PokemonSpecies.gson.toJson(it, EVOLUTION_SET_TYPE) },
                    override.preEvolution?.let { PokemonSpecies.gson.toJson(it, PreEvolution::class.java) }
                )
            }
            buffer.writeIdentifier(entry.speciesIdentifier)
            buffer.writeString(evolutionsJson)
            buffer.writeNullable(preEvolutionJson) { _, json -> buffer.writeString(json) }
            buffer.writeCollection(formJson) { _, override ->
                buffer.writeString(override.first)
                buffer.writeNullable(override.second) { _, json -> buffer.writeString(json) }
                buffer.writeNullable(override.third) { _, json -> buffer.writeString(json) }
            }
        }
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): SpeciesEvolutionEntry? {
        val identifier = buffer.readIdentifier()
        // Read the whole entry before parsing its JSON, so a semantic decode error
        // cannot make the next entry read JSON as a species identifier.
        val evolutionsJson = buffer.readString()
        val preEvolutionJson = buffer.readNullable { _ -> buffer.readString() }
        val formJson = buffer.readList { _ ->
            Triple(
                buffer.readString(),
                buffer.readNullable { _ -> buffer.readString() },
                buffer.readNullable { _ -> buffer.readString() }
            )
        }
        return try {
            RegistryAccessResolver.withAccess(buffer.registryAccess()) {
                val evolutions: MutableSet<Evolution> = PokemonSpecies.gson.fromJson(evolutionsJson, EVOLUTION_SET_TYPE)
                val preEvolution = preEvolutionJson?.let { PokemonSpecies.gson.fromJson(it, PreEvolution::class.java) }
                val formOverrides = formJson.map { override ->
                    val formEvolutions = override.second?.let { PokemonSpecies.gson.fromJson<MutableSet<Evolution>>(it, EVOLUTION_SET_TYPE) }
                    val formPreEvolution = override.third?.let { PokemonSpecies.gson.fromJson(it, PreEvolution::class.java) }
                    FormEvolutionOverride(override.first, formEvolutions, formPreEvolution)
                }
                SpeciesEvolutionEntry(identifier, evolutions, preEvolution, formOverrides)
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Caught exception decoding evolution data for species {}", identifier, e)
            null
        }
    }

    override fun synchronizeDecoded(entries: Collection<SpeciesEvolutionEntry>) {
        entries.forEach { entry ->
            val species = PokemonSpecies.getByIdentifier(entry.speciesIdentifier)
            if (species == null) {
                Cobblemon.LOGGER.error("Received evolution data for unknown species {}", entry.speciesIdentifier)
                return@forEach
            }
            species.applyEvolutionData(entry.evolutions, entry.preEvolution)
            entry.formOverrides.forEach { override ->
                species.getFormByName(override.formName).applyEvolutionData(override.evolutions, override.preEvolution)
            }
        }
    }

    companion object {
        val ID = cobblemonResource("species_evolution_sync")
        private val EVOLUTION_SET_TYPE = TypeToken.getParameterized(MutableSet::class.java, Evolution::class.java).type
        fun decode(buffer: RegistryFriendlyByteBuf): SpeciesEvolutionSyncPacket = SpeciesEvolutionSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}

class SpeciesEvolutionEntry(
    val speciesIdentifier: Identifier,
    val evolutions: MutableSet<Evolution>,
    val preEvolution: PreEvolution?,
    val formOverrides: List<FormEvolutionOverride>
)

class FormEvolutionOverride(
    val formName: String,
    val evolutions: MutableSet<Evolution>?,
    val preEvolution: PreEvolution?
)

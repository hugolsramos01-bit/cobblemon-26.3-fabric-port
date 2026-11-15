package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonBlocks;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.berry.Berries;
import com.cobblemon.mod.common.api.drop.DropTable;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokedex.CaughtCount;
import com.cobblemon.mod.common.api.pokedex.Dexes;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SeenCount;
import com.cobblemon.mod.common.pokedex.scanner.PokedexEntityData;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.spawning.position.AirborneSpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.sherds.CobblemonSherds;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.net.messages.server.ThrowPokemonForBattlePacket;
import com.cobblemon.mod.common.net.serverhandling.ThrowPokemonForBattleHandler;
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket;
import com.cobblemon.mod.common.block.PCBlock;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.HashSet;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;

/** Server-side smoke checks for the registrations that must survive a fresh 26.3 bootstrap. */
public final class CobblemonBootstrapGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void cobblemonContentIsRegistered(GameTestHelper context) {
        assertRegisteredItem("cobblemon:poke_ball");
        assertRegisteredItem("cobblemon:healing_machine");

        var pokemonId = Identifier.parse("cobblemon:pokemon");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(pokemonId)) {
            throw new AssertionError("Missing Cobblemon Pokémon entity type: " + pokemonId);
        }

        context.succeed();
    }

    @GameTest
    public void cobblemonDataRegistriesAreLoaded(GameTestHelper context) {
        if (PokemonSpecies.count() < 1_000) {
            throw new AssertionError("Expected over 1,000 Pokémon species, found " + PokemonSpecies.count());
        }
        if (Berries.all().size() < 70) {
            throw new AssertionError("Expected at least 70 berries, found " + Berries.all().size());
        }

        context.succeed();
    }

    @GameTest
    public void decoratedPotSherdsHavePatternsAndTextures(GameTestHelper context) throws Exception {
        var patterns = context.getLevel().registryAccess().lookupOrThrow(Registries.DECORATED_POT_PATTERN);
        if (CobblemonSherds.INSTANCE.getAllSherds().size() != 6) {
            throw new AssertionError("Expected all six Cobblemon decorated-pot sherds to be defined");
        }

        for (var sherd : CobblemonSherds.INSTANCE.getAllSherds()) {
            if (patterns.get(sherdKey(sherd.getPatternId())).isEmpty()) {
                throw new AssertionError("Missing decorated-pot pattern " + sherd.getPatternId());
            }
            if (!sherd.getItem().builtInRegistryHolder().is(CobblemonItemTags.POTTERY_SHERDS)) {
                throw new AssertionError("Sherd item is missing from the decorated-pot sherd tag: " + sherd.getPatternId());
            }

            String texturePath = "assets/cobblemon/textures/entity/decorated_pot/" + sherd.getPatternId().getPath() + ".png";
            try (var stream = CobblemonBootstrapGameTest.class.getClassLoader().getResourceAsStream(texturePath)) {
                if (stream == null) {
                    throw new AssertionError("Missing decorated-pot pattern texture " + texturePath);
                }
                byte[] header = stream.readNBytes(24);
                if (header.length != 24 || header[0] != (byte) 0x89 || header[1] != 'P'
                        || header[2] != 'N' || header[3] != 'G') {
                    throw new AssertionError("Invalid decorated-pot pattern PNG " + texturePath);
                }
                int width = java.nio.ByteBuffer.wrap(header, 16, 4).getInt();
                int height = java.nio.ByteBuffer.wrap(header, 20, 4).getInt();
                if (width < 1 || height < 1) {
                    throw new AssertionError("Empty decorated-pot pattern texture " + texturePath);
                }
            }
        }
        context.succeed();
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.entity.DecoratedPotPattern> sherdKey(Identifier id) {
        return net.minecraft.resources.ResourceKey.create(Registries.DECORATED_POT_PATTERN, id);
    }

    @GameTest
    public void cobblemonWorldgenStructuresPoolsLootAndTemplatesLoad(GameTestHelper context) {
        var registryAccess = context.getLevel().registryAccess();
        var structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        var pools = registryAccess.lookupOrThrow(Registries.TEMPLATE_POOL);
        var structureSets = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
        long cobblemonStructures = structures.keySet().stream().filter(id -> id.getNamespace().equals(Cobblemon.MODID)).count();
        long cobblemonPools = pools.keySet().stream().filter(id -> id.getNamespace().equals(Cobblemon.MODID)).count();
        long cobblemonStructureSets = structureSets.keySet().stream().filter(id -> id.getNamespace().equals(Cobblemon.MODID)).count();
        var server = context.getLevel().getServer();
        var cobblemonLootTables = server.getResourceManager().listResources("loot_table",
                id -> id.getNamespace().equals(Cobblemon.MODID));
        for (var id : cobblemonLootTables.keySet()) {
            server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, id));
        }
        if (cobblemonStructures < 60 || cobblemonPools < 200 || cobblemonStructureSets < 2 || cobblemonLootTables.size() < 700) {
            throw new AssertionError("Cobblemon worldgen/loot registries are incomplete: structures=" + cobblemonStructures
                    + ", pools=" + cobblemonPools + ", structure_sets=" + cobblemonStructureSets
                    + ", loot_tables=" + cobblemonLootTables.size());
        }

        var templateManager = server.getStructureTemplateManager();
        var templates = templateManager.listTemplates()
                .filter(id -> id.getNamespace().equals(Cobblemon.MODID))
                .toList();
        if (templates.size() < 1_200) {
            throw new AssertionError("Expected at least 1,200 Cobblemon NBT templates, found " + templates.size());
        }
        for (var id : templates) {
            var template = templateManager.get(id).orElseThrow(() -> new AssertionError("Missing structure template " + id));
            var size = template.getSize();
            if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1) {
                throw new AssertionError("Structure template has invalid dimensions: " + id + " => " + size);
            }
        }

        for (var id : pools.keySet()) {
            if (!id.getNamespace().equals(Cobblemon.MODID)) continue;
            var pool = pools.getValue(id);
            if (pool == null || pool.getTemplates().isEmpty()) {
                throw new AssertionError("Missing or empty Cobblemon template pool " + id);
            }
            for (var weightedElement : pool.getTemplates()) {
                validatePoolElement(weightedElement.getFirst(), templateManager, id.toString());
            }
        }

        // The start pool intentionally includes a weighted empty element, so /place jigsaw can
        // legitimately produce no structure. Place a known NBT template directly to keep the
        // smoke check deterministic while still exercising real block placement.
        var placementTemplateId = net.minecraft.resources.Identifier.parse("cobblemon:habitats/flowerbed_clearing1");
        var placementTemplate = templateManager.get(placementTemplateId)
                .orElseThrow(() -> new AssertionError("Missing disposable-world template " + placementTemplateId));
        var placementPosition = context.absolutePos(new BlockPos(1, 1, 1));
        if (!placementTemplate.placeInWorld(context.getLevel(), placementPosition, placementPosition,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                context.getLevel().getRandom(), 2)) {
            throw new AssertionError("Could not place the Cobblemon flowerbed template in the disposable test world");
        }

        System.out.println("[Cobblemon GameTest] Worldgen loaded: " + cobblemonStructures + " structures, "
                + cobblemonStructureSets + " structure sets, " + cobblemonPools + " pools, "
                + templates.size() + " NBT templates, " + cobblemonLootTables.size() + " loot tables");
        context.succeed();
    }

    private static void validatePoolElement(StructurePoolElement element,
                                            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
                                            String poolId) {
        if (element instanceof SinglePoolElement single) {
            var templateId = single.getTemplateLocation();
            var size = single.getSize(templateManager, Rotation.NONE);
            if (templateId.getNamespace().equals(Cobblemon.MODID)
                    && (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1)) {
                throw new AssertionError("Pool " + poolId + " references invalid template " + templateId + " => " + size);
            }
        } else if (element instanceof ListPoolElement list) {
            for (var child : list.getElements()) validatePoolElement(child, templateManager, poolId);
        } else {
            var size = element.getSize(templateManager, Rotation.NONE);
            if (size.getX() < 0 || size.getY() < 0 || size.getZ() < 0) {
                throw new AssertionError("Pool " + poolId + " has an invalid element size: " + size);
            }
        }
    }

    @GameTest
    public void nationalPokedexCoversEveryImplementedSpecies(GameTestHelper context) {
        var national = Dexes.INSTANCE.getDexEntryMap().get(Identifier.parse("cobblemon:national"));
        if (national == null || national.getEntries().isEmpty()) {
            throw new AssertionError("The National Pokédex definition is missing or has no entries");
        }

        var missing = PokemonSpecies.getImplemented().stream()
                .filter(species -> Dexes.INSTANCE.findDexEntryBySpeciesId(species.getResourceIdentifier()) == null)
                .map(species -> species.getResourceIdentifier().toString())
                .limit(20)
                .toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("Implemented Pokémon are missing from the National Pokédex: " + missing);
        }

        context.succeed();
    }

    @GameTest
    public void pokedexTracksSeenCaughtFormsAndRoundTripsSaveAndClientData(GameTestHelper context) {
        var manager = new PokedexManager(UUID.randomUUID(), new java.util.HashMap<>());
        manager.initialize();
        Pokemon seenEevee = PokemonProperties.Companion.parse("eevee level=24 gender=female shiny=true").create();
        var speciesId = seenEevee.getSpecies().getResourceIdentifier();

        if (manager.getKnowledgeForSpecies(speciesId) != PokedexEntryProgress.UNREGISTERED) {
            throw new AssertionError("A new Pokédex did not start with the species unregistered");
        }

        manager.encounter(seenEevee);
        var record = manager.getSpeciesRecord(speciesId);
        var form = record == null ? null : record.getFormRecord(seenEevee.getForm().getName());
        if (record == null || form == null
                || manager.getKnowledgeForSpecies(speciesId) != PokedexEntryProgress.SEEN
                || form.getKnowledge() != PokedexEntryProgress.SEEN
                || !form.getGenders().contains(seenEevee.getGender())
                || !form.hasSeenShinyState(true)
                || record.getHighestLevel() != -1) {
            throw new AssertionError("Encountering Eevee did not register seen form, gender and shiny without marking it owned");
        }

        manager.obtain(seenEevee);
        Pokemon maleEevee = PokemonProperties.Companion.parse("eevee level=38 gender=male").create();
        manager.obtain(maleEevee);
        Pokemon lowerLevelEevee = PokemonProperties.Companion.parse("eevee level=9 gender=male").create();
        manager.obtain(lowerLevelEevee);
        form = manager.getSpeciesRecord(speciesId).getFormRecord(seenEevee.getForm().getName());
        if (manager.getKnowledgeForSpecies(speciesId) != PokedexEntryProgress.OWNED
                || form.getKnowledge() != PokedexEntryProgress.OWNED
                || !form.getGenders().containsAll(java.util.Set.of(seenEevee.getGender(), maleEevee.getGender()))
                || !form.hasSeenShinyState(true)
                || !form.hasSeenShinyState(false)
                || form.getHighestLevel() != 38
                || manager.getSpeciesRecord(speciesId).getHighestLevel() != 38) {
            throw new AssertionError("Catching repeated Eevee did not preserve ownership, variations and the highest level");
        }

        var entry = Dexes.INSTANCE.findDexEntryBySpeciesId(speciesId);
        if (entry == null
                || manager.getEncounteredForms(entry).isEmpty()
                || manager.getCaughtForms(entry).isEmpty()
                || manager.getGlobalCalculatedValue(SeenCount.INSTANCE) != 1
                || manager.getGlobalCalculatedValue(CaughtCount.INSTANCE) != 1) {
            throw new AssertionError("Pokédex form visibility or seen/caught counters disagree with the recorded data");
        }

        var saved = PokedexManager.Companion.getCODEC().encodeStart(JsonOps.INSTANCE, manager).getOrThrow();
        var restored = PokedexManager.Companion.getCODEC().parse(JsonOps.INSTANCE, saved).getOrThrow();
        if (restored.getKnowledgeForSpecies(speciesId) != PokedexEntryProgress.OWNED
                || restored.getSpeciesRecord(speciesId).getFormRecord("NORMAL").getHighestLevel() != 38) {
            throw new AssertionError("Pokédex save codec did not restore owned progress and form data");
        }

        var outbound = new SetClientPlayerDataPacket(
                PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX(),
                manager.toClientData(),
                true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
        try {
            outbound.encode(buffer);
            var inbound = SetClientPlayerDataPacket.Companion.decode(buffer);
            if (!inbound.isIncremental() || !(inbound.getPlayerData() instanceof ClientPokedexManager clientData)
                    || clientData.getKnowledgeForSpecies(speciesId) != PokedexEntryProgress.OWNED
                    || clientData.getSpeciesRecord(speciesId).getFormRecord("normal").getHighestLevel() != 38
                    || buffer.isReadable()) {
                throw new AssertionError("Pokédex client sync packet lost progress or left unread bytes");
            }
        } finally {
            buffer.release();
        }

        context.succeed();
    }

    @GameTest
    public void scanningADisguiseDoesNotRegisterHiddenPokemonVariations(GameTestHelper context) {
        var manager = new PokedexManager(UUID.randomUUID(), new java.util.HashMap<>());
        manager.initialize();
        Pokemon visibleEevee = PokemonProperties.Companion.parse("eevee level=8 gender=female").create();
        Pokemon disguisedZoroark = PokemonProperties.Companion.parse("zoroark level=42 gender=male shiny=true").create();
        var scan = new PokedexEntityData(
                disguisedZoroark,
                new PokedexEntityData.DisguiseData(visibleEevee.getSpecies(), visibleEevee.getForm()));

        manager.encounter(scan);
        var visibleSpeciesId = visibleEevee.getSpecies().getResourceIdentifier();
        var visibleRecord = manager.getSpeciesRecord(visibleSpeciesId);
        var visibleFormRecord = visibleRecord == null ? null : visibleRecord.getFormRecord(visibleEevee.getForm().getName());
        if (visibleRecord == null || visibleFormRecord == null
                || manager.getKnowledgeForSpecies(visibleSpeciesId) != PokedexEntryProgress.SEEN) {
            throw new AssertionError("Scanning a disguise did not register the apparent species as seen");
        }
        if (!visibleFormRecord.getGenders().isEmpty()
                || !visibleFormRecord.getSeenShinyStates().isEmpty()
                || !visibleRecord.getAspects().isEmpty()) {
            throw new AssertionError("Scanning a disguise leaked the hidden Pokémon's gender, shiny state or aspects into the apparent entry");
        }

        context.succeed();
    }

    @GameTest
    public void pokemonEntityCanSpawnWithLoadedSpecies(GameTestHelper context) {
        PokemonEntity pokemon = context.spawn(CobblemonEntities.POKEMON, new BlockPos(1, 2, 1));
        if (pokemon.getPokemon().getSpecies() == null) {
            throw new AssertionError("Spawned Pokémon entity has no species");
        }

        context.succeed();
    }

    @GameTest
    public void pokemonCanMoveFromPartyToPC(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        var pokemon = new Pokemon();
        if (pokemon.getSpecies() == null) {
            throw new AssertionError("Test Pokémon did not initialize with a species");
        }

        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (!party.add(pokemon) || party.occupied() != 1) {
            throw new AssertionError("Pokémon could not be added to the party");
        }

        var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        var pcPosition = pc.getFirstAvailablePosition();
        if (pcPosition == null || !party.remove(pokemon)) {
            throw new AssertionError("Pokémon could not be removed from the party or the PC had no free slot");
        }
        pc.set(pcPosition, pokemon);

        boolean pokemonFoundInPC = false;
        for (Pokemon storedPokemon : pc) {
            if (storedPokemon.getUuid().equals(pokemon.getUuid())) {
                pokemonFoundInPC = true;
                break;
            }
        }
        if (party.occupied() != 0 || !pokemonFoundInPC) {
            throw new AssertionError("Party-to-PC move left the Pokémon duplicated or missing");
        }

        context.succeed();
    }

    @GameTest
    public void pokemonPartyDataRoundTripsThroughJSON(GameTestHelper context) {
        var originalPokemon = new Pokemon();
        var originalParty = new PartyStore(UUID.randomUUID());
        if (originalPokemon.getSpecies() == null || !originalParty.add(originalPokemon)) {
            throw new AssertionError("Could not prepare Pokémon party data for serialization");
        }

        var registryAccess = context.getLevel().registryAccess();
        JsonObject savedParty = originalParty.saveToJSON(new JsonObject(), registryAccess);
        var restoredParty = new PartyStore(UUID.randomUUID());
        restoredParty.loadFromJSON(savedParty, registryAccess);
        restoredParty.initialize();

        Pokemon restoredPokemon = restoredParty.get(0);
        if (restoredPokemon == null
                || !restoredPokemon.getUuid().equals(originalPokemon.getUuid())
                || !restoredPokemon.getSpecies().equals(originalPokemon.getSpecies())) {
            throw new AssertionError("Party JSON round trip changed or lost Pokémon identity/species");
        }

        context.succeed();
    }

    @GameTest
    public void pokemonStateRoundTripsThroughNBT(GameTestHelper context) {
        var pokemon = PokemonProperties.Companion.parse("eevee level=42").create();
        pokemon.setShiny(true);
        pokemon.setCurrentHealth(7);
        pokemon.setCaughtBall(PokeBalls.getGreatBall());
        UUID pokemonId = pokemon.getUuid();
        Identifier speciesId = pokemon.getSpecies().getResourceIdentifier();

        var registryAccess = context.getLevel().registryAccess();
        var savedPokemon = pokemon.saveToNBT(registryAccess, new CompoundTag());
        var restoredPokemon = new Pokemon().loadFromNBT(registryAccess, savedPokemon);

        if (!restoredPokemon.getUuid().equals(pokemonId)
                || !restoredPokemon.getSpecies().getResourceIdentifier().equals(speciesId)
                || restoredPokemon.getLevel() != 42
                || !restoredPokemon.getShiny()
                || restoredPokemon.getCurrentHealth() != 7
                || !restoredPokemon.getCaughtBall().equals(PokeBalls.getGreatBall())) {
            throw new AssertionError("Pokémon NBT round trip lost identity, species, level, shiny, health, or caught ball");
        }

        context.succeed();
    }

    @GameTest
    public void pokemonEntityReadsNestedTetheringData(GameTestHelper context) throws ReflectiveOperationException {
        var registryAccess = context.getLevel().registryAccess();
        var player = context.makeMockServerPlayerInLevel();
        var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        var pcPokemon = PokemonProperties.Companion.parse("eevee").create();
        var pcPosition = pc.getFirstAvailablePosition();
        if (pcPosition == null) {
            throw new AssertionError("Could not prepare a PC slot for tether restoration");
        }
        UUID tetheringId = UUID.randomUUID();
        pcPokemon.setTetheringId(tetheringId);
        pc.set(pcPosition, pcPokemon);

        var original = context.spawn(CobblemonEntities.POKEMON, new BlockPos(1, 2, 1));
        original.setTethering(new PokemonPastureBlockEntity.Tethering(
                new BlockPos(0, 0, 0),
                new BlockPos(4, 2, 4),
                player.getUUID(),
                player.getName().getString(),
                tetheringId,
                pcPokemon.getUuid(),
                pc.getUuid(),
                original.getId(),
                new BlockPos(2, 1, 2)));

        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess);
        var writeEntityData = PokemonEntity.class.getDeclaredMethod("addAdditionalSaveData",
                net.minecraft.world.level.storage.ValueOutput.class);
        writeEntityData.setAccessible(true);
        writeEntityData.invoke(original, saved);
        var serialized = saved.buildResult();
        if (!serialized.contains("Tethering")) {
            throw new AssertionError("Tethering data was not saved inside the entity persistence compound");
        }

        var restored = context.spawn(CobblemonEntities.POKEMON, new BlockPos(2, 2, 1));
        var readEntityData = PokemonEntity.class.getDeclaredMethod("readAdditionalSaveData",
                net.minecraft.world.level.storage.ValueInput.class);
        readEntityData.setAccessible(true);
        readEntityData.invoke(restored, TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, serialized));
        var restoredTethering = restored.getTethering();
        if (restoredTethering == null
                || !restoredTethering.getTetheringId().equals(original.getTethering().getTetheringId())
                || !restoredTethering.getPokemonId().equals(original.getTethering().getPokemonId())
                || !restoredTethering.getPlayerId().equals(original.getTethering().getPlayerId())
                || !restoredTethering.getPcId().equals(original.getTethering().getPcId())) {
            throw new AssertionError("Nested pasture tethering data did not survive entity save/load");
        }

        context.succeed();
    }

    @GameTest
    public void pokemonCopyFromPreservesPotentialMarks(GameTestHelper context) {
        Mark activeMark = new Mark(Identifier.parse("cobblemon:test_active"), "active", "", null, null,
                Identifier.parse("cobblemon:test/active"), null, null, 0F, null, null, 0);
        Mark potentialMark = new Mark(Identifier.parse("cobblemon:test_potential"), "potential", "", null, null,
                Identifier.parse("cobblemon:test/potential"), null, null, 0F, null, null, 0);
        Pokemon source = PokemonProperties.Companion.parse("eevee").create();
        Pokemon destination = PokemonProperties.Companion.parse("eevee").create();
        source.getMarks().add(activeMark);
        source.getPotentialMarks().add(potentialMark);

        destination.copyFrom(source);

        if (!destination.getMarks().equals(java.util.Set.of(activeMark))
                || !destination.getPotentialMarks().equals(java.util.Set.of(potentialMark))) {
            throw new AssertionError("Pokemon.copyFrom did not preserve active and potential marks independently");
        }
        context.succeed();
    }

    @GameTest
    public void partyEnforcesCapacityAndKeepsUuidIndexAfterMove(GameTestHelper context) {
        var party = new PartyStore(UUID.randomUUID());
        Pokemon[] team = new Pokemon[7];
        for (int i = 0; i < team.length; i++) {
            team[i] = new Pokemon();
        }
        for (int i = 0; i < 6; i++) {
            if (!party.add(team[i])) {
                throw new AssertionError("The party rejected a Pokémon before all six slots were filled");
            }
        }
        if (party.size() != 6 || party.occupied() != 6 || party.add(team[6])) {
            throw new AssertionError("The party did not enforce its six-Pokémon capacity");
        }

        party.swap(0, 5);
        if (party.get(0) != team[5]
                || party.get(5) != team[0]
                || party.get(team[5].getUuid()) != team[5]
                || party.get(team[0].getUuid()) != team[0]) {
            throw new AssertionError("Moving Pokémon in the party desynchronized slots and UUID lookup");
        }

        context.succeed();
    }

    @GameTest
    public void shrinkingPcPreservesOverflowAndUuidLookup(GameTestHelper context) {
        var pc = new PCStore(UUID.randomUUID());
        pc.resize(3, false, pokemon -> {
            pc.relocateEvictedBoxPokemon(pokemon);
            return kotlin.Unit.INSTANCE;
        });
        Pokemon[] collection = new Pokemon[61];
        for (int i = 0; i < collection.length; i++) {
            collection[i] = new Pokemon();
            if (!pc.add(collection[i])) {
                throw new AssertionError("The PC rejected a Pokémon while free box capacity remained");
            }
        }

        pc.resize(1, false, pokemon -> {
            pc.relocateEvictedBoxPokemon(pokemon);
            return kotlin.Unit.INSTANCE;
        });
        int inBoxes = 0;
        int inOverflow = 0;
        for (Pokemon pokemon : collection) {
            boolean inPC = pc.get(pokemon.getUuid()) == pokemon;
            boolean inBackup = pc.getBackupStore().get(pokemon.getUuid()) == pokemon;
            if (inPC == inBackup) {
                throw new AssertionError("A Pokémon was lost or duplicated while the PC shrank: " + pokemon.getUuid());
            }
            if (inPC) inBoxes++;
            if (inBackup) inOverflow++;
        }
        if (inBoxes != 30 || inOverflow != 31 || pc.getBoxes().size() != 1) {
            throw new AssertionError("PC resize produced unexpected box/overflow counts: "
                    + inBoxes + " in boxes, " + inOverflow + " in overflow");
        }

        context.succeed();
    }

    @GameTest
    public void wildBattleRejectsEmptyPartyWithoutChangingWildPokemon(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        PokemonEntity wildPokemon = context.spawn(CobblemonEntities.POKEMON, new BlockPos(2, 2, 2));

        var result = BattleBuilder.INSTANCE.pve(player, wildPokemon);
        if (!(result instanceof ErroredBattleStart)
                || wildPokemon.getBattleId() != null
                || BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            throw new AssertionError("A player with no party Pokémon entered a wild battle or mutated the target");
        }

        context.succeed();
    }

    @GameTest
    public void airborneSpawnPositionIsRegistered(GameTestHelper context) {
        var type = SpawnablePosition.Companion.getByName("airborne");
        if (type == null || type.getClazz() != AirborneSpawnablePosition.class) {
            throw new AssertionError("The airborne spawn position type was not registered");
        }
        context.succeed();
    }

    @GameTest
    public void flyingPokemonAcrossRideProfilesReceiveAirWanderBehaviour(GameTestHelper context) {
        // Bird, jet, hover, glider, and rocket profile species all share the
        // can_fly behavior query, independent of their riding controller.
        String[] species = {"braviary", "dragonite", "metagross", "butterfree", "golurk"};
        for (int i = 0; i < species.length; i++) {
            PokemonEntity pokemon = PokemonProperties.Companion.parse(species[i] + " level=30")
                    .createEntity(context.getLevel());
            BlockPos pos = context.absolutePos(new BlockPos(1 + i * 2, 2, 1));
            pokemon.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            context.getLevel().addFreshEntity(pokemon);
            if (!pokemon.getBehaviours().contains(Identifier.parse("cobblemon:wanders_air"))) {
                throw new AssertionError(species[i] + " did not receive the shared flying wander behavior");
            }
            if (!species[i].equals("butterfree")
                    && !pokemon.getBehaviours().contains(Identifier.parse("cobblemon:wanders"))) {
                throw new AssertionError(species[i] + " can walk but did not receive the occasional landing behavior");
            }
            pokemon.discard();
        }
        context.succeed();
    }

    @GameTest
    public void throwingPartyPokemonAtWildTargetStartsBattleWithoutConsumingBall(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        BlockPos playerPosition = context.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(playerPosition.getX() + 0.5, playerPosition.getY(), playerPosition.getZ() + 0.5);
        player.getInventory().add(new ItemStack(CobblemonItems.POKE_BALL));

        Pokemon leader = PokemonProperties.Companion.parse("pikachu level=10").create(player);
        if (!Cobblemon.INSTANCE.getStorage().getParty(player).add(leader)) {
            throw new AssertionError("Could not prepare a living Pokémon for the battle throw");
        }
        PokemonEntity wildPokemon = context.spawn(CobblemonEntities.POKEMON, new BlockPos(1, 2, 6));
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                wildPokemon.position().add(0.0, wildPokemon.getBbHeight() * 0.5, 0.0));

        ThrowPokemonForBattleHandler.INSTANCE.handle(
                new ThrowPokemonForBattlePacket(0), context.getLevel().getServer(), player);
        var projectiles = context.getLevel().getEntitiesOfClass(EmptyPokeBallEntity.class,
                player.getBoundingBox().inflate(16.0), EmptyPokeBallEntity::isBattleChallengeProjectile);
        if (projectiles.size() != 1) {
            throw new AssertionError("A valid aimed wild target did not create exactly one battle Poké Ball projectile");
        }

        EmptyPokeBallEntity projectile = projectiles.get(0);
        if (!leader.getUuid().equals(projectile.getBattleChallengeLeadPokemon())
                || !projectile.getPokeBall().equals(leader.getCaughtBall())) {
            throw new AssertionError("The battle projectile did not preserve the selected Pokémon and its cosmetic ball");
        }

        for (int tick = 0; tick < 12 && BattleRegistry.getBattleByParticipatingPlayer(player) == null; tick++) {
            projectile.tick();
        }
        var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        if (battle == null || wildPokemon.getBattleId() == null) {
            throw new AssertionError("Hitting the wild Pokémon with the battle projectile did not start a battle");
        }
        try {
            if (!battle.getActor(player).getPokemonList().get(0).getUuid().equals(leader.getUuid())) {
                throw new AssertionError("The selected Pokémon was not chosen as the battle lead");
            }
            if (player.getInventory().countItem(CobblemonItems.POKE_BALL) != 1) {
                throw new AssertionError("Starting a battle consumed a Poké Ball from the player's inventory");
            }
        } finally {
            BattleRegistry.closeBattle(battle);
        }
        context.succeed();
    }

    @GameTest
    public void throwingPartyPokemonAtHighMovingWildTargetStartsBattle(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        BlockPos playerPosition = context.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(playerPosition.getX() + 0.5, playerPosition.getY(), playerPosition.getZ() + 0.5);

        Pokemon leader = PokemonProperties.Companion.parse("pikachu level=10").create(player);
        if (!Cobblemon.INSTANCE.getStorage().getParty(player).add(leader)) {
            throw new AssertionError("Could not prepare a living Pokémon for the high-target battle throw");
        }

        BlockPos targetPosition = context.absolutePos(new BlockPos(1, 6, 6));
        PokemonEntity wildPokemon = PokemonProperties.Companion.parse("dragonite level=30")
                .createEntity(context.getLevel());
        wildPokemon.setPos(targetPosition.getX() + 0.5, targetPosition.getY(), targetPosition.getZ() + 0.5);
        if (!context.getLevel().addFreshEntity(wildPokemon)) {
            throw new AssertionError("Could not spawn the elevated moving battle target");
        }
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                wildPokemon.position().add(0.0, wildPokemon.getBbHeight() * 0.5, 0.0));

        ThrowPokemonForBattleHandler.INSTANCE.handle(
                new ThrowPokemonForBattlePacket(0), context.getLevel().getServer(), player);
        var projectiles = context.getLevel().getEntitiesOfClass(EmptyPokeBallEntity.class,
                player.getBoundingBox().inflate(24.0), EmptyPokeBallEntity::isBattleChallengeProjectile);
        if (projectiles.size() != 1) {
            throw new AssertionError("Aimed throw at an elevated wild Pokémon did not create one battle projectile");
        }

        EmptyPokeBallEntity projectile = projectiles.get(0);
        // Move the target across the incoming line during flight to cover ordinary
        // entity movement instead of only the stationary-target case.
        for (int tick = 0; tick < 16 && BattleRegistry.getBattleByParticipatingPlayer(player) == null; tick++) {
            wildPokemon.setPos(wildPokemon.getX() + 0.04, wildPokemon.getY(), wildPokemon.getZ());
            projectile.tick();
        }

        var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        if (battle == null || wildPokemon.getBattleId() == null) {
            throw new AssertionError("The projectile failed to start battle with the elevated moving Pokémon");
        }
        try {
            if (!battle.getActor(player).getPokemonList().get(0).getUuid().equals(leader.getUuid())) {
                throw new AssertionError("The high-target battle selected the wrong lead Pokémon");
            }
        } finally {
            BattleRegistry.closeBattle(battle);
        }
        context.succeed();
    }

    @GameTest
    public void battleThrowRejectsInvalidPartySlot(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        ThrowPokemonForBattleHandler.INSTANCE.handle(
                new ThrowPokemonForBattlePacket(6), context.getLevel().getServer(), player);
        var projectiles = context.getLevel().getEntitiesOfClass(EmptyPokeBallEntity.class,
                player.getBoundingBox().inflate(16.0), EmptyPokeBallEntity::isBattleChallengeProjectile);
        if (!projectiles.isEmpty()) {
            throw new AssertionError("An invalid party slot created a battle projectile");
        }
        context.succeed();
    }

    @GameTest
    public void pcPlacementBuildsBothHalvesAndOneBlockEntity(GameTestHelper context) {
        var level = context.getLevel();
        BlockPos position = new BlockPos(1, 2, 1);
        context.setBlock(position.below(), Blocks.STONE);
        context.setBlock(position, CobblemonBlocks.PC.defaultBlockState());
        BlockPos worldPosition = context.absolutePos(position);
        var pcBlock = (PCBlock) CobblemonBlocks.PC;
        var bottomState = level.getBlockState(worldPosition);
        pcBlock.setPlacedBy(level, worldPosition, bottomState, null, new ItemStack(CobblemonItems.PC));

        var placedBottom = level.getBlockState(worldPosition);
        var placedTop = level.getBlockState(worldPosition.above());
        if (placedBottom.getValue(PCBlock.Companion.getPART()) != PCBlock.PCPart.BOTTOM
                || placedTop.getBlock() != CobblemonBlocks.PC
                || placedTop.getValue(PCBlock.Companion.getPART()) != PCBlock.PCPart.TOP
                || level.getBlockEntity(worldPosition) == null
                || level.getBlockEntity(worldPosition.above()) != null) {
            throw new AssertionError("PC placement did not create a valid two-block structure and bottom block entity");
        }

        context.succeed();
    }

    @GameTest
    public void playerPartyAndPCStoresPersistThroughConfiguredNBTBackend(GameTestHelper context) {
        if (!"nbt".equals(Cobblemon.config.getStorageFormat())) {
            throw new AssertionError("The configured GameTest storage backend is not NBT: " + Cobblemon.config.getStorageFormat());
        }

        var player = context.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        var partyPokemon = new Pokemon();
        partyPokemon.setCurrentHealth(1);
        var pcPokemon = new Pokemon();

        var storage = Cobblemon.INSTANCE.getStorage();
        var party = storage.getParty(player);
        var pc = storage.getPC(player);
        var pcPosition = pc.getFirstAvailablePosition();
        if (!party.add(partyPokemon) || pcPosition == null) {
            throw new AssertionError("Could not prepare party and PC data for the NBT backend round trip");
        }
        pc.set(pcPosition, pcPokemon);

        Path storeRoot = context.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("pokemon");
        Path partyFile = playerStorePath(storeRoot, "playerpartystore", playerId);
        Path pcFile = playerStorePath(storeRoot, "pcstore", playerId);

        // Exercise the actual configured file-store factory: disconnect saves dirty stores and evicts their cache.
        storage.onPlayerDisconnect(player);
        context.runAfterDelay(1, () -> assertPersistedPlayerStores(
                context,
                storeRoot,
                partyFile,
                pcFile,
                playerId,
                partyPokemon,
                pcPokemon,
                0));
    }

    private static Path playerStorePath(Path root, String storeName, UUID playerId) {
        String uuid = playerId.toString();
        return root.resolve(storeName).resolve(uuid.substring(0, 2)).resolve(uuid + ".dat");
    }

    private static void assertPersistedPlayerStores(
            GameTestHelper context,
            Path storeRoot,
            Path partyFile,
            Path pcFile,
            UUID playerId,
            Pokemon expectedPartyPokemon,
            Pokemon expectedPCPokemon,
            int attempt) {
        Path partyTemp = partyFile.resolveSibling(partyFile.getFileName() + ".temp");
        Path pcTemp = pcFile.resolveSibling(pcFile.getFileName() + ".temp");
        if (attempt >= 200) {
            throw new AssertionError("The configured NBT backend did not finish writing the party and PC stores");
        }
        if (!Files.isRegularFile(partyFile) || !Files.isRegularFile(pcFile)
                || Files.exists(partyTemp) || Files.exists(pcTemp)) {
            context.runAfterDelay(1, () -> assertPersistedPlayerStores(
                    context, storeRoot, partyFile, pcFile, playerId,
                    expectedPartyPokemon, expectedPCPokemon, attempt + 1));
            return;
        }

        var registryAccess = context.getLevel().registryAccess();
        var storage = Cobblemon.INSTANCE.getStorage();
        var loadedParty = storage.getParty(playerId, registryAccess);
        var loadedPC = storage.getPC(playerId, registryAccess);
        Pokemon reloadedPartyPokemon = loadedParty.get(0);
        boolean pcPokemonWasReloaded = false;
        for (Pokemon storedPokemon : loadedPC) {
            if (storedPokemon.getUuid().equals(expectedPCPokemon.getUuid())
                    && storedPokemon.getSpecies().equals(expectedPCPokemon.getSpecies())) {
                pcPokemonWasReloaded = true;
                break;
            }
        }

        if (reloadedPartyPokemon == null
                || !reloadedPartyPokemon.getUuid().equals(expectedPartyPokemon.getUuid())
                || !reloadedPartyPokemon.getSpecies().equals(expectedPartyPokemon.getSpecies())
                || reloadedPartyPokemon.getCurrentHealth() != 1
                || loadedParty.occupied() != 1
                || !pcPokemonWasReloaded) {
            throw new AssertionError("The configured NBT backend lost party/PC Pokémon identity or health after cache eviction");
        }
        context.succeed();
    }

    @GameTest
    public void partyHealingRestoresPokemonHealth(GameTestHelper context) {
        var pokemon = new Pokemon();
        var party = new PartyStore(UUID.randomUUID());
        pokemon.setCurrentHealth(1);
        if (!party.add(pokemon)) {
            throw new AssertionError("Could not add test Pokémon to the party");
        }

        party.heal();
        if (pokemon.getCurrentHealth() != pokemon.getMaxHealth()) {
            throw new AssertionError("Party healing did not restore the Pokémon to full health");
        }

        context.succeed();
    }

    @GameTest
    public void evolutionChangesSpeciesAndPreservesPokemonIdentity(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        Pokemon pokemon = PokemonProperties.Companion.parse("eevee level=20").create(player);
        UUID pokemonId = pokemon.getUuid();
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (!party.add(pokemon)) {
            throw new AssertionError("Could not place the test Eevee in the player's party");
        }

        Evolution evolution = new LevelUpEvolution(
                "gametest_evolution",
                PokemonProperties.Companion.parse("species=vaporeon"),
                null,
                false,
                false,
                new HashSet<>(),
                new HashSet<MoveTemplate>(),
                new DropTable(),
                false);
        if (!evolution.evolve(pokemon)) {
            throw new AssertionError("The non-optional test evolution did not start");
        }
        if (!pokemon.getSpecies().getResourceIdentifier().equals(Identifier.parse("cobblemon:vaporeon"))) {
            throw new AssertionError("Evolution did not change Eevee into Vaporeon: " + pokemon.getSpecies());
        }
        if (!pokemon.getUuid().equals(pokemonId) || party.get(0) != pokemon) {
            throw new AssertionError("Evolution replaced the Pokémon identity or detached it from its party");
        }

        context.succeed();
    }

    @GameTest
    public void playerCanStartAndCloseWildBattle(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        Pokemon partyPokemon = PokemonProperties.Companion.parse("pikachu level=10").create(player);
        if (!Cobblemon.INSTANCE.getStorage().getParty(player).add(partyPokemon)) {
            throw new AssertionError("Could not prepare a living Pokémon for the PvE battle");
        }
        PokemonEntity wildPokemon = context.spawn(CobblemonEntities.POKEMON, new BlockPos(2, 2, 2));

        var result = BattleBuilder.INSTANCE.pve(player, wildPokemon);
        if (!(result instanceof SuccessfulBattleStart)) {
            throw new AssertionError("BattleBuilder rejected a valid player-versus-wild battle");
        }

        var battle = ((SuccessfulBattleStart) result).getBattle();
        try {
            if (BattleRegistry.getBattle(battle.getBattleId()) != battle || wildPokemon.getBattleId() == null) {
                throw new AssertionError("The PvE battle was not registered on both the registry and wild Pokémon");
            }
        } finally {
            BattleRegistry.closeBattle(battle);
        }
        if (BattleRegistry.getBattle(battle.getBattleId()) != null) {
            throw new AssertionError("Closing the PvE battle left it in the global battle registry");
        }

        context.succeed();
    }

    @GameTest(maxTicks = 400)
    public void masterBallCaptureAddsWildPokemonToParty(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        PokemonEntity wildPokemon = context.spawn(CobblemonEntities.POKEMON, new BlockPos(2, 2, 2));
        UUID pokemonId = wildPokemon.getPokemon().getUuid();
        EmptyPokeBallEntity ball = new EmptyPokeBallEntity(
                PokeBalls.getMasterBall(),
                context.getLevel(),
                player,
                CobblemonEntities.EMPTY_POKEBALL);
        ball.setPos(wildPokemon.position());
        context.getLevel().addFreshEntity(ball);
        ball.setCapturingPokemon(wildPokemon);
        ball.beginCapture();

        context.runAfterDelay(300, () -> {
            if (!ball.getCaptureFuture().getNow(false)) {
                throw new AssertionError("The guaranteed Master Ball capture did not complete successfully");
            }
            boolean capturedPokemonInParty = false;
            for (Pokemon storedPokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (storedPokemon.getUuid().equals(pokemonId)) {
                    capturedPokemonInParty = true;
                    break;
                }
            }
            if (!capturedPokemonInParty || !wildPokemon.isRemoved()
                    || Cobblemon.INSTANCE.getStorage().getParty(player).occupied() != 1) {
                throw new AssertionError("Capture did not move the wild Pokémon into the player's party exactly once");
            }
            var pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
            var captured = Cobblemon.INSTANCE.getStorage().getParty(player).get(0);
            if (pokedex.getKnowledgeForSpecies(captured.getSpecies().getResourceIdentifier()) != PokedexEntryProgress.OWNED
                    || pokedex.getSpeciesRecord(captured.getSpecies().getResourceIdentifier()).getHighestLevel() != captured.getLevel()) {
                throw new AssertionError("A successful capture added the Pokémon but did not register it as owned in the Pokédex");
            }
            context.succeed();
        });
    }

    @GameTest
    public void gemBlockDropsGemWithoutSilkTouch(GameTestHelper context) {
        BlockPos position = new BlockPos(1, 2, 1);
        context.setBlock(position, BuiltInRegistries.BLOCK.getValue(Identifier.parse("cobblemon:bug_gem_block")));
        context.getLevel().destroyBlock(
                context.absolutePos(position),
                true,
                context.makeMockServerPlayerInLevel());
        context.assertItemEntityPresent(BuiltInRegistries.ITEM.getValue(Identifier.parse("cobblemon:bug_gem")));

        context.succeed();
    }

    private static void assertRegisteredItem(String id) {
        var identifier = Identifier.parse(id);
        var item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == Items.AIR) {
            throw new AssertionError("Missing Cobblemon item registration: " + identifier);
        }
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}

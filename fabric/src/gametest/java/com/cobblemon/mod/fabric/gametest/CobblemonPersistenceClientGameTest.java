package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Reopens a saved disposable world to verify real party and PC store persistence and sync. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonPersistenceClientGameTest {
    private CobblemonPersistenceClientGameTest() {}

    public static void verify(ClientGameTestContext context) {
        if (!"nbt".equals(Cobblemon.config.getStorageFormat())) {
            throw new AssertionError("Persistence world test requires the configured NBT storage backend, got "
                    + Cobblemon.config.getStorageFormat());
        }

        TestWorldSave savedWorld;
        UUID playerId;
        UUID pcStoreId;
        Snapshot expectedParty;
        Snapshot expectedPC;
        PlayerPartyStore firstPartyStore;
        PCStore firstPCStore;
        ClientParty firstClientParty;
        ClientPC firstClientPC;
        Path partyFile;
        Path pcFile;

        try (TestSingleplayerContext firstWorld = context.worldBuilder().create()) {
            firstWorld.getConnection().waitForChunksRender();
            Object[] fixture = firstWorld.getServer().computeOnServer(server -> {
                var player = firstWorld.getConnection().getServerPlayer();
                var storage = Cobblemon.INSTANCE.getStorage();
                var party = storage.getParty(player);
                var pc = storage.getPC(player);

                Pokemon partyPokemon = PokemonProperties.Companion.parse(
                        "basculin striped=white level=31").create(player);
                partyPokemon.setCurrentHealth(Math.max(1, partyPokemon.getMaxHealth() - 7));
                partyPokemon.getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("tackle").create());
                partyPokemon.getMoveSet().setMove(1, Moves.INSTANCE.getByNameOrDummy("watergun").create());
                if (!party.add(partyPokemon)) throw new AssertionError("Could not add the owned Pokémon to the party");

                Pokemon pcPokemon = PokemonProperties.Companion.parse("eevee level=24").create(player);
                pcPokemon.setCurrentHealth(Math.max(1, pcPokemon.getMaxHealth() - 5));
                pcPokemon.getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("quickattack").create());
                pcPokemon.getMoveSet().setMove(1, Moves.INSTANCE.getByNameOrDummy("growl").create());
                var pcPosition = pc.getFirstAvailablePosition();
                if (pcPosition == null) throw new AssertionError("The player's PC has no available Pokémon slot");
                pc.set(pcPosition, pcPokemon);
                // Explicitly observe the PC so the test client receives a full snapshot after fixture population.
                pc.addObserver(player);

                var partySnapshot = Snapshot.from(partyPokemon);
                var pcSnapshot = Snapshot.from(pcPokemon);
                if (!"whitestriped".equals(partySnapshot.formId)) {
                    throw new AssertionError("Persistence fixture did not resolve Basculin's White-Striped form: "
                            + partySnapshot.speciesId + "/" + partySnapshot.formId);
                }
                return new Object[] { player.getUUID(), party, pc, pc.getUuid(), partySnapshot, pcSnapshot };
            });

            playerId = (UUID) fixture[0];
            firstPartyStore = (PlayerPartyStore) fixture[1];
            firstPCStore = (PCStore) fixture[2];
            pcStoreId = (UUID) fixture[3];
            expectedParty = (Snapshot) fixture[4];
            expectedPC = (Snapshot) fixture[5];
            savedWorld = firstWorld.getWorldSave();
            Path storeRoot = savedWorld.getSaveDirectory().resolve("pokemon");
            partyFile = playerStorePath(storeRoot, "playerpartystore", playerId);
            pcFile = playerStorePath(storeRoot, "pcstore", pcStoreId);

            context.waitFor(client -> clientPartyMatches(expectedParty)
                    && clientPCMatches(playerId, expectedPC), 200);
            firstClientParty = context.computeOnClient(client -> CobblemonClient.INSTANCE.getStorage().getParty());
            firstClientPC = context.computeOnClient(client -> CobblemonClient.INSTANCE.getStorage()
                    .getPcStores().get(pcStoreId));
            if (firstClientPC == null) throw new AssertionError("The initial PC snapshot was not synchronized to the client");
        }

        waitForStoreFiles(context, partyFile, pcFile);

        try (TestSingleplayerContext reopenedWorld = savedWorld.open()) {
            reopenedWorld.getConnection().waitForChunksRender();
            var serverSnapshots = reopenedWorld.getServer().computeOnServer(server -> {
                var player = reopenedWorld.getConnection().getServerPlayer();
                if (!player.getUUID().equals(playerId)) {
                    throw new AssertionError("Reopened world changed the singleplayer owner UUID");
                }
                var storage = Cobblemon.INSTANCE.getStorage();
                var party = storage.getParty(player);
                var pc = storage.getPC(player);
                if (!pc.getUuid().equals(pcStoreId)) {
                    throw new AssertionError("Reopened world changed the player's PC store UUID");
                }
                // Explicitly request the current PC snapshot for the reopened client storage instance.
                pc.addObserver(player);
                if (party == firstPartyStore || pc == firstPCStore) {
                    throw new AssertionError("Reopened world reused cached party/PC stores instead of loading persisted stores");
                }

                Pokemon partyPokemon = party.get(0);
                Pokemon pcPokemon = findPokemon(pc, expectedPC.uuid);
                assertPokemon(partyPokemon, expectedParty, "party");
                assertPokemon(pcPokemon, expectedPC, "PC");
                if (party.occupied() != 1) throw new AssertionError("Reopened party contains unexpected duplicate/lost Pokémon");
                return new Snapshot[] { Snapshot.from(partyPokemon), Snapshot.from(pcPokemon) };
            });
            assertSame(serverSnapshots[0], expectedParty, "reloaded party");
            assertSame(serverSnapshots[1], expectedPC, "reloaded PC");

            context.waitFor(client -> clientPartyMatches(expectedParty)
                    && clientPCMatches(playerId, expectedPC), 200);
            boolean clientSynced = context.computeOnClient(client -> {
                ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();
                ClientPC pc = CobblemonClient.INSTANCE.getStorage().getPcStores().get(pcStoreId);
                return party != firstClientParty
                        && pc != firstClientPC
                        && party.get(0) != null
                        && party.get(0).getUuid().equals(expectedParty.uuid)
                        && pc != null
                        && pc.findByUUID(expectedPC.uuid) != null;
            });
            if (!clientSynced) throw new AssertionError("Reopened world did not replace and synchronize client party/PC stores");
            context.computeOnClient(client -> {
                assertPokemon(CobblemonClient.INSTANCE.getStorage().getParty().get(0), expectedParty, "client party");
                assertPokemon(CobblemonClient.INSTANCE.getStorage().getPcStores().get(pcStoreId)
                        .findByUUID(expectedPC.uuid), expectedPC, "client PC");
                return null;
            });
        }

        System.out.println("[Cobblemon GameTest] Saved world close/reopen preserved owned party and PC Pokémon data and client sync");
    }

    private static void waitForStoreFiles(ClientGameTestContext context, Path partyFile, Path pcFile) {
        Path partyTemp = partyFile.resolveSibling(partyFile.getFileName() + ".temp");
        Path pcTemp = pcFile.resolveSibling(pcFile.getFileName() + ".temp");
        for (int tick = 0; tick < 200; tick++) {
            if (Files.isRegularFile(partyFile) && Files.isRegularFile(pcFile)
                    && !Files.exists(partyTemp) && !Files.exists(pcTemp)) return;
            context.waitTick();
        }
        throw new AssertionError("Closing the first world did not flush both party and PC NBT files: "
                + partyFile + ", " + pcFile);
    }

    private static Path playerStorePath(Path root, String storeName, UUID playerId) {
        String uuid = playerId.toString();
        return root.resolve(storeName).resolve(uuid.substring(0, 2)).resolve(uuid + ".dat");
    }

    private static boolean clientPartyMatches(Snapshot expected) {
        var pokemon = CobblemonClient.INSTANCE.getStorage().getParty().get(0);
        return pokemon != null && matches(pokemon, expected);
    }

    private static boolean clientPCMatches(UUID storeId, Snapshot expected) {
        ClientPC pc = CobblemonClient.INSTANCE.getStorage().getPcStores().get(storeId);
        var pokemon = pc == null ? null : pc.findByUUID(expected.uuid);
        return pokemon != null && matches(pokemon, expected);
    }

    private static Pokemon findPokemon(PCStore pc, UUID uuid) {
        for (Pokemon pokemon : pc) {
            if (pokemon.getUuid().equals(uuid)) return pokemon;
        }
        return null;
    }

    private static void assertPokemon(Pokemon pokemon, Snapshot expected, String storeName) {
        if (pokemon == null || !matches(pokemon, expected)) {
            throw new AssertionError(storeName + " Pokémon did not preserve UUID/species/form/moves/experience/HP: "
                    + (pokemon == null ? "missing" : Snapshot.from(pokemon)));
        }
    }

    private static boolean matches(Pokemon pokemon, Snapshot expected) {
        return pokemon.getUuid().equals(expected.uuid)
                && pokemon.getSpecies().getResourceIdentifier().equals(expected.speciesId)
                && pokemon.getForm().formOnlyShowdownId().equals(expected.formId)
                && pokemon.getLevel() == expected.level
                && pokemon.getExperience() == expected.experience
                && pokemon.getCurrentHealth() == expected.currentHealth
                && pokemon.getMoveSet().getMoves().stream()
                        .map(move -> move.getTemplate().getName()).toList().equals(expected.moveIds);
    }

    private static void assertSame(Snapshot actual, Snapshot expected, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + " Pokémon changed during world reload: " + actual);
    }

    private static final class Snapshot {
        private final UUID uuid;
        private final Identifier speciesId;
        private final String formId;
        private final int level;
        private final int experience;
        private final int currentHealth;
        private final List<String> moveIds;

        private Snapshot(UUID uuid, Identifier speciesId, String formId, int level, int experience,
                         int currentHealth, List<String> moveIds) {
            this.uuid = uuid;
            this.speciesId = speciesId;
            this.formId = formId;
            this.level = level;
            this.experience = experience;
            this.currentHealth = currentHealth;
            this.moveIds = List.copyOf(moveIds);
        }

        private static Snapshot from(Pokemon pokemon) {
            return new Snapshot(
                    pokemon.getUuid(),
                    pokemon.getSpecies().getResourceIdentifier(),
                    pokemon.getForm().formOnlyShowdownId(),
                    pokemon.getLevel(),
                    pokemon.getExperience(),
                    pokemon.getCurrentHealth(),
                    pokemon.getMoveSet().getMoves().stream().map(move -> move.getTemplate().getName()).toList());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Snapshot snapshot)) return false;
            return uuid.equals(snapshot.uuid) && speciesId.equals(snapshot.speciesId)
                    && formId.equals(snapshot.formId) && level == snapshot.level
                    && experience == snapshot.experience && currentHealth == snapshot.currentHealth
                    && moveIds.equals(snapshot.moveIds);
        }

        @Override
        public int hashCode() {
            return uuid.hashCode();
        }

        @Override
        public String toString() {
            return speciesId + "/" + formId + " " + uuid + " level=" + level
                    + " exp=" + experience + " hp=" + currentHealth + " moves=" + moveIds;
        }
    }
}

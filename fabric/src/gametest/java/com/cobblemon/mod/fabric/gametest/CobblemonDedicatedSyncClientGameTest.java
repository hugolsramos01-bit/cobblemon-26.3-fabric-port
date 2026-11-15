package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.recipe.ClientCobblemonRecipeSnapshot;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI;
import com.cobblemon.mod.common.api.interaction.PokemonInteractions;
import com.cobblemon.mod.common.api.interaction.PokemonInteractionSet;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.requirements.OwnerHoldsItemRequirement;
import com.cobblemon.mod.common.client.net.data.DataRegistrySyncPacketHandler;
import com.cobblemon.mod.common.net.messages.client.data.PokemonInteractionsSyncPacket;
import com.cobblemon.mod.common.util.adapters.RegistryAccessResolver;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

/** Exercises login data sync against a separately launched dedicated server and reconnect. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonDedicatedSyncClientGameTest {
    private CobblemonDedicatedSyncClientGameTest() {}

    public static void verify(ClientGameTestContext context) {
        if (!"nbt".equals(Cobblemon.config.getStorageFormat())) {
            throw new AssertionError("Dedicated reconnect test requires NBT storage, got "
                    + Cobblemon.config.getStorageFormat());
        }

        long initialRecipeGeneration = context.computeOnClient(client ->
                ClientCobblemonRecipeSnapshot.INSTANCE.snapshot().getGeneration());
        Properties properties = dedicatedServerProperties();
        MinecraftServer bridgedServer = null;
        try {
            try (TestDedicatedServerContext serverContext = context.worldBuilder().createServer(properties)) {
            bridgedServer = serverContext.computeOnServer(server -> server);
            DedicatedServerTestBridge.activate(bridgedServer);
            serverContext.computeOnServer(server -> {
                if (Cobblemon.INSTANCE.getImplementation().server() != server) {
                    throw new AssertionError("Dedicated test server bridge did not resolve the active server instance");
                }
                return null;
            });
            UUID playerId;
            Snapshot expected;
            try (TestDedicatedServerConnection connection = serverContext.connect()) {
                connection.waitForChunksRender();
                playerId = serverContext.computeOnServer(server -> connection.getServerPlayer().getUUID());
                expected = serverContext.computeOnServer(server -> {
                    var player = connection.getServerPlayer();
                    var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                    Pokemon pokemon = PokemonProperties.Companion.parse(
                            "basculin striped=white level=31").create(player);
                    pokemon.setCurrentHealth(Math.max(1, pokemon.getMaxHealth() - 7));
                    pokemon.getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("tackle").create());
                    pokemon.getMoveSet().setMove(1, Moves.INSTANCE.getByNameOrDummy("watergun").create());
                    if (!party.add(pokemon)) throw new AssertionError("Could not add Pokémon to dedicated server party");
                    if (party.occupied() != 1 || party.get(0) != pokemon) {
                        throw new AssertionError("Dedicated server party fixture was not populated before sync");
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CobblemonItems.POKEDEX_RED));
                    Snapshot snapshot = Snapshot.from(pokemon);
                    if (!"whitestriped".equals(snapshot.formId)) {
                        throw new AssertionError("Dedicated fixture did not produce White-Striped Basculin: " + snapshot.formId);
                    }
                    return snapshot;
                });

                waitForClientState(context, "party Pokémon sync", expected,
                        client -> clientPartyMatches(expected));
                waitForClientState(context, "brush interaction sync", expected,
                        client -> hasDecodedBrushInteraction());
                waitForClientState(context, "Pokédex held-item sync", expected,
                        client -> client.player != null
                                && client.player.getMainHandItem().is(CobblemonItems.POKEDEX_RED));
                context.computeOnClient(client -> {
                    roundTripInteractionsWithoutFallback(client);
                    return null;
                });
                context.waitFor(client -> {
                    var snapshot = ClientCobblemonRecipeSnapshot.INSTANCE.snapshot();
                    return snapshot.getGeneration() > initialRecipeGeneration
                            && (!snapshot.getCookingPotRecipes().isEmpty()
                            || !snapshot.getBrewingStandRecipes().isEmpty());
                }, 200);
                assertPublicPokedexUse(context);
            }

            Path partyFile = serverContext.computeOnServer(server -> playerStorePath(
                    server.getWorldPath(LevelResource.ROOT), playerId));
            waitForPartyFile(context, partyFile);
            long afterLogoutGeneration = context.computeOnClient(client ->
                    ClientCobblemonRecipeSnapshot.INSTANCE.snapshot().getGeneration());
            try (TestDedicatedServerConnection reconnect = serverContext.connect()) {
                reconnect.waitForChunksRender();
                UUID reconnectedPlayerId = serverContext.computeOnServer(
                        server -> reconnect.getServerPlayer().getUUID());
                if (!playerId.equals(reconnectedPlayerId)) {
                    throw new AssertionError("Dedicated reconnect created a different player UUID");
                }
                context.waitFor(client -> clientPartyMatches(expected), 200);
                boolean serverReloaded = serverContext.computeOnServer(server -> {
                    var party = Cobblemon.INSTANCE.getStorage().getParty(reconnect.getServerPlayer());
                    return party.occupied() == 1 && matches(party.get(0), expected);
                });
                if (!serverReloaded) throw new AssertionError("Dedicated reconnect did not restore the party Pokémon");
                context.waitFor(client -> {
                    var snapshot = ClientCobblemonRecipeSnapshot.INSTANCE.snapshot();
                    var party = CobblemonClient.INSTANCE.getStorage().getParty();
                    return snapshot.getGeneration() > afterLogoutGeneration
                            && clientPartyMatches(expected)
                            && hasDecodedBrushInteraction()
                            && recipeSnapshotHasEntries()
                            && occupiedClientSlots(party) == 1;
                }, 200);
            }
            }
        } finally {
            // Keep the bridge available while TestDedicatedServerContext shuts its server down.
            DedicatedServerTestBridge.clear(bridgedServer);
        }
        System.out.println("[Cobblemon GameTest] Dedicated server synced recipes and party data across reconnect");
    }

    private static Properties dedicatedServerProperties() {
        Properties properties = new Properties();
        properties.setProperty("online-mode", "false");
        properties.setProperty("max-players", "1");
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("server-port", Integer.toString(ephemeralLoopbackPort()));
        properties.setProperty("level-name", "fabric-client-gametest-dedicated-sync-" + UUID.randomUUID());
        properties.setProperty("motd", "Disposable Cobblemon client game test");
        return properties;
    }

    private static int ephemeralLoopbackPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (Exception exception) {
            throw new AssertionError("Could not reserve an ephemeral loopback server port", exception);
        }
    }

    private static void assertPublicPokedexUse(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            client.gui.setScreen(null);
            client.options.keyUse.setDown(true);
            if (!client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND).consumesAction()) {
                throw new AssertionError("The synchronized Pokédex item rejected client use");
            }
            client.options.keyUse.setDown(false);
            if (client.player.isUsingItem()) client.gameMode.releaseUsingItem(client.player);
            return null;
        });
        context.waitFor(client -> client.gui.screen() instanceof PokedexGUI, 100);
        context.computeOnClient(client -> {
            if (!(client.gui.screen() instanceof PokedexGUI)) {
                throw new AssertionError("Using the synchronized Pokédex did not open its GUI");
            }
            client.gui.setScreen(null);
            return null;
        });
    }

    private static boolean clientPartyMatches(Snapshot expected) {
        ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();
        Pokemon pokemon = party.get(0);
        return occupiedClientSlots(party) == 1 && pokemon != null && matches(pokemon, expected);
    }

    private static void waitForClientState(ClientGameTestContext context, String state, Snapshot expected,
                                           Predicate<net.minecraft.client.Minecraft> predicate) {
        try {
            context.waitFor(client -> predicate.test(client), 200);
        } catch (AssertionError timeout) {
            String diagnostics = context.computeOnClient(client -> describeClientState(client, expected));
            throw new AssertionError("Timed out waiting for " + state + ": " + diagnostics, timeout);
        }
    }

    private static String describeClientState(net.minecraft.client.Minecraft client, Snapshot expected) {
        String partyState;
        try {
            ClientParty party = CobblemonClient.INSTANCE.getStorage().getParty();
            partyState = "occupied=" + occupiedClientSlots(party) + ", slots=" + party.getSlots().stream()
                    .map(pokemon -> pokemon == null ? "empty" : Snapshot.from(pokemon).toString()).toList();
        } catch (Throwable error) {
            partyState = "unavailable: " + error;
        }
        String heldItem = client.player == null ? "no client player" : client.player.getMainHandItem().toString();
        return "expected party=" + expected + "; client party=" + partyState
                + "; brush=" + brushInteractionState()
                + "; held item=" + heldItem;
    }

    private static String brushInteractionState() {
        var requirements = PokemonInteractions.INSTANCE.getInteractions().stream()
                .flatMap(set -> set.getInteractions().stream())
                .flatMap(interaction -> interaction.getRequirements().stream())
                .filter(OwnerHoldsItemRequirement.class::isInstance)
                .map(OwnerHoldsItemRequirement.class::cast)
                .toList();
        var brushStack = new ItemStack(Items.BRUSH);
        long matching = requirements.stream()
                .filter(requirement -> requirement.getItemCondition().test(brushStack)).count();
        return "matched=" + (matching > 0) + ", matching predicates=" + matching
                + ", owner item predicates=" + requirements.size()
                + ", interaction sets=" + PokemonInteractions.INSTANCE.getInteractions().size();
    }

    private static boolean hasDecodedBrushInteraction() {
        var brushStack = new ItemStack(Items.BRUSH);
        return PokemonInteractions.INSTANCE.getInteractions().stream()
                .flatMap(set -> set.getInteractions().stream())
                .flatMap(interaction -> interaction.getRequirements().stream())
                .filter(OwnerHoldsItemRequirement.class::isInstance)
                .map(OwnerHoldsItemRequirement.class::cast)
                .anyMatch(requirement -> requirement.getItemCondition().test(brushStack));
    }

    /** Forces both packet operations to prove they use the friendly buffer's registries. */
    private static void roundTripInteractionsWithoutFallback(net.minecraft.client.Minecraft client) {
        var resolver = RegistryAccessResolver.INSTANCE;
        var accessBefore = resolver.getAccess();
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), client.level.registryAccess());
        try {
            resolver.withAccess(RegistryAccess.EMPTY, () -> {
                var packet = new PokemonInteractionsSyncPacket(
                        List.copyOf(PokemonInteractions.INSTANCE.getInteractions()));
                packet.encode(wire);
                var decoded = PokemonInteractionsSyncPacket.Companion.decode(wire);
                new DataRegistrySyncPacketHandler<PokemonInteractionSet, PokemonInteractionsSyncPacket>()
                        .handle(decoded, client);
                if (resolver.getAccess() != RegistryAccess.EMPTY) {
                    throw new AssertionError("Data registry packet decode did not restore the outer registry scope");
                }
                return null;
            });
            if (wire.isReadable()) throw new AssertionError("Interaction packet round-trip left trailing frame bytes");
            if (resolver.getAccess() != accessBefore) {
                throw new AssertionError("Interaction packet round-trip leaked its registry scope");
            }
            if (!hasDecodedBrushInteraction()) {
                throw new AssertionError("Interaction packet round-trip lost the registry-backed brush predicate");
            }
        } finally {
            wire.release();
        }
    }

    private static long occupiedClientSlots(ClientParty party) {
        return party.getSlots().stream().filter(java.util.Objects::nonNull).count();
    }

    private static boolean recipeSnapshotHasEntries() {
        var snapshot = ClientCobblemonRecipeSnapshot.INSTANCE.snapshot();
        return !snapshot.getCookingPotRecipes().isEmpty() || !snapshot.getBrewingStandRecipes().isEmpty();
    }

    private static Path playerStorePath(Path worldDirectory, UUID playerId) {
        String value = playerId.toString();
        return worldDirectory.resolve("pokemon").resolve("playerpartystore")
                .resolve(value.substring(0, 2)).resolve(value + ".dat");
    }

    private static void waitForPartyFile(ClientGameTestContext context, Path partyFile) {
        Path temporaryFile = partyFile.resolveSibling(partyFile.getFileName() + ".temp");
        for (int tick = 0; tick < 200; tick++) {
            if (Files.isRegularFile(partyFile) && !Files.exists(temporaryFile)) return;
            context.waitTick();
        }
        throw new AssertionError("Dedicated disconnect did not flush party storage to " + partyFile);
    }

    private static boolean matches(Pokemon pokemon, Snapshot expected) {
        return pokemon != null && pokemon.getUuid().equals(expected.uuid)
                && pokemon.getSpecies().getResourceIdentifier().equals(expected.speciesId)
                && pokemon.getForm().formOnlyShowdownId().equals(expected.formId)
                && pokemon.getLevel() == expected.level
                && pokemon.getCurrentHealth() == expected.currentHealth
                && pokemon.getMoveSet().getMoves().stream()
                        .map(move -> move.getTemplate().getName()).toList().equals(expected.moveIds);
    }

    private static final class Snapshot {
        private final UUID uuid;
        private final net.minecraft.resources.Identifier speciesId;
        private final String formId;
        private final int level;
        private final int currentHealth;
        private final List<String> moveIds;

        private Snapshot(UUID uuid, net.minecraft.resources.Identifier speciesId, String formId,
                         int level, int currentHealth, List<String> moveIds) {
            this.uuid = uuid;
            this.speciesId = speciesId;
            this.formId = formId;
            this.level = level;
            this.currentHealth = currentHealth;
            this.moveIds = List.copyOf(moveIds);
        }

        private static Snapshot from(Pokemon pokemon) {
            return new Snapshot(pokemon.getUuid(), pokemon.getSpecies().getResourceIdentifier(),
                    pokemon.getForm().formOnlyShowdownId(), pokemon.getLevel(), pokemon.getCurrentHealth(),
                    pokemon.getMoveSet().getMoves().stream().map(move -> move.getTemplate().getName()).toList());
        }

        @Override
        public String toString() {
            return speciesId + "/" + formId + " " + uuid + " level=" + level
                    + " hp=" + currentHealth + " moves=" + moveIds;
        }
    }
}

package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.mixin.accessor.ChunkMapAccessor;
import com.cobblemon.mod.common.mixin.accessor.TrackedEntityAccessor;
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/** Exercises native tracking callbacks and checks party identity after automatic recall. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonVisibilityRecallClientGameTest {
    public static void verify(ClientGameTestContext context, TestSingleplayerContext world) {
        var first = spawn(context, world);
        long tick = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var map = ((ChunkMapAccessor) player.level().getChunkSource().chunkMap).getEntityMap();
            int originalSize = map.size();
            // Match ChunkMap.tick: a tracking callback executes during values iteration.
            for (var tracked : map.values()) {
                if (tracked == map.get(first.getId())) {
                    trackingCall(tracked, "removePlayer", player);
                    first.stopSeenByPlayer(player); // Duplicate notification must be harmless.
                    if (first.isRemoved() || map.size() != originalSize) {
                        throw new AssertionError("Visibility callback removed an entity inline during tracker iteration");
                    }
                }
            }
            return player.level().getGameTime();
        });
        waitPastTick(context, tick);
        world.getServer().computeOnServer(server -> {
            assertRecalled(world, first);
            return null;
        });

        var second = spawn(context, world);
        tick = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var map = ((ChunkMapAccessor) player.level().getChunkSource().chunkMap).getEntityMap();
            var tracked = map.get(second.getId());
            trackingCall(tracked, "removePlayer", player);
            trackingCall(tracked, "updatePlayer", player); // Owner regains tracking in the same tick.
            if (second.isRemoved()) throw new AssertionError("Owner re-entry could not cancel pending recall");
            return player.level().getGameTime();
        });
        waitPastTick(context, tick);
        world.getServer().computeOnServer(server -> {
            if (second.isRemoved() || second.getPokemon().getEntity() != second)
                throw new AssertionError("Pending recall removed a Pokémon after its owner regained tracking");
            return null;
        });

        tick = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(player.getX() + 128, player.getY(), player.getZ());
            return player.level().getGameTime();
        });
        waitPastTick(context, tick);
        world.getServer().computeOnServer(server -> {
            assertRecalled(world, second);
            return null;
        });
        System.out.println("[Cobblemon GameTest] PASS visibility recall: tracker iteration safe, duplicate notification harmless, owner re-entry cancels, teleport recalls without losing party identity");
    }

    private static PokemonEntity spawn(ClientGameTestContext context, TestSingleplayerContext world) {
        var entity = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var pokemon = PokemonProperties.Companion.parse("eevee level=25").create(player);
            if (!Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon))
                throw new AssertionError("Could not add recall fixture to party");
            var spawned = pokemon.sendOut(world.getConnection().getServerLevel(),
                    player.position().add(2, 0, 0), null, it -> { it.setNoAi(true); return kotlin.Unit.INSTANCE; });
            if (spawned == null) throw new AssertionError("Could not send out recall fixture");
            return spawned;
        });
        context.waitFor(client -> client.level.getEntity(entity.getId()) != null, 100);
        world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var tracked = ((ChunkMapAccessor) player.level().getChunkSource().chunkMap).getEntityMap().get(entity.getId());
            if (!(tracked instanceof TrackedEntityAccessor tracker)
                    || !tracker.getSeenBy().contains(player.connection)
                    || !player.getUUID().equals(entity.getOwnerUUID()))
                throw new AssertionError("Recall fixture is not owned and tracked by its player");
            return null;
        });
        return entity;
    }

    private static void waitPastTick(ClientGameTestContext context, long tick) {
        context.waitFor(client -> client.level.getGameTime() >= tick + 4, 100);
    }

    private static void assertRecalled(TestSingleplayerContext world, PokemonEntity entity) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(world.getConnection().getServerPlayer());
        int matches = 0;
        for (var pokemon : party) if (pokemon != null && pokemon.getUuid().equals(entity.getPokemon().getUuid())) {
            matches++;
            if (pokemon != entity.getPokemon()) throw new AssertionError("Recall replaced the party Pokémon");
        }
        if (!entity.isRemoved() || !(entity.getPokemon().getState() instanceof InactivePokemonState) || matches != 1)
            throw new AssertionError("Automatic recall did not preserve exactly one inactive party Pokémon");
    }

    private static void trackingCall(Object tracker, String method, ServerPlayer player) {
        if (tracker == null) throw new AssertionError("Native tracker missing");
        try {
            var callback = tracker.getClass().getDeclaredMethod(method, ServerPlayer.class);
            callback.setAccessible(true);
            callback.invoke(tracker, player);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Native tracking callback failed: " + method, failure);
        }
    }
}

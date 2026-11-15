package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.Moves;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Exercises an interleaving between the length prefix and the move bodies without timing a thread race. */
final class CobblemonMoveSetCodecClientGameTest {
    private static final String[] MOVE_NAMES = { "growl", "tailwhip", "thunderwave", "quickattack" };

    static void verify(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            for (int count = 0; count <= MOVE_NAMES.length; count++) {
                verifyRoundTrip(client.level.registryAccess(), count, null);
            }
            verifyRoundTrip(client.level.registryAccess(), 3, moves ->
                    moves.setMove(3, Moves.INSTANCE.getByNameOrDummy(MOVE_NAMES[3]).create()));
            verifyRoundTrip(client.level.registryAccess(), 3, MoveSet::clear);
            verifyRoundTrip(client.level.registryAccess(), 3, moves ->
                    moves.setMove(0, Moves.INSTANCE.getByNameOrDummy("splash").create()));
            return null;
        });
        System.out.println("[Cobblemon GameTest] MoveSet codec consumes every byte with 0-4 moves and during add/clear/replace interleavings");
    }

    private static void verifyRoundTrip(RegistryAccess registries, int count,
                                      java.util.function.Consumer<MoveSet> mutate) {
        var moves = new MoveSet();
        for (int index = 0; index < count; index++) {
            moves.setMove(index, Moves.INSTANCE.getByNameOrDummy(MOVE_NAMES[index]).create());
        }
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries) {
            private boolean prefixWritten;

            @Override
            public FriendlyByteBuf writeByte(int value) {
                super.writeByte(value);
                if (!prefixWritten) {
                    prefixWritten = true;
                    if (mutate != null) mutate.accept(moves);
                }
                return this;
            }
        };
        try {
            moves.saveToBuffer(buffer);
            var decoded = new MoveSet().loadFromBuffer(buffer);
            if (buffer.isReadable()) {
                throw new AssertionError("MoveSet codec left " + buffer.readableBytes() + " bytes after " + count + " moves");
            }
            if (decoded.getMoves().size() != count) {
                throw new AssertionError("MoveSet codec changed the encoded move count");
            }
            for (int index = 0; index < count; index++) {
                var actual = decoded.get(index);
                if (!MOVE_NAMES[index].equals(actual.getName()) || actual.getCurrentPp() != actual.getMaxPp()) {
                    throw new AssertionError("MoveSet codec did not preserve the move snapshot at index " + index);
                }
            }
        } finally {
            buffer.release();
        }
    }
}

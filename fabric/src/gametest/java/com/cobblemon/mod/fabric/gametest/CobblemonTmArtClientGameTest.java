package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.item.components.TMMoveComponent;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;

/** Checks the original per-element TM artwork, rather than merely the absence of missingno. */
public final class CobblemonTmArtClientGameTest {
    private static final List<String> ELEMENTAL_TYPES = List.of(
            "bug", "dark", "dragon", "electric", "fairy", "fighting", "fire", "flying", "ghost",
            "grass", "ground", "ice", "normal", "poison", "psychic", "rock", "steel", "water");

    public static void verify(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var failures = new java.util.ArrayList<String>();
            var moves = Moves.INSTANCE.all();
            for (var elementalType : ELEMENTAL_TYPES) {
                var move = moves.stream()
                        .filter(candidate -> elementalType.equals(candidate.getElementalType().getShowdownId()))
                        .findFirst()
                        .orElse(null);
                if (move == null) {
                    failures.add("No move fixture for type " + elementalType);
                    continue;
                }
                var stack = TMMoveComponent.Companion.createStack(move);
                verifyArtwork(client, stack, elementalType, move.getName(), failures);
            }

            // An item with no component and an unknown move must both use normal artwork.
            verifyArtwork(client, new net.minecraft.world.item.ItemStack(CobblemonItems.TECHNICAL_MACHINE),
                    "normal", "missing move", failures);
            var malformed = TMMoveComponent.Companion.createStack(Moves.INSTANCE.getByNameOrDummy("missing_move_fixture"));
            verifyArtwork(client, malformed, "normal", "unknown move", failures);

            if (!failures.isEmpty()) throw new AssertionError("TM move types lost original artwork: " + failures);
            return null;
        });
    }

    private static void verifyArtwork(net.minecraft.client.Minecraft client, net.minecraft.world.item.ItemStack stack,
                                      String expectedType, String label, List<String> failures) {
        var expected = Identifier.parse("cobblemon:item/tms/" + expectedType + "_tm");
        for (var display : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
            var state = new ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(state, stack, display, client.level, client.player, 0);
            var material = state.pickParticleMaterial(RandomSource.create(0));
            if (state.isEmpty() || material == null || !expected.equals(material.sprite().contents().name())) {
                failures.add(label + " in " + display + ": "
                        + (material == null ? "no material" : material.sprite().contents().name()));
            }
        }
    }
}

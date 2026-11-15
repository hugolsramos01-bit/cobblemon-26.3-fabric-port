package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.gui.summary.widgets.PartySlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.PartyWidget;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.util.Arrays;
import java.util.UUID;

/** Exercises Summary through Screen input dispatch and real party packets. */
@SuppressWarnings("UnstableApiUsage")
final class CobblemonSummaryClientGameTest {
    static void verify(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        UUID[] original = context.computeOnClient(client -> partyIds());
        context.computeOnClient(client -> {
            var summary = (Summary) client.gui.screen();
            var panel = (PartyWidget) summary.getSideScreen();
            int[][] positions = positions(panel);
            for (int index : new int[] { 2, 1, 3, 0, 2 }) {
                var click = at(panel, index);
                summary.mouseClicked(click, false);
                summary.mouseReleased(click);
                if (!summary.getSelectedPokemon$com_cobblemon_mod_common().getUuid().equals(original[index])) {
                    throw new AssertionError("Summary left click did not select Pokémon " + index);
                }
                assertStable(panel, positions);
            }
            // No frame or hover update between pointer events: use current event coordinates.
            var toggle = event(panel.getX() + 90, panel.getY() - 3);
            summary.mouseClicked(toggle, false);
            summary.mouseReleased(toggle);
            if (!panel.getSwapEnabled()) throw new AssertionError("Summary swap header did not enable reordering");
            var source = at(panel, 0);
            summary.mouseClicked(source, false);
            if (panel.getDraggedSlot() == null) throw new AssertionError("Summary did not start a drag preview");
            summary.mouseDragged(event(panel.getX() + 40, panel.getY() + 60), 10, 10);
            assertStable(panel, positions);
            summary.mouseDragged(event(panel.getX() - 20, panel.getY() - 20), -60, -80);
            summary.mouseReleased(event(panel.getX() - 20, panel.getY() - 20));
            if (panel.getDraggedSlot() != null || panel.getSwapSource() != null || summary.isDragging()) {
                throw new AssertionError("Summary retained a cancelled drag");
            }
            assertStable(panel, positions);
            return null;
        });
        assertOrder(context, singleplayer, original);

        UUID[] swapped = original.clone();
        swapped[0] = original[2];
        swapped[2] = original[0];
        drag(context, 0, 2);
        assertOrder(context, singleplayer, swapped);
        drag(context, 2, 0);
        assertOrder(context, singleplayer, original);

        UUID[] moved = original.clone();
        moved[4] = original[0];
        moved[0] = null;
        drag(context, 0, 4);
        assertOrder(context, singleplayer, moved);
        drag(context, 4, 0);
        assertOrder(context, singleplayer, original);

        context.computeOnClient(client -> {
            var summary = (Summary) client.gui.screen();
            var panel = (PartyWidget) summary.getSideScreen();
            var toggle = event(panel.getX() + 90, panel.getY() - 3);
            summary.mouseClicked(toggle, false);
            summary.mouseReleased(toggle);
            if (panel.getSwapEnabled()) throw new AssertionError("Summary swap header did not disable reordering");
            var target = at(panel, 1);
            summary.mouseClicked(new MouseButtonEvent(target.x(), target.y(), new MouseButtonInfo(2, 0)), false);
            summary.mouseReleased(new MouseButtonEvent(target.x(), target.y(), new MouseButtonInfo(2, 0)));
            if (panel.getDraggedSlot() != null) throw new AssertionError("Secondary click started a Summary drag");
            summary.mouseClicked(target, false);
            summary.mouseReleased(target);
            if (!summary.getSelectedPokemon$com_cobblemon_mod_common().getUuid().equals(original[1])) {
                throw new AssertionError("Summary selection failed after reordering");
            }
            return null;
        });
        context.takeScreenshot("cobblemon-26.3-summary-selection-verified");
        System.out.println("[Cobblemon GameTest] Summary selected party members, toggled swap, cancelled drag, swapped and moved through real packets without shifting cards");
    }

    private static void drag(ClientGameTestContext context, int source, int target) {
        context.computeOnClient(client -> {
            var summary = (Summary) client.gui.screen();
            var panel = (PartyWidget) summary.getSideScreen();
            int[][] positions = positions(panel);
            var start = at(panel, source);
            var end = at(panel, target);
            summary.mouseClicked(start, false);
            summary.mouseDragged(end, end.x() - start.x(), end.y() - start.y());
            assertStable(panel, positions);
            summary.mouseReleased(end);
            if (panel.getDraggedSlot() != null || panel.getSwapSource() != null || summary.isDragging()) {
                throw new AssertionError("Summary retained the drag after release");
            }
            return null;
        });
    }

    private static void assertOrder(ClientGameTestContext context, TestSingleplayerContext singleplayer, UUID[] expected) {
        context.waitFor(client -> Arrays.equals(partyIds(), expected), 100);
        singleplayer.getServer().computeOnServer(server -> {
            var party = Cobblemon.INSTANCE.getStorage().getParty(singleplayer.getConnection().getServerPlayer());
            for (int i = 0; i < expected.length; i++) {
                var pokemon = party.get(i);
                if (!java.util.Objects.equals(expected[i], pokemon == null ? null : pokemon.getUuid())) {
                    throw new AssertionError("Summary party packet did not update server slot " + i);
                }
            }
            return null;
        });
    }

    private static UUID[] partyIds() {
        return CobblemonClient.INSTANCE.getStorage().getParty().getSlots().stream()
                .map(pokemon -> pokemon == null ? null : pokemon.getUuid()).toArray(UUID[]::new);
    }

    private static int[][] positions(PartyWidget panel) {
        return panel.getChildren().stream().filter(child -> child instanceof PartySlotWidget)
                .map(child -> { var slot = (PartySlotWidget) child; return new int[] { slot.getX(), slot.getY() }; })
                .toArray(int[][]::new);
    }

    private static void assertStable(PartyWidget panel, int[][] expected) {
        if (!Arrays.deepEquals(expected, positions(panel))) throw new AssertionError("Summary moved a fixed party card");
    }

    private static MouseButtonEvent at(PartyWidget panel, int index) {
        return event(panel.getX() + 16 + (index % 2) * 51, panel.getY() + 17 + (index / 2) * 32 + (index % 2) * 8);
    }

    private static MouseButtonEvent event(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
    }
}

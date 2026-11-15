package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.gui.VariableObjectSelectionList;
import com.cobblemon.mod.common.client.gui.VariableObjectSelectionScrollbar;
import com.cobblemon.mod.common.client.gui.startselection.StarterSelectionScreen;
import com.cobblemon.mod.common.client.gui.startselection.widgets.CategoryList;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Exercises native Screen input routing for the starter category scrollbar. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonStarterScrollClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        var focus = System.getProperty("cobblemon.gametest.focus", "");
        if (!focus.isEmpty() && !focus.equals("starter-scrollbar")) {
            return;
        }
        try (var singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.computeOnClient(client -> {
                var categories = Cobblemon.INSTANCE.getStarterConfig().getStarters().stream()
                    .map(category -> category.asRenderableStarterCategory()).toList();
                client.setScreenAndShow(new StarterSelectionScreen(categories));
                return null;
            });
            context.waitTicks(2);
            context.computeOnClient(client -> {
                var screen = (StarterSelectionScreen) client.gui.screen();
                var list = screen.children().stream().filter(CategoryList.class::isInstance)
                    .map(CategoryList.class::cast).findFirst().orElseThrow();
                var bar = screen.children().stream().filter(VariableObjectSelectionScrollbar.class::isInstance)
                    .map(VariableObjectSelectionScrollbar.class::cast).findFirst().orElseThrow();
                if (list.getMaxScroll() <= 0 || !bar.scrollbarVisible()) {
                    throw new AssertionError("Starter scrollbar test requires overflowing categories");
                }
                var originalCategory = screen.getCurrentCategory();
                double x = bar.getX() + bar.getWidth() / 2.0;
                double top = bar.getY() + 2;
                double bottom = bar.getY() + bar.getHeight() - 2;
                list.setScroll(0);
                if (bar.mouseClicked(event(x, top, InputConstants.MOUSE_BUTTON_RIGHT), false)) {
                    throw new AssertionError("Starter scrollbar should not grab a right click");
                }
                screen.mouseClicked(event(x, top, InputConstants.MOUSE_BUTTON_RIGHT), false);
                if (list.getScrollAmount() != 0) {
                    throw new AssertionError("Right click should not move the starter scrollbar");
                }
                if (!screen.mouseClicked(event(x, top, InputConstants.MOUSE_BUTTON_LEFT), false)) {
                    throw new AssertionError("Native left click did not grab the starter scrollbar");
                }
                if (!screen.mouseDragged(event(x, bottom, InputConstants.MOUSE_BUTTON_LEFT), 0, bottom - top)
                        || list.getScrollAmount() != list.getMaxScroll()) {
                    throw new AssertionError("Dragging the starter scrollbar did not reach the final categories");
                }
                screen.mouseReleased(event(x, bottom, InputConstants.MOUSE_BUTTON_LEFT));
                double released = list.getScrollAmount();
                screen.mouseDragged(event(x, top, InputConstants.MOUSE_BUTTON_LEFT), 0, top - bottom);
                if (list.getScrollAmount() != released) {
                    throw new AssertionError("Starter scrollbar kept dragging after release");
                }
                list.setScroll(0);
                if (!screen.mouseClicked(event(x, bottom, InputConstants.MOUSE_BUTTON_LEFT), false)
                        || list.getScrollAmount() <= 0) {
                    throw new AssertionError("Clicking the starter scrollbar track did not jump through the list");
                }
                screen.mouseReleased(event(x, bottom, InputConstants.MOUSE_BUTTON_LEFT));
                list.setScroll(0);
                if (!screen.mouseScrolled(list.getX() + 20, list.getY() + 20, 0, -1)
                        || list.getScrollAmount() <= 0) {
                    throw new AssertionError("Mouse wheel over starter categories did not scroll");
                }
                list.setScroll(0);
                if (!screen.mouseScrolled(x, top, 0, -1) || list.getScrollAmount() <= 0) {
                    throw new AssertionError("Mouse wheel over starter scrollbar did not scroll");
                }
                if (screen.getCurrentCategory() != originalCategory || client.gui.screen() != screen) {
                    throw new AssertionError("Scrolling changed the starter selection or closed the screen");
                }
                var empty = new VariableObjectSelectionList(0, 0, 97, 145);
                var hiddenBar = new VariableObjectSelectionScrollbar(empty, 97, 0, 3, 145);
                if (hiddenBar.mouseClicked(event(98, 1, InputConstants.MOUSE_BUTTON_LEFT), false)
                        || !Double.isFinite(empty.getScrollAmount()) || empty.getScrollAmount() != 0) {
                    throw new AssertionError("A hidden starter scrollbar must ignore clicks");
                }
                return null;
            });
            context.takeScreenshot("cobblemon-starter-scrollbar-scrolled");
            System.out.println("[Cobblemon GameTest] PASS starter scrollbar: left drag/release, track, wheel, hidden bar");
            context.computeOnClient(client -> {
                client.setScreenAndShow(null);
                return null;
            });
        }
    }

    private static MouseButtonEvent event(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }
}

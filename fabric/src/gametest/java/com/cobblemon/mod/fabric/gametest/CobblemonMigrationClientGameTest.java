package com.cobblemon.mod.fabric.gametest;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Runs independent migration groups in separate disposable worlds and reports every group. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonMigrationClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        String focus = System.getProperty("cobblemon.gametest.focus", "");
        if (focus.equals("starter-scrollbar")) {
            // The dedicated starter screen entrypoint owns this focus.
            return;
        }
        if (focus.equals("smoke")) {
            System.out.println("[Cobblemon Migration] SKIP migration groups: explicit smoke focus");
            return;
        }
        if (focus.equals("base-defects")) {
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                CobblemonPokedexMigrationClientGameTest.verifyScannerHud(context);
                CobblemonTmArtClientGameTest.verify(context);
                verifyBuiltInResourcePackCompatibility(context);
            }
            return;
        }
        if (focus.equals("visibility-recall")) {
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                CobblemonVisibilityRecallClientGameTest.verify(context, world);
            }
            return;
        }
        if (focus.equals("pokedex")) {
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                CobblemonPokedexMigrationClientGameTest.run(context, world);
            }
            return;
        }
        if (focus.equals("persistence")) {
            CobblemonPersistenceClientGameTest.verify(context);
            return;
        }
        if (focus.equals("dedicated-sync")) {
            CobblemonDedicatedSyncClientGameTest.verify(context);
            return;
        }
        if (!focus.isEmpty() && !focus.equals("migration"))
            throw new IllegalArgumentException("Unknown GameTest focus: " + focus);
        var failures = new ArrayList<Throwable>();
        checkOwnedWorld("Saved world party and PC reload", failures,
                () -> CobblemonPersistenceClientGameTest.verify(context));
        checkOwnedWorld("Dedicated server login and reconnect", failures,
                () -> CobblemonDedicatedSyncClientGameTest.verify(context));
        check("Deferred visibility recall", context, failures,
                CobblemonVisibilityRecallClientGameTest::verify);
        check("JEI recipe tag retention", context, failures,
                (test, world) -> CobblemonRecipeTagsClientGameTest.verify(test));
        check("Pokédex scanner and tab input", context, failures,
                CobblemonPokedexMigrationClientGameTest::run);
        check("First-person Pokédex scanner HUD", context, failures,
                (test, world) -> CobblemonPokedexMigrationClientGameTest.verifyScannerHud(test));
        check("Brewing menu and authoritative shift-click", context, failures,
                CobblemonMachineMigrationGameTest::verifyBrewing);
        check("Cooking seasoning insertion and synchronization", context, failures,
                CobblemonMachineMigrationGameTest::verifyCooking);
        check("Healing machine current and legacy persistence", context, failures,
                (test, world) -> CobblemonMachineMigrationGameTest.verifyHealing(world));
        check("TM elemental artwork", context, failures,
                (test, world) -> CobblemonTmArtClientGameTest.verify(test));
        check("Built-in client resource pack compatibility", context, failures,
                (test, world) -> verifyBuiltInResourcePackCompatibility(test));
        if (!failures.isEmpty()) {
            var error = new AssertionError(failures.size() + " independent migration client group(s) failed");
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }

    private static void verifyBuiltInResourcePackCompatibility(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var incompatible = new ArrayList<String>();
            for (var id : java.util.List.of("cobblemon:gyaradosjump",
                    "cobblemon:regionbiasforms", "cobblemon:uniqueshinyforms")) {
                var pack = client.getResourcePackRepository().getPack(id);
                if (pack == null) incompatible.add(id + ": missing");
                else if (!pack.getCompatibility().isCompatible())
                    incompatible.add(id + ": " + pack.getCompatibility());
            }
            if (!incompatible.isEmpty())
                throw new AssertionError("Built-in packs rejected by native compatibility check: " + incompatible);
            return null;
        });
    }

    private static void check(String name, ClientGameTestContext context, ArrayList<Throwable> failures,
                              BiConsumer<ClientGameTestContext, TestSingleplayerContext> scenario) {
        System.out.println("[Cobblemon Migration] START " + name);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            scenario.accept(context, world);
            System.out.println("[Cobblemon Migration] PASS " + name);
        } catch (AssertionError | RuntimeException failure) {
            var annotated = new AssertionError(name, failure);
            failures.add(annotated);
            System.err.println("[Cobblemon Migration] FAIL " + name);
            annotated.printStackTrace();
        }
    }

    private static void checkOwnedWorld(String name, ArrayList<Throwable> failures, Runnable scenario) {
        System.out.println("[Cobblemon Migration] START " + name);
        try {
            scenario.run();
            System.out.println("[Cobblemon Migration] PASS " + name);
        } catch (AssertionError | RuntimeException failure) {
            var annotated = new AssertionError(name, failure);
            failures.add(annotated);
            System.err.println("[Cobblemon Migration] FAIL " + name);
            annotated.printStackTrace();
        }
    }
}

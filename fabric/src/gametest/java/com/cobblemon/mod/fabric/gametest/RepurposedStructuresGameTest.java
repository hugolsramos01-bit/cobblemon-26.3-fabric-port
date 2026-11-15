package com.cobblemon.mod.fabric.gametest;

import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Smoke-checks the optional, upstream 26.3 Repurposed Structures profile. */
public final class RepurposedStructuresGameTest implements CustomTestMethodInvoker {
    private static final String MOD_ID = "repurposed_structures";

    @GameTest
    public void upstream26_3StructuresAndPlacementsLoad(GameTestHelper context) {
        var mod = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (mod.isEmpty()) {
            System.out.println("[Cobblemon GameTest] SKIP Repurposed Structures: dev profile is not enabled");
            context.succeed();
            return;
        }

        var version = mod.get().getMetadata().getVersion().getFriendlyString();
        if (!version.equals("7.8.4+26.3-fabric")) {
            throw new AssertionError("Expected pinned Repurposed Structures 7.8.4+26.3-fabric, found " + version);
        }

        var registryAccess = context.getLevel().registryAccess();
        var structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        var structureSets = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
        var templatePools = registryAccess.lookupOrThrow(Registries.TEMPLATE_POOL);
        var processorLists = registryAccess.lookupOrThrow(Registries.PROCESSOR_LIST);
        var expectedStructures = List.of("fortress_jungle", "mansion_dappled", "mineshaft_pale_garden");
        var missingStructures = expectedStructures.stream()
                .map(path -> ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(MOD_ID, path)))
                .filter(key -> structures.get(key).isEmpty())
                .map(key -> key.identifier().toString())
                .toList();
        var expectedSets = List.of("mansions_dappled", "mineshafts_overworld", "villages_overworld");
        var missingSets = expectedSets.stream()
                .map(path -> ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath(MOD_ID, path)))
                .filter(key -> structureSets.get(key).isEmpty())
                .map(key -> key.identifier().toString())
                .toList();
        var cobblemonPokecenterPool = ResourceKey.create(Registries.TEMPLATE_POOL,
                Identifier.fromNamespaceAndPath("cobblemon", "villages/birch/pokecenter"));
        var birchMossifyProcessor = ResourceKey.create(Registries.PROCESSOR_LIST,
                Identifier.fromNamespaceAndPath(MOD_ID, "villages/birch/mossify"));
        boolean missingPokecenterPool = templatePools.get(cobblemonPokecenterPool).isEmpty();
        boolean missingMossifyProcessor = processorLists.get(birchMossifyProcessor).isEmpty();

        if (!missingStructures.isEmpty() || !missingSets.isEmpty() || missingPokecenterPool || missingMossifyProcessor) {
            throw new AssertionError("Repurposed Structures worldgen registry is incomplete; missing structures="
                    + missingStructures + ", structure_sets=" + missingSets
                    + ", Cobblemon Pokécenter pool missing=" + missingPokecenterPool
                    + ", RS mossify processor missing=" + missingMossifyProcessor
                    + ", selected packs=" + context.getLevel().getServer().getPackRepository().getSelectedIds()
                    + ", available Cobblemon packs=" + context.getLevel().getServer().getPackRepository()
                            .getAvailableIds().stream().filter(id -> id.contains("cobblemon")).toList());
        }

        System.out.println("[Cobblemon GameTest] Repurposed Structures " + version
                + " loaded; structure placements and Cobblemon Pokécenter pool/processor are present in the disposable world");
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}

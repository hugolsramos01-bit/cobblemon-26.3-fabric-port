package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeMap;

/** Reproduces JEI's local vanilla fallback without connecting to or modifying a live server. */
public final class CobblemonRecipeTagsClientGameTest {
    public static void verify(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            if (!FabricLoader.getInstance().isModLoaded("jei")) {
                throw new AssertionError("Recipe-tag regression requires the configured development JEI runtime");
            }
            var registries = client.level.registryAccess();
            var itemLookup = registries.lookupOrThrow(Registries.ITEM);
            var before = snapshot(itemLookup);
            if (before.keySet().stream().noneMatch(id -> id.startsWith("cobblemon:"))
                    || !new ItemStack(CobblemonItems.POKEDEX_RED).is(CobblemonItemTags.POKEDEX)) {
                throw new AssertionError("Client item tags were already incomplete before JEI fallback");
            }
            // The public accessor may return cached recipes and never execute the defective
            // branch. Invoke the installed version's fallback explicitly to test that branch.
            try {
                var loader = Class.forName("mezz.jei.common.recipes.VanillaClientRecipeLoader");
                var fallback = loader.getDeclaredMethod("loadVanillaRecipeRegistry", RegistryAccess.class);
                fallback.setAccessible(true);
                Object recipes = fallback.invoke(null, registries);
                if (!(recipes instanceof RecipeMap map) || map.values().isEmpty()) {
                    throw new AssertionError("JEI fallback did not actually load vanilla recipes");
                }
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Installed JEI fallback could not be exercised", exception);
            }
            var after = snapshot(itemLookup);
            if (!before.equals(after)) {
                var changed = before.entrySet().stream()
                        .filter(entry -> !entry.getValue().equals(after.get(entry.getKey())))
                        .map(Map.Entry::getKey).limit(15).toList();
                throw new AssertionError("JEI vanilla fallback changed live item tags: " + changed);
            }
            if (!new ItemStack(CobblemonItems.POKEDEX_RED).is(CobblemonItemTags.POKEDEX)) {
                throw new AssertionError("JEI fallback removed handheld Pokédex recognition");
            }
            System.out.println("[Cobblemon GameTest] JEI fallback loaded recipes and preserved all "
                    + before.size() + " live item tags and their members");
            return null;
        });
    }

    private static Map<String, Set<String>> snapshot(HolderLookup<Item> items) {
        var result = new TreeMap<String, Set<String>>();
        items.listTags().forEach(tag -> result.put(tag.key().location().toString(), tag.stream()
                .map(holder -> holder.unwrapKey().orElseThrow().identifier().toString()).collect(Collectors.toSet())));
        return result;
    }
}

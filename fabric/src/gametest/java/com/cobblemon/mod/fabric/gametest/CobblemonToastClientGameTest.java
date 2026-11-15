package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.tms.TechnicalMachines;
import com.cobblemon.mod.common.client.gui.toast.CobblemonToast;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

/** Validates real baked notification icons and renders the shared toast implementation. */
@SuppressWarnings("UnstableApiUsage")
final class CobblemonToastClientGameTest {
    static void verify(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            for (var item : CobblemonItems.INSTANCE.getPokedexes()) {
                var sprite = verifyIcon(client, item.getDefaultInstance());
                var itemId = BuiltInRegistries.ITEM.getKey(item);
                var expected = Identifier.fromNamespaceAndPath("cobblemon", "item/pokedexes/" + itemId.getPath());
                if (!sprite.equals(expected)) throw new AssertionError("Pokédex toast resolved the wrong sprite: " + sprite);
            }
            verifyIcon(client, new ItemStack(CobblemonItems.POKE_BALL));
            verifyIcon(client, new ItemStack(CobblemonItems.PC));
            var tms = TechnicalMachines.INSTANCE.getTmMap().values();
            if (tms.isEmpty()) throw new AssertionError("No TM notification icons were loaded for validation");
            for (var tm : tms) verifyIcon(client, tm.createItemStack());

            var manager = client.gui.toastManager();
            manager.clear();
            manager.addToast(toast("Change Party Selection", "Pokédex notification icon", new ItemStack(CobblemonItems.POKEDEX_RED)));
            manager.addToast(toast("Check out the starters!", "Poké Ball notification icon", new ItemStack(CobblemonItems.POKE_BALL)));
            manager.addToast(toast("Wallpaper unlocked", "PC notification icon", new ItemStack(CobblemonItems.PC)));
            manager.addToast(toast("New TMs Learned!", "TM notification icon", tms.iterator().next().createItemStack()));
            System.out.println("[Cobblemon GameTest] Baked toast icons resolved: seven Pokédex colours, Poké Ball, PC and " + tms.size() + " TMs");
            return null;
        });
        try {
            long deadline = System.nanoTime() + 900_000_000L;
            context.waitFor(client -> System.nanoTime() >= deadline, 100);
            context.takeScreenshot("cobblemon-26.3-toast-icons");
        } finally {
            context.computeOnClient(client -> { client.gui.toastManager().clear(); return null; });
        }
    }

    private static Identifier verifyIcon(Minecraft client, ItemStack stack) {
        var state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, client.level, null, 0);
        var material = state.pickParticleMaterial(RandomSource.create(0));
        if (state.isEmpty() || material == null || material.sprite().contents().name().getPath().equals("missingno")) {
            throw new AssertionError("Toast icon has no baked texture: " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return material.sprite().contents().name();
    }

    private static CobblemonToast toast(String title, String description, ItemStack icon) {
        return new CobblemonToast(UUID.randomUUID(), List.of(icon), Component.literal(title), Component.literal(description),
                Identifier.withDefaultNamespace("toast/advancement"), -1F, 0, null);
    }
}

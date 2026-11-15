package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokedex.Dexes;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI;
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton;
import com.cobblemon.mod.common.client.pokedex.PokedexType;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Exercises the handheld Pokédex use path and knowledge-gated GUI tabs in a disposable client world. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonPokedexMigrationClientGameTest {
    private static final int TAB_DESCRIPTION = 0;
    private static final int TAB_ABILITIES = 1;
    private static final int TAB_DROPS = 4;

    private CobblemonPokedexMigrationClientGameTest() {}

    public static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        verifyHeldItemStartsScanner(context);
        verifyKnowledgeGatedTabClicks(context);
        verifyScannerHud(context);
        verifyCompletedScan(context, singleplayer);
    }

    /** Hold the actual item, raycast a wild target, and wait for the server's registration packet. */
    private static void verifyCompletedScan(ClientGameTestContext context, TestSingleplayerContext world) {
        var target = world.getServer().computeOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var level = player.level();
            var base = player.blockPosition().above(60);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 7; z++)
                level.setBlockAndUpdate(base.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CobblemonItems.POKEDEX_RED));
            var pokemon = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion
                    .parse("eevee level=12").createEntity(level);
            pokemon.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 4.5);
            pokemon.setNoAi(true);
            pokemon.setNoGravity(true);
            level.addFreshEntity(pokemon);
            var dex = com.cobblemon.mod.common.Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
            dex.deleteSpeciesRecord(pokemon.getPokemon().getSpecies().getResourceIdentifier());
            return pokemon;
        });
        try {
            context.waitFor(client -> client.level.getEntity(target.getId()) != null
                    && client.player.getMainHandItem().is(CobblemonItems.POKEDEX_RED)
                    && client.player.getY() > target.getY() - 1, 100);
            // A completion packet cannot register a target without a valid start.
            context.computeOnClient(client -> {
                new com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishScanningPacket(
                        target.getId(), 0).sendToServer();
                return null;
            });
            world.getConnection().waitForServerboundPackets();
            world.getServer().computeOnServer(server -> {
                var dex = com.cobblemon.mod.common.Cobblemon.INSTANCE.getPlayerDataManager()
                        .getPokedexData(world.getConnection().getServerPlayer());
                if (dex.getHighestKnowledgeForSpecies(target.getPokemon().getSpecies().getResourceIdentifier())
                        != PokedexEntryProgress.UNREGISTERED)
                    throw new AssertionError("Server registered a scan completion without a start packet");
                return null;
            });
            context.computeOnClient(client -> {
                client.gui.setScreen(null);
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                var usage = CobblemonClient.INSTANCE.getPokedexUsageContext();
                usage.resetState(true);
                CobblemonClient.INSTANCE.getClientPokedexData().deleteSpeciesRecord(
                        target.getPokemon().getSpecies().getResourceIdentifier());
                var entity = client.level.getEntity(target.getId());
                var direction = entity.position().add(0, entity.getBbHeight() * 0.5, 0)
                        .subtract(client.player.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
                float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
                client.player.setYRot(yaw);
                client.player.setXRot(pitch);
                client.player.setYHeadRot(yaw);
                client.options.keyUse.setDown(true);
                if (!client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND).consumesAction())
                    throw new AssertionError("Actual client Pokédex use was rejected");
                return null;
            });
            context.waitFor(client -> CobblemonClient.INSTANCE.getPokedexUsageContext().getRegisterCompleted(), 300);
            boolean serverRegistered = world.getServer().computeOnServer(server ->
                    com.cobblemon.mod.common.Cobblemon.INSTANCE.getPlayerDataManager()
                            .getPokedexData(world.getConnection().getServerPlayer())
                            .getHighestKnowledgeForSpecies(target.getPokemon().getSpecies().getResourceIdentifier())
                            == PokedexEntryProgress.SEEN);
            boolean clientRegistered = context.computeOnClient(client ->
                    CobblemonClient.INSTANCE.getClientPokedexData().getHighestKnowledgeForSpecies(
                            target.getPokemon().getSpecies().getResourceIdentifier()) == PokedexEntryProgress.SEEN);
            if (!serverRegistered || !clientRegistered)
                throw new AssertionError("Completed wild scan did not synchronize SEEN knowledge on both sides");
            System.out.println("[Cobblemon GameTest] Real held-item scanner raycast completed registration through client/server packets");
        } finally {
            context.computeOnClient(client -> {
                client.options.keyUse.setDown(false);
                if (client.player.isUsingItem()) client.gameMode.releaseUsingItem(client.player);
                CobblemonClient.INSTANCE.getPokedexUsageContext().resetState(true);
                return null;
            });
            world.getServer().computeOnServer(server -> {
                target.discard();
                world.getConnection().getServerPlayer().stopUsingItem();
                return null;
            });
        }
    }

    /** Verify the installed Fabric replacement with the real vanilla hotbar extractor. */
    public static void verifyScannerHud(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var usage = CobblemonClient.INSTANCE.getPokedexUsageContext();
            var oldCamera = client.options.getCameraType();
            boolean wasScanning = usage.getScanningGuiOpen();
            try {
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                usage.setScanningGuiOpen(false);
                int normal = extractRegisteredHotbar(client);
                if (normal == 0) throw new AssertionError("Native hotbar fixture produced no GUI elements");
                usage.setScanningGuiOpen(true);
                if (extractRegisteredHotbar(client) != 0)
                    throw new AssertionError("Vanilla hotbar remained visible inside the first-person scanner");
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                if (extractRegisteredHotbar(client) == 0)
                    throw new AssertionError("Scanner incorrectly hid the third-person hotbar");
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                usage.setScanningGuiOpen(false);
                if (extractRegisteredHotbar(client) == 0)
                    throw new AssertionError("Closing the scanner did not restore the native hotbar");
            } finally {
                usage.setScanningGuiOpen(wasScanning);
                client.options.setCameraType(oldCamera);
            }
            return null;
        });
        System.out.println("[Cobblemon GameTest] Native hotbar extraction hides during first-person scanning and restores in third person/after closing");
    }

    private static int extractRegisteredHotbar(net.minecraft.client.Minecraft client) {
        try {
            // Inspect installed registry wiring, not a separately constructed predicate.
            Class<?> registry = Class.forName("net.fabricmc.fabric.impl.client.rendering.hud.HudElementRegistryImpl");
            Object root = registry.getMethod("getRoot", net.minecraft.resources.Identifier.class)
                    .invoke(null, net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR);
            var layers = (List<?>) root.getClass().getMethod("layers").invoke(root);
            var layerType = Class.forName("net.fabricmc.fabric.impl.client.rendering.hud.HudLayer");
            var vanilla = net.minecraft.client.gui.Hud.class.getDeclaredMethod("extractItemHotbar",
                    net.minecraft.client.gui.GuiGraphicsExtractor.class, net.minecraft.client.DeltaTracker.class);
            vanilla.setAccessible(true);
            var state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
            var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(client, state, 0, 0);
            net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement original = (g, ticks) -> {
                try { vanilla.invoke(client.gui.hud, g, ticks); }
                catch (ReflectiveOperationException e) { throw new AssertionError("Native hotbar extraction failed", e); }
            };
            for (var layer : layers) {
                if (net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR
                        .equals(layerType.getMethod("id").invoke(layer))) {
                    var registered = (net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement)
                            layerType.getMethod("element", net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement.class)
                                    .invoke(layer, original);
                    registered.extractRenderState(graphics, client.getDeltaTracker());
                    var count = new java.util.concurrent.atomic.AtomicInteger();
                    state.forEachElement(element -> count.incrementAndGet(),
                            net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
                    return count.get();
                }
            }
            throw new AssertionError("Fabric hotbar layer was not registered");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not inspect native HUD registration", failure);
        }
    }

    private static void verifyHeldItemStartsScanner(ClientGameTestContext context) {
        ItemUseSnapshot snapshot = context.computeOnClient(client -> {
            if (client.player.isUsingItem()) {
                throw new AssertionError("Cannot test Pokédex use while the player is already using an item");
            }
            var usage = CobblemonClient.INSTANCE.getPokedexUsageContext();
            // infoGuiOpen is a legacy sticky flag, not the current screen's lifecycle state.
            if (usage.getScanningGuiOpen() || client.gui.screen() instanceof PokedexGUI
                    || usage.getTransitionIntervals() > 0.0F) {
                throw new AssertionError("Cannot test Pokédex use while its scanner is already active");
            }
            return new ItemUseSnapshot(
                    client.player.getItemInHand(InteractionHand.MAIN_HAND).copy(),
                    client.options.keyUse.isDown(),
                    usage.getType());
        });
        try {
            context.computeOnClient(client -> {
                var player = client.player;
                var item = CobblemonItems.POKEDEX_RED;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                client.options.keyUse.setDown(true);
                InteractionResult result = item.use(client.level, player, InteractionHand.MAIN_HAND);
                if (!result.consumesAction() || !player.isUsingItem()) {
                    throw new AssertionError("Using the held Pokédex did not start the client's item-use state: " + result);
                }
                if (CobblemonClient.INSTANCE.getPokedexUsageContext().getType() != PokedexType.RED) {
                    throw new AssertionError("Using the red Pokédex did not select the red scanner skin");
                }
                return null;
            });

            context.waitFor(client -> client.player != null && client.player.isUsingItem()
                    && CobblemonClient.INSTANCE.getPokedexUsageContext().getScanningGuiOpen(), 100);
            boolean scannerOpened = context.computeOnClient(client ->
                    CobblemonClient.INSTANCE.getPokedexUsageContext().getScanningGuiOpen()
                            && CobblemonClient.INSTANCE.getPokedexUsageContext().getType() == PokedexType.RED);
            if (!scannerOpened) {
                throw new AssertionError("Holding the Pokédex through its use-tick threshold did not open the scanner");
            }
        } finally {
            context.computeOnClient(client -> {
                if (client.player.isUsingItem()) client.player.stopUsingItem();
                client.options.keyUse.setDown(snapshot.useKeyDown());
                client.player.setItemInHand(InteractionHand.MAIN_HAND, snapshot.handStack().copy());
                var usage = CobblemonClient.INSTANCE.getPokedexUsageContext();
                usage.resetState(true);
                usage.setType(snapshot.pokedexType());
                return null;
            });
        }
        System.out.println("[Cobblemon GameTest] Held Pokédex use started client prediction and opened the scanner after five use ticks");
    }

    private static void verifyKnowledgeGatedTabClicks(ClientGameTestContext context) {
        DexInteractionSnapshot snapshot = context.computeOnClient(client -> {
            var dex = CobblemonClient.INSTANCE.getClientPokedexData();
            var originalScreen = client.gui.screen();
            PokedexEntry entry = Dexes.INSTANCE.getDexEntryMap().values().stream()
                    .flatMap(definition -> definition.getEntries().stream())
                    .filter(candidate -> candidate.getConditionAspects().isEmpty())
                    .filter(candidate -> !candidate.getForms().isEmpty()
                            && candidate.getForms().stream().allMatch(form -> !form.getUnlockForms().isEmpty()))
                    .filter(candidate -> candidate.getForms().stream()
                            .flatMap(form -> form.getUnlockForms().stream())
                            .map(formName -> dex.getSpeciesRecord(candidate.getSpeciesId()) == null ? null
                                    : dex.getSpeciesRecord(candidate.getSpeciesId()).getFormRecord(formName))
                            .allMatch(record -> record == null
                                    || record.getKnowledge() == PokedexEntryProgress.UNREGISTERED))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No unconditional Pokédex entry with controllable form knowledge was loaded"));
            var originalRecord = dex.getSpeciesRecord(entry.getSpeciesId());
            Map<String, PokedexEntryProgress> originalProgress = new LinkedHashMap<>();
            for (var form : entry.getForms()) {
                for (String formName : form.getUnlockForms()) {
                    String normalized = formName.toLowerCase(Locale.ROOT);
                    var previous = originalRecord == null ? null : originalRecord.getFormRecord(normalized);
                    originalProgress.putIfAbsent(normalized, previous == null
                            ? null : previous.getKnowledge());
                }
            }
            return new DexInteractionSnapshot(entry, originalRecord != null, originalProgress, originalScreen);
        });
        try {
            context.computeOnClient(client -> {
                var dex = CobblemonClient.INSTANCE.getClientPokedexData();
                PokedexEntry entry = snapshot.entry();
                var record = dex.getOrCreateSpeciesRecord(entry.getSpeciesId());
                for (String formName : snapshot.formProgress().keySet()) {
                    record.getOrCreateFormRecord(formName).setKnowledgeProgress(PokedexEntryProgress.SEEN);
                }

                PokedexGUI.Companion.open(dex, PokedexType.RED, null, null);
                if (!(client.gui.screen() instanceof PokedexGUI gui)) {
                    throw new AssertionError("The Pokédex screen did not open for tab interaction");
                }
                gui.setSelectedEntry(entry);
                if (gui.getTabInfoIndex() != TAB_DESCRIPTION || gui.canSelectTab(TAB_ABILITIES)) {
                    throw new AssertionError("A merely encountered form incorrectly unlocked its abilities tab");
                }

                clickTab(gui, TAB_ABILITIES);
                if (gui.getTabInfoIndex() != TAB_DESCRIPTION) {
                    throw new AssertionError("Clicking the locked abilities tab changed the selected Pokédex tab");
                }

                if (!gui.canSelectTab(TAB_DROPS)) {
                    throw new AssertionError("Encountered-form knowledge did not unlock the drops tab");
                }
                clickTab(gui, TAB_DROPS);
                if (gui.getTabInfoIndex() != TAB_DROPS) {
                    throw new AssertionError("Clicking the encountered-form drops tab did not select it");
                }

                for (String formName : snapshot.formProgress().keySet()) {
                    record.getOrCreateFormRecord(formName).setKnowledgeProgress(PokedexEntryProgress.OWNED);
                }
                gui.setSelectedEntry(entry); // Refresh active buttons after the controlled record update.
                if (!gui.canSelectTab(TAB_ABILITIES)) {
                    throw new AssertionError("Owned-form knowledge did not unlock the abilities tab");
                }
                clickTab(gui, TAB_ABILITIES);
                if (gui.getTabInfoIndex() != TAB_ABILITIES) {
                    throw new AssertionError("Clicking the owned-form abilities tab did not select it");
                }
                return null;
            });
        } finally {
            context.computeOnClient(client -> {
                var dex = CobblemonClient.INSTANCE.getClientPokedexData();
                var record = dex.getSpeciesRecord(snapshot.entry().getSpeciesId());
                if (record != null) {
                    for (var previous : snapshot.formProgress().entrySet()) {
                        if (previous.getValue() == null) record.deleteFormRecord(previous.getKey());
                        else record.getOrCreateFormRecord(previous.getKey()).setKnowledgeProgress(previous.getValue());
                    }
                    if (!snapshot.hadSpeciesRecord() && record.isFormRecordsEmpty()) {
                        dex.deleteSpeciesRecord(snapshot.entry().getSpeciesId());
                    }
                }
                client.gui.setScreen(snapshot.previousScreen());
                return null;
            });
        }
        System.out.println("[Cobblemon GameTest] Pokédex tab clicks enforce seen-form and owned-form knowledge gates");
    }

    private static void clickTab(PokedexGUI gui, int tabIndex) {
        int left = (gui.width - 345) / 2;
        int top = (gui.height - 207) / 2;
        List<ScaledButton> tabs = gui.children().stream()
                .filter(ScaledButton.class::isInstance)
                .map(ScaledButton.class::cast)
                .filter(button -> Math.abs(button.getY() - (top + 181)) <= 1)
                .filter(button -> button.getX() >= left + 180)
                .sorted(Comparator.comparingInt(ScaledButton::getX))
                .toList();
        if (tabs.size() != 7) {
            throw new AssertionError("Expected seven Pokédex tab buttons, found " + tabs.size());
        }
        ScaledButton target = tabs.get(tabIndex);
        double clickX = target.getButtonX() + target.getButtonWidth().doubleValue() * target.getScale() / 2.0;
        double clickY = target.getButtonY() + target.getButtonHeight().doubleValue() * target.getScale() / 2.0;
        gui.mouseClicked(new MouseButtonEvent(clickX, clickY,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
    }

    private record ItemUseSnapshot(ItemStack handStack, boolean useKeyDown, PokedexType pokedexType) {}

    private record DexInteractionSnapshot(PokedexEntry entry, boolean hadSpeciesRecord,
                                          Map<String, PokedexEntryProgress> formProgress,
                                          net.minecraft.client.gui.screens.Screen previousScreen) {}

}

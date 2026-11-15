package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.api.cooking.Seasonings;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.CobblemonBlocks;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

/** Focused client/server regression coverage for machine menu migrations. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonMachineMigrationGameTest {
    private CobblemonMachineMigrationGameTest() {}

    /** Called by the shared smoke test after creating its disposable singleplayer world. */
    public static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        verifyBrewing(context, singleplayer);
        verifyCooking(context, singleplayer);
        verifyHealing(singleplayer);
        System.out.println("[Cobblemon GameTest] Brewing menu quick-move and medicinal brew processing; closed-lid aprijuice processing and client sync; cooking seasoning validation; healing machine current/legacy persistence");
    }

    public static void verifyBrewing(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        var serverState = singleplayer.getServer().computeOnServer(server -> {
            var connection = singleplayer.getConnection();
            var level = connection.getServerLevel();
            var player = connection.getServerPlayer();
            var position = player.blockPosition().offset(2, 0, 0);
            var previousSupport = level.getBlockState(position.below());
            var previousBlock = level.getBlockState(position);
            level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(position, Blocks.BREWING_STAND.defaultBlockState());
            if (!(level.getBlockEntity(position) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand)) {
                throw new AssertionError("Placed brewing stand did not create its block entity");
            }
            ItemStack previousHotbarStack = player.getInventory().getItem(0).copy();
            player.getInventory().setItem(0, new ItemStack(Items.POTION));
            var result = level.getBlockState(position).useWithoutItem(level, player,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            if (!result.consumesAction() || !(player.containerMenu instanceof BrewingStandMenu)) {
                throw new AssertionError("Using a placed brewing stand did not open its server menu");
            }
            return new Object[] { position, previousHotbarStack, previousBlock, previousSupport };
        });

        try {
            context.waitFor(client -> client.player.containerMenu instanceof BrewingStandMenu menu
                    && menu.slots.get(32).getItem().is(Items.POTION), 100);
            context.computeOnClient(client -> {
                if (!(client.player.containerMenu instanceof BrewingStandMenu menu)) {
                    throw new AssertionError("Server did not open a client brewing menu");
                }
                if (!menu.slots.get(32).getItem().is(Items.POTION)) {
                    throw new AssertionError("Client brewing menu did not receive the hotbar potion");
                }
                client.gameMode.handleContainerInput(menu.containerId, 32, 0, ContainerInput.QUICK_MOVE, client.player);
                return null;
            });

            context.waitFor(client -> client.player.containerMenu instanceof BrewingStandMenu menu
                    && menu.slots.get(0).getItem().is(Items.POTION), 100);
            boolean clientHasBottle = context.computeOnClient(client -> client.player.containerMenu instanceof BrewingStandMenu menu
                    && menu.slots.get(0).getItem().is(Items.POTION));
            boolean serverHasBottle = singleplayer.getServer().computeOnServer(server -> {
                var level = singleplayer.getConnection().getServerLevel();
                return level.getBlockEntity((BlockPos) serverState[0]) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand
                        && stand.getItem(0).is(Items.POTION);
            });
            if (!clientHasBottle || !serverHasBottle) {
                throw new AssertionError("Server-authoritative brewing shift-click did not place the potion in bottle slot 0");
            }

            singleplayer.getServer().computeOnServer(server -> {
                var connection = singleplayer.getConnection();
                var level = connection.getServerLevel();
                var player = connection.getServerPlayer();
                var position = (BlockPos) serverState[0];
                player.closeContainer();
                if (!(level.getBlockEntity(position) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand)) {
                    throw new AssertionError("Placed brewing stand disappeared before processing");
                }
                stand.setItem(0, new ItemStack(CobblemonItems.MEDICINAL_BREW));
                stand.setItem(3, new ItemStack(CobblemonItems.ORAN_BERRY));
                stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));

                // Run the real native stand ticker for 450 deterministic server ticks.
                for (int tick = 0; tick < 450; tick++) {
                    net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(
                            level, position, level.getBlockState(position), stand);
                }
                if (!stand.getItem(0).is(CobblemonItems.POTION)
                        || !stand.getItem(3).isEmpty()
                        || !stand.getItem(4).isEmpty()) {
                    throw new AssertionError("Medicinal Brew + Oran Berry did not brew Cobblemon Potion and consume ingredient/fuel");
                }
                return null;
            });
        } finally {
            singleplayer.getServer().computeOnServer(server -> {
                var connection = singleplayer.getConnection();
                var level = connection.getServerLevel();
                var player = connection.getServerPlayer();
                BlockPos position = (BlockPos) serverState[0];
                var stand = level.getBlockEntity(position);
                if (stand instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity brewingStand) {
                    for (int slot = 0; slot < brewingStand.getContainerSize(); slot++) {
                        brewingStand.setItem(slot, ItemStack.EMPTY);
                    }
                }
                player.getInventory().setItem(0, (ItemStack) serverState[1]);
                player.closeContainer();
                level.setBlockAndUpdate(position, (net.minecraft.world.level.block.state.BlockState) serverState[2]);
                level.setBlockAndUpdate(position.below(), (net.minecraft.world.level.block.state.BlockState) serverState[3]);
                return null;
            });
            context.computeOnClient(client -> {
                client.gui.setScreen(null);
                return null;
            });
        }
    }

    public static void verifyCooking(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        verifyCookingPotSeasoning(singleplayer);
        verifyCookingPotSeasoningSync(context, singleplayer);
    }

    private static void verifyCookingPotSeasoning(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var menu = new CookingPotMenu(0, player.getInventory());
            TagKey<net.minecraft.world.item.Item> recipeSeasonings = TagKey.create(
                    Registries.ITEM, Identifier.fromNamespaceAndPath("cobblemon", "recipe_filters/bait_seasoning"));
            menu.setCurrentSeasoningTag(recipeSeasonings);

            ItemStack validSeasoning = new ItemStack(Items.APPLE, 3);
            ItemStack otherSeasoning = new ItemStack(Items.CARROT, 2);
            if (Seasonings.INSTANCE.getFromItemStack(validSeasoning) == null
                    || Seasonings.INSTANCE.getFromItemStack(otherSeasoning) == null) {
                throw new AssertionError("Bundled seasoning registry did not recognize the cooking test items");
            }
            if (!menu.slots.get(10).mayPlace(validSeasoning)) {
                throw new AssertionError("Cooking pot rejected a seasoning accepted by the current recipe tag");
            }
            if (menu.slots.get(10).mayPlace(otherSeasoning)) {
                throw new AssertionError("Cooking pot accepted a seasoning outside the current recipe tag");
            }

            ItemStack previousHotbarStack = player.getInventory().getItem(0).copy();
            try {
                // The server's shift-click path must route an allowed seasoning into the
                // seasoning compartment and leave a disallowed seasoning in player inventory.
                player.getInventory().setItem(0, validSeasoning.copy());
                menu.quickMoveStack(player, 40); // hotbar slot zero in the menu
                if (menu.getContainer().getItem(10).getCount() != 3 || !player.getInventory().getItem(0).isEmpty()) {
                    throw new AssertionError("Server cooking-pot shift-click did not insert a valid seasoning");
                }

                player.getInventory().setItem(0, otherSeasoning.copy());
                ItemStack rejected = menu.quickMoveStack(player, 40);
                if (!rejected.isEmpty() || player.getInventory().getItem(0).getCount() != 2
                        || !menu.getContainer().getItem(11).isEmpty() || !menu.getContainer().getItem(12).isEmpty()) {
                    throw new AssertionError("Server cooking-pot shift-click moved a seasoning excluded by the recipe tag");
                }
            } finally {
                menu.getContainer().clearContent();
                player.getInventory().setItem(0, previousHotbarStack);
                menu.removed(player);
            }
            return null;
        });
    }

    private static void verifyCookingPotSeasoningSync(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Object[] fixture = singleplayer.getServer().computeOnServer(server -> {
            var connection = singleplayer.getConnection();
            var level = connection.getServerLevel();
            var player = connection.getServerPlayer();
            var campfirePosition = player.blockPosition().offset(3, 0, 0);
            var previousSupport = level.getBlockState(campfirePosition.below());
            var previousBlock = level.getBlockState(campfirePosition);
            level.setBlockAndUpdate(campfirePosition.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(campfirePosition, CobblemonBlocks.CAMPFIRE.defaultBlockState());
            var campfire = (CampfireBlockEntity) level.getBlockEntity(campfirePosition);
            if (campfire == null) throw new AssertionError("Placed cooking pot did not create its block entity");

            // green_aprijuice is a bundled recipe whose flavour-seasoning tag accepts berries.
            campfire.setItem(1, new ItemStack(CobblemonItems.GREEN_APRICORN));
            campfire.setItem(2, new ItemStack(CobblemonItems.PEP_UP_FLOWER));
            campfire.setItem(3, new ItemStack(CobblemonItems.ENERGY_ROOT));
            player.openMenu(campfire);
            if (!(player.containerMenu instanceof CookingPotMenu menu)) {
                throw new AssertionError("Using the cooking pot did not open its server menu");
            }
            menu.slotsChanged(campfire);
            var expectedTag = TagKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("cobblemon", "recipe_filters/flavour_seasoning"));
            if (!new ItemStack(CobblemonItems.ORAN_BERRY).is(expectedTag)) {
                throw new AssertionError("Oran Berry fixture is not in the bundled flavour-seasoning tag");
            }
            if (menu.getCurrentSeasoningTag() == null || !menu.getCurrentSeasoningTag().equals(expectedTag)) {
                throw new AssertionError("The bundled aprijuice recipe did not select its flavour-seasoning tag");
            }
            menu.broadcastChanges();
            return new Object[] { campfirePosition, previousBlock, previousSupport };
        });

        BlockPos campfirePosition = (BlockPos) fixture[0];
        try {
            context.waitFor(client -> client.player.containerMenu instanceof CookingPotMenu menu
                    && expectedSeasoningAccepted(menu), 100);
            boolean seasoningSynced = context.computeOnClient(client -> client.player.containerMenu instanceof CookingPotMenu menu
                    && expectedSeasoningAccepted(menu));
            if (!seasoningSynced) {
                throw new AssertionError("Server recipe seasoning tag did not reach the active client cooking-pot menu");
            }

            singleplayer.getServer().computeOnServer(server -> {
                var level = singleplayer.getConnection().getServerLevel();
                var campfire = (CampfireBlockEntity) level.getBlockEntity(campfirePosition);
                if (campfire == null) throw new AssertionError("Cooking-pot block entity disappeared before processing");
                campfire.setItem(10, new ItemStack(CobblemonItems.ORAN_BERRY));
                campfire.toggleLid(false); // Close the lid; the server recipe ticker cooks only while it is closed.

                // Deterministic calls to the actual block entity ticker stand in for 100 world ticks.
                for (int tick = 0; tick < CampfireBlockEntity.COOKING_TOTAL_TIME
                        / CampfireBlockEntity.COOKING_PROGRESS_PER_TICK; tick++) {
                    CampfireBlockEntity.Companion.serverTick(level, campfirePosition, level.getBlockState(campfirePosition), campfire);
                }
                if (!campfire.getItem(0).is(CobblemonItems.APRIJUICE_GREEN)
                        || !campfire.getItem(1).isEmpty()
                        || !campfire.getItem(2).isEmpty()
                        || !campfire.getItem(3).isEmpty()) {
                    throw new AssertionError("Closed cooking pot did not produce green aprijuice and consume its three ingredients");
                }
                return null;
            });

            context.waitFor(client -> client.player.containerMenu instanceof CookingPotMenu menu
                    && menu.slots.get(0).getItem().is(CobblemonItems.APRIJUICE_GREEN)
                    && menu.slots.get(1).getItem().isEmpty()
                    && menu.slots.get(2).getItem().isEmpty()
                    && menu.slots.get(3).getItem().isEmpty(), 100);
            boolean resultSynced = context.computeOnClient(client -> client.player.containerMenu instanceof CookingPotMenu menu
                    && menu.slots.get(0).getItem().is(CobblemonItems.APRIJUICE_GREEN)
                    && menu.slots.get(1).getItem().isEmpty()
                    && menu.slots.get(2).getItem().isEmpty()
                    && menu.slots.get(3).getItem().isEmpty());
            if (!resultSynced) throw new AssertionError("Cooking output and consumed ingredients did not synchronize to the client menu");
            System.out.println("[Cobblemon GameTest] Cooking pot completed via 100 deterministic server block-entity ticks");
        } finally {
            singleplayer.getServer().computeOnServer(server -> {
                var connection = singleplayer.getConnection();
                var level = connection.getServerLevel();
                var player = connection.getServerPlayer();
                player.closeContainer();
                level.setBlockAndUpdate(campfirePosition, (net.minecraft.world.level.block.state.BlockState) fixture[1]);
                level.setBlockAndUpdate(campfirePosition.below(), (net.minecraft.world.level.block.state.BlockState) fixture[2]);
                return null;
            });
            context.computeOnClient(client -> {
                client.gui.setScreen(null);
                return null;
            });
        }
    }

    private static boolean expectedSeasoningAccepted(CookingPotMenu menu) {
        var tag = menu.getCurrentSeasoningTag();
        return tag != null
                && tag.location().equals(Identifier.fromNamespaceAndPath("cobblemon", "recipe_filters/flavour_seasoning"))
                && menu.slots.get(10).mayPlace(new ItemStack(CobblemonItems.ORAN_BERRY));
    }

    public static void verifyHealing(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().computeOnServer(server -> {
            var level = singleplayer.getConnection().getServerLevel();
            var position = BlockPos.ZERO;
            var original = new HealingMachineBlockEntity(position, CobblemonBlocks.HEALING_MACHINE.defaultBlockState());
            var pokeBall = PokeBalls.INSTANCE.getPokeBall(Identifier.fromNamespaceAndPath("cobblemon", "poke_ball"));
            if (pokeBall == null) throw new AssertionError("Bundled Poké Ball registry entry was missing");
            original.pokeBalls().put(0, pokeBall);
            original.setHealTimeLeft(13);
            original.setHealingCharge(2.5F);
            original.setInfinite(true);

            var currentFormat = original.saveWithoutMetadata(level.registryAccess());
            var currentLoaded = new HealingMachineBlockEntity(position, CobblemonBlocks.HEALING_MACHINE.defaultBlockState());
            currentLoaded.loadWithComponents(TagValueInput.create(
                    ProblemReporter.DISCARDING, level.registryAccess(), currentFormat));
            if (currentLoaded.pokeBalls().get(0) != pokeBall || currentLoaded.getHealTimeLeft() != 13
                    || Math.abs(currentLoaded.getHealingCharge() - 2.5F) > 0.001F || !currentLoaded.getInfinite()) {
                throw new AssertionError("Healing machine did not reload the current list-based saved data");
            }

            var legacyFormat = currentFormat.copy();
            var legacyBalls = new net.minecraft.nbt.CompoundTag();
            legacyBalls.putString("0", "cobblemon:poke_ball");
            legacyFormat.put("MachinePokeBalls", legacyBalls);
            var legacyLoaded = new HealingMachineBlockEntity(position, CobblemonBlocks.HEALING_MACHINE.defaultBlockState());
            legacyLoaded.loadWithComponents(TagValueInput.create(
                    ProblemReporter.DISCARDING, level.registryAccess(), legacyFormat));
            if (legacyLoaded.pokeBalls().get(0) != pokeBall || legacyLoaded.getHealTimeLeft() != 13
                    || Math.abs(legacyLoaded.getHealingCharge() - 2.5F) > 0.001F || !legacyLoaded.getInfinite()) {
                throw new AssertionError("Healing machine did not reload legacy map-based saved data");
            }
            return null;
        });
    }
}

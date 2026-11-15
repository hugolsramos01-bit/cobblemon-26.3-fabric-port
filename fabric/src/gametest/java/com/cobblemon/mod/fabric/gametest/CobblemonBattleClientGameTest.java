package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.client.battle.animations.MoveTileOffscreenAnimation;
import com.cobblemon.mod.common.client.battle.animations.MoveTileOnscreenAnimation;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.InputConstants;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.joml.Matrix4f;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.UUID;

/** Exercises battle rendering, screen input, packets and the real Showdown engine. */
public final class CobblemonBattleClientGameTest {
    public static void verify(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        verifyRenderBoundaries(context);
        verifyTrainerDoubles(context, singleplayer);
        verifyKnockout(context, singleplayer, false);
        verifyKnockout(context, singleplayer, true);
        verifyBattleCapture(context, singleplayer);
        // Fleeing moves the player out of the fixture area, so exercise it last.
        verifyWildBattle(context, singleplayer);
    }

    private static void verifyRenderBoundaries(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var geometry = new com.cobblemon.mod.common.client.render.block.FrozenModelGeometry(java.util.List.of());
            for (var clip : java.util.List.of(
                    new net.minecraft.client.gui.navigation.ScreenRectangle(0, 0, 0, 0),
                    new net.minecraft.client.gui.navigation.ScreenRectangle(100, 100, 10, 10))) {
                var state = new com.cobblemon.mod.common.client.gui.PosableGuiRenderState(
                        net.minecraft.resources.Identifier.parse("cobblemon:textures/gui/white.png"),
                        geometry, java.util.List.of(), java.util.List.of(), 0, 0, 32, 32, clip, new Matrix4f());
                if (state.bounds() != null) {
                    throw new AssertionError("A completely clipped Pokémon PIP escaped native GUI culling");
                }
            }
            var extractor = new net.minecraft.client.gui.GuiGraphicsExtractor(client,
                    new net.minecraft.client.renderer.state.gui.GuiRenderState(), 0, 0);
            extractor.enableScissor(extractor.guiWidth() + 10, 10, extractor.guiWidth() + 50, 50);
            var outside = com.cobblemon.mod.common.client.gui.GuiGraphicsExtractorExtensionsKt.getScissorArea(extractor);
            extractor.disableScissor();
            if (outside.width() != 0 || outside.height() != 0) {
                throw new AssertionError("The PIP clip was not intersected with the visible viewport");
            }
            var offscreenPortrait = com.cobblemon.mod.common.client.gui.GuiGraphicsExtractorExtensionsKt
                    .getVisibleScissorArea(extractor,
                            new net.minecraft.client.gui.navigation.ScreenRectangle(-100, 20, 48, 48));
            if (offscreenPortrait != null) {
                throw new AssertionError("A fully offscreen battle portrait must not enqueue a zero-sized scissor");
            }
            var partialPortrait = com.cobblemon.mod.common.client.gui.GuiGraphicsExtractorExtensionsKt
                    .getVisibleScissorArea(extractor,
                            new net.minecraft.client.gui.navigation.ScreenRectangle(-10, 20, 48, 48));
            if (partialPortrait == null || partialPortrait.width() <= 0 || partialPortrait.height() <= 0) {
                throw new AssertionError("A partially visible battle portrait should retain a positive clip");
            }
            extractor.enableScissor(0, 0, 10, 10);
            extractor.enableScissor(20, 20, 30, 30);
            var nested = com.cobblemon.mod.common.client.gui.GuiGraphicsExtractorExtensionsKt.getScissorArea(extractor);
            extractor.disableScissor();
            extractor.disableScissor();
            if (nested.width() != 0 || nested.height() != 0) {
                throw new AssertionError("Disjoint nested clips were incorrectly widened");
            }
            return null;
        });
        System.out.println("[Cobblemon GameTest] Empty, disjoint and offscreen Pokémon clips were culled");
    }

    private static void verifyTrainerDoubles(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        UUID[] opponentIds = singleplayer.getServer().computeOnServer(server -> {
            var level = singleplayer.getConnection().getServerLevel();
            var player = singleplayer.getConnection().getServerPlayer();
            UUID[] ids = new UUID[2];
            for (int index = 0; index < 2; index++) {
                var entity = PokemonProperties.Companion.parse("magikarp level=100").createEntity(level);
                entity.getPokemon().getMoveSet().clear();
                entity.getPokemon().getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("splash").create());
                entity.setPos(player.position().add(5.0, 0.0, index * 3.0));
                if (!level.addFreshEntity(entity)) throw new AssertionError("Could not spawn doubles target");
                ids[index] = entity.getUUID();
            }
            return ids;
        });
        singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);
        context.waitFor(client -> java.util.Arrays.stream(opponentIds)
                .allMatch(id -> client.level.getEntity(id) instanceof PokemonEntity), 100);
        UUID battleId = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            var playerTeam = new java.util.ArrayList<com.cobblemon.mod.common.battles.pokemon.BattlePokemon>();
            var opponentTeam = new java.util.ArrayList<com.cobblemon.mod.common.battles.pokemon.BattlePokemon>();
            for (int index = 0; index < 2; index++) {
                var pokemon = party.get(index);
                pokemon.heal();
                pokemon.getMoveSet().clear();
                pokemon.getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("quickattack").create());
                playerTeam.add(com.cobblemon.mod.common.battles.pokemon.BattlePokemon.Companion.playerOwned(pokemon));
                var opponent = ((PokemonEntity) singleplayer.getConnection().getServerLevel().getEntity(opponentIds[index])).getPokemon();
                opponentTeam.add(new com.cobblemon.mod.common.battles.pokemon.BattlePokemon(
                        opponent, opponent, new java.util.ArrayList<>(), new java.util.ArrayList<>()));
            }
            var result = BattleRegistry.startBattle(
                    com.cobblemon.mod.common.battles.BattleFormat.Companion.getGEN_9_DOUBLES(),
                    new com.cobblemon.mod.common.battles.BattleSide(
                            new com.cobblemon.mod.common.battles.actor.PlayerBattleActor(player.getUUID(), playerTeam)),
                    new com.cobblemon.mod.common.battles.BattleSide(
                            new com.cobblemon.mod.common.battles.actor.TrainerBattleActor("GameTest Trainer", UUID.randomUUID(),
                                    opponentTeam, new com.cobblemon.mod.common.battles.ai.RandomBattleAI())), true);
            if (!(result instanceof SuccessfulBattleStart success)) throw new AssertionError("Could not start doubles");
            return success.getBattle().getBattleId();
        });
        try {
            waitForRoot(context);
            chooseDoublesMove(context);
            waitForRoot(context);
            // The general back button extends left of its nominal parent widget.
            context.computeOnClient(client -> {
                var gui = (BattleGUI) client.gui.screen();
                var selection = (BattleGeneralActionSelection) gui.getCurrentActionSelection();
                var back = selection.getBackButton();
                gui.mouseClicked(new MouseButtonEvent(back.getX() + 1, back.getY() + 3,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                if (CobblemonClient.INSTANCE.getBattle().getPendingActionRequests().get(0).getResponse() != null) {
                    throw new AssertionError("Doubles back button did not cancel the first choice");
                }
                return null;
            });
            waitForRoot(context);
            chooseDoublesMove(context);
            waitForRoot(context);
            var previousRequest = context.computeOnClient(client -> CobblemonClient.INSTANCE.getBattle().getFirstUnansweredRequest());
            chooseDoublesMove(context);
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() != null
                    && CobblemonClient.INSTANCE.getBattle().getMustChoose()
                    && CobblemonClient.INSTANCE.getBattle().getFirstUnansweredRequest() != previousRequest
                    && client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleGeneralActionSelection, 400);
            context.takeScreenshot("cobblemon-26.3-battle-doubles-turn");
            clickBattleOption(context, BattleGUI.Companion.getForfeitResource());
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof com.cobblemon.mod.common.client.gui.battle.subscreen.ForfeitConfirmationSelection, 100);
            clickForfeitResponse(context, false);
            waitForRoot(context);
            clickBattleOption(context, BattleGUI.Companion.getForfeitResource());
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof com.cobblemon.mod.common.client.gui.battle.subscreen.ForfeitConfirmationSelection, 100);
            context.takeScreenshot("cobblemon-26.3-battle-forfeit");
            clickForfeitResponse(context, true);
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() == null, 400);
            System.out.println("[Cobblemon GameTest] Doubles target clicks, cancel/reselect, full turn and decline/accept forfeit completed");
        } finally {
            singleplayer.getServer().computeOnServer(server -> {
                var battle = BattleRegistry.getBattle(battleId);
                if (battle != null) battle.stop();
                for (var id : opponentIds) {
                    var entity = singleplayer.getConnection().getServerLevel().getEntity(id);
                    if (entity != null) entity.discard();
                }
                return null;
            });
        }
    }

    private static void waitForRoot(ClientGameTestContext context) {
        context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                && gui.getCurrentActionSelection() instanceof BattleGeneralActionSelection, 400);
    }

    private static void chooseDoublesMove(ClientGameTestContext context) {
        clickBattleOption(context, BattleGUI.Companion.getFightResource());
        context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                && gui.getCurrentActionSelection() instanceof BattleMoveSelection, 100);
        context.computeOnClient(client -> {
            var gui = (BattleGUI) client.gui.screen();
            var moves = (BattleMoveSelection) gui.getCurrentActionSelection();
            var tile = moves.getMoveTiles().get(0);
            // After a doubles knockout, the field can contain an empty active slot.
            var emptySlot = java.util.stream.StreamSupport.stream(CobblemonClient.INSTANCE.getBattle()
                    .getSide2().getActiveClientBattlePokemon().spliterator(), false).findFirst().orElseThrow();
            var previousPokemon = emptySlot.getBattlePokemon();
            try {
                emptySlot.setBattlePokemon(null);
                var targets = new com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection(
                        gui, moves.getRequest(), tile.getMove(), null, null);
                if (targets.getTargetTiles().stream().anyMatch(t -> t.getTarget() == emptySlot && t.getSelectable())) {
                    throw new AssertionError("An empty doubles slot remained selectable");
                }
            } finally {
                emptySlot.setBattlePokemon(previousPokemon);
            }
            gui.mouseClicked(new MouseButtonEvent(tile.getX() + 10, tile.getY() + 8,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            return null;
        });
        context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                && gui.getCurrentActionSelection() instanceof com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection, 100);
        context.takeScreenshot("cobblemon-26.3-battle-targets");
        context.computeOnClient(client -> {
            var gui = (BattleGUI) client.gui.screen();
            var selection = (com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection) gui.getCurrentActionSelection();
            var tile = selection.getTargetTiles().stream()
                    .filter(t -> t.getSelectable() && !t.getTarget().isAllied(selection.getRequest().getActivePokemon()))
                    .findFirst().orElseThrow();
            gui.mouseClicked(new MouseButtonEvent(tile.getX() + 10, tile.getY() + 8,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            if (selection.getRequest().getResponse() == null) throw new AssertionError("Target click was lost");
            return null;
        });
    }

    private static void clickForfeitResponse(ClientGameTestContext context, boolean accept) {
        context.computeOnClient(client -> {
            var gui = (BattleGUI) client.gui.screen();
            var selection = (com.cobblemon.mod.common.client.gui.battle.subscreen.ForfeitConfirmationSelection) gui.getCurrentActionSelection();
            var button = accept ? selection.getAcceptButton() : selection.getDeclineButton();
            gui.mouseClicked(new MouseButtonEvent(button.getX() + 8, button.getY() + 8,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            return null;
        });
    }

    private static void verifyWildBattle(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        UUID wildId = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var level = singleplayer.getConnection().getServerLevel();
            var leader = Cobblemon.INSTANCE.getStorage().getParty(player).get(0);
            leader.setLevel(50);
            leader.getMoveSet().clear();
            String[] moves = { "growl", "tailwhip", "thunderwave", "quickattack" };
            for (int i = 0; i < moves.length; i++) {
                leader.getMoveSet().setMove(i, Moves.INSTANCE.getByNameOrDummy(moves[i]).create());
            }
            leader.heal();
            var wild = PokemonProperties.Companion.parse("magikarp level=100").createEntity(level);
            wild.getPokemon().getMoveSet().clear();
            wild.getPokemon().getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("splash").create());
            wild.setPos(player.position().add(4.0, 0.0, 0.0));
            if (!level.addFreshEntity(wild)) throw new AssertionError("Could not spawn the battle target");
            return wild.getUUID();
        });
        singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);
        context.waitFor(client -> client.level.getEntity(wildId) instanceof PokemonEntity, 100);
        UUID battleId = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var wild = (PokemonEntity) singleplayer.getConnection().getServerLevel().getEntity(wildId);
            if (wild.getPokemon().getEntity() != wild) {
                throw new AssertionError("Battle target entity state did not finish initialising");
            }
            var result = BattleBuilder.INSTANCE.pve(player, wild);
            if (!(result instanceof SuccessfulBattleStart success)) {
                throw new AssertionError("Could not start a real client wild battle: " + result);
            }
            return success.getBattle().getBattleId();
        });
        try {
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() != null
                    && client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleGeneralActionSelection, 400);
            context.takeScreenshot("cobblemon-26.3-battle-root");
            System.out.println("[Cobblemon GameTest] Real wild battle reached an actionable client GUI");
            verifyLogToggle(context);
            verifyBattleResize(context);
            context.computeOnClient(client -> {
                for (var side : CobblemonClient.INSTANCE.getBattle().getSides()) {
                    for (var active : side.getActiveClientBattlePokemon()) {
                        active.getAnimations().clear();
                        active.getAnimations().add(new MoveTileOffscreenAnimation(0.01F, null));
                    }
                }
                return null;
            });
            long hiddenUntil = System.nanoTime() + 500_000_000L;
            context.waitFor(client -> System.nanoTime() >= hiddenUntil, 100);
            context.takeScreenshot("cobblemon-26.3-battle-offscreen");
            context.computeOnClient(client -> {
                for (var side : CobblemonClient.INSTANCE.getBattle().getSides()) {
                    for (var active : side.getActiveClientBattlePokemon()) {
                        active.getAnimations().clear();
                        active.getAnimations().add(new MoveTileOnscreenAnimation(null, 0.01F));
                    }
                }
                return null;
            });
            clickBattleOption(context, BattleGUI.Companion.getBagResource());
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle().getMinimised()
                    && client.gui.screen() == null, 100);
            context.computeOnClient(client -> {
                var battle = CobblemonClient.INSTANCE.getBattle();
                if (!battle.getMustChoose() || battle.getFirstUnansweredRequest() == null) {
                    throw new AssertionError("Catch incorrectly consumed the pending battle turn");
                }
                battle.setMinimised(false);
                client.gui.setScreen(new BattleGUI());
                return null;
            });
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleGeneralActionSelection, 100);
            clickBattleOption(context, BattleGUI.Companion.getSwitchResource());
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleSwitchPokemonSelection, 100);
            context.takeScreenshot("cobblemon-26.3-battle-switch");
            context.computeOnClient(client -> {
                var gui = (BattleGUI) client.gui.screen();
                var selection = (BattleSwitchPokemonSelection) gui.getCurrentActionSelection();
                var back = selection.getBackButton();
                gui.mouseClicked(new MouseButtonEvent(back.getX() + 3, back.getY() + 3,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                return null;
            });
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleGeneralActionSelection, 100);
            clickBattleOption(context, BattleGUI.Companion.getFightResource());
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleMoveSelection, 100);
            context.takeScreenshot("cobblemon-26.3-battle-moves");
            for (int index = 0; index < 4; index++) {
                final int moveIndex = index;
                var request = context.computeOnClient(client -> {
                    var gui = (BattleGUI) client.gui.screen();
                    var selection = (BattleMoveSelection) gui.getCurrentActionSelection();
                    var tile = selection.getMoveTiles().get(moveIndex);
                    var pending = selection.getRequest();
                    gui.mouseClicked(new MouseButtonEvent(tile.getX() + 12, tile.getY() + 8,
                            new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    if (!(pending.getResponse() instanceof com.cobblemon.mod.common.battles.MoveActionResponse response)
                            || !response.getMoveName().equals(tile.getMove().getId())) {
                        throw new AssertionError("Click did not choose move slot " + moveIndex);
                    }
                    return pending;
                });
                context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() != null
                        && CobblemonClient.INSTANCE.getBattle().getMustChoose()
                        && CobblemonClient.INSTANCE.getBattle().getFirstUnansweredRequest() != request
                        && client.gui.screen() instanceof BattleGUI gui
                        && gui.getCurrentActionSelection() instanceof BattleGeneralActionSelection, 400);
                System.out.println("[Cobblemon GameTest] Server completed a real turn for move slot " + moveIndex);
                if (index < 3) {
                    clickBattleOption(context, BattleGUI.Companion.getFightResource());
                    context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                            && gui.getCurrentActionSelection() instanceof BattleMoveSelection, 100);
                }
            }
            clickBattleOption(context, BattleGUI.Companion.getSwitchResource());
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleSwitchPokemonSelection, 100);
            UUID switchedTo = context.computeOnClient(client -> {
                var gui = (BattleGUI) client.gui.screen();
                var selection = (BattleSwitchPokemonSelection) gui.getCurrentActionSelection();
                var tile = selection.getTiles().stream().filter(t -> !t.isFainted() && !t.isCurrentlyInBattle())
                        .findFirst().orElseThrow();
                gui.mouseClicked(new MouseButtonEvent(tile.getX() + 12, tile.getY() + 8,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                return tile.getPokemon().getUuid();
            });
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() != null
                    && java.util.stream.StreamSupport.stream(CobblemonClient.INSTANCE.getBattle()
                            .getSide1().getActiveClientBattlePokemon().spliterator(), false)
                        .anyMatch(active -> active.getBattlePokemon() != null
                                && active.getBattlePokemon().getUuid().equals(switchedTo))
                    && client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleGeneralActionSelection, 400);
            context.takeScreenshot("cobblemon-26.3-battle-switched");
            clickBattleOption(context, BattleGUI.Companion.getRunResource());
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() == null
                    || (CobblemonClient.INSTANCE.getBattle().getMustChoose()
                        && CobblemonClient.INSTANCE.getBattle().getFirstUnansweredRequest() != null), 400);
            singleplayer.getServer().computeOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                player.teleportTo(player.getX() + 128, player.getY(), player.getZ());
                return null;
            });
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() == null, 100);
            System.out.println("[Cobblemon GameTest] Catch minimised, all four move slots advanced turns, Switch changed the active Pokémon, Run ended after leaving range");
        } finally {
            singleplayer.getServer().computeOnServer(server -> {
                var battle = BattleRegistry.getBattle(battleId);
                if (battle != null) battle.end();
                return null;
            });
        }
        context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() == null, 100);
    }

    private static void verifyBattleResize(ClientGameTestContext context) {
        int previousScale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            for (int scale : new int[] { 1, 2, 3 }) {
                context.computeOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    return null;
                });
                waitForRoot(context);
                long deadline = System.nanoTime() + 250_000_000L;
                context.waitFor(client -> System.nanoTime() >= deadline, 100);
                context.takeScreenshot("cobblemon-26.3-battle-scale-" + scale);
                context.computeOnClient(client -> {
                    if (CobblemonClient.INSTANCE.getBattle().getMessages().getListeners().size() > 2) {
                        throw new AssertionError("Resizing accumulated abandoned battle log subscriptions");
                    }
                    return null;
                });
            }
        } finally {
            context.computeOnClient(client -> {
                client.options.guiScale().set(previousScale);
                client.resizeGui();
                return null;
            });
        }
        waitForRoot(context);
        System.out.println("[Cobblemon GameTest] Battle rendered at GUI scales 1, 2 and 3 without leaking log listeners");
    }

    private static void verifyLogToggle(ClientGameTestContext context) {
        for (int expectedHeight : new int[] { 92, 46 }) {
            context.computeOnClient(client -> {
                var gui = (BattleGUI) client.gui.screen();
                var pane = gui.children().stream()
                        .filter(com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane.class::isInstance)
                        .map(com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane.class::cast)
                        .findFirst().orElseThrow();
                int offset = pane.getHeight() == 46 ? 46 : 92;
                gui.mouseClicked(new MouseButtonEvent(pane.getX() + 162, pane.getAppropriateY() + offset + 2,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                return null;
            });
            context.waitFor(client -> ((BattleGUI) client.gui.screen()).children().stream()
                    .anyMatch(child -> child instanceof com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane pane
                            && pane.getHeight() == expectedHeight), 20);
        }
        System.out.println("[Cobblemon GameTest] Battle log expanded and collapsed through Screen mouse dispatch");
    }

    private static void verifyBattleCapture(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        UUID wildId = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var level = singleplayer.getConnection().getServerLevel();
            var wild = PokemonProperties.Companion.parse("magikarp level=5").createEntity(level);
            wild.getPokemon().getMoveSet().clear();
            wild.getPokemon().getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy("splash").create());
            wild.setPos(player.position().add(4.0, 0.0, 0.0));
            if (!level.addFreshEntity(wild)) throw new AssertionError("Could not spawn capture target");
            return wild.getUUID();
        });
        singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);
        context.waitFor(client -> client.level.getEntity(wildId) instanceof PokemonEntity, 100);
        UUID[] captureIds = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var wild = (PokemonEntity) singleplayer.getConnection().getServerLevel().getEntity(wildId);
            var result = BattleBuilder.INSTANCE.pve(player, wild);
            if (!(result instanceof SuccessfulBattleStart success)) throw new AssertionError("Could not start capture battle");
            return new UUID[] { success.getBattle().getBattleId(), wild.getPokemon().getUuid() };
        });
        try {
            waitForRoot(context);
            clickBattleOption(context, BattleGUI.Companion.getBagResource());
            context.waitFor(client -> client.gui.screen() == null, 100);
            var ball = singleplayer.getServer().computeOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                var level = singleplayer.getConnection().getServerLevel();
                var wild = (PokemonEntity) level.getEntity(wildId);
                // A real projectile collision starts capture; do not call beginCapture directly.
                var projectile = new com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity(
                        com.cobblemon.mod.common.api.pokeball.PokeBalls.getMasterBall(), level, player,
                        CobblemonEntities.EMPTY_POKEBALL);
                projectile.setPos(wild.getX() - 1.5, wild.getY() + wild.getBbHeight() * 0.5, wild.getZ());
                projectile.setDeltaMovement(0.8, 0.0, 0.0);
                if (!level.addFreshEntity(projectile)) throw new AssertionError("Could not launch capture projectile");
                return projectile;
            });
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() != null
                    && java.util.stream.StreamSupport.stream(CobblemonClient.INSTANCE.getBattle().getSide2()
                            .getActiveClientBattlePokemon().spliterator(), false).anyMatch(p -> p.getBallCapturing() != null), 100);
            context.takeScreenshot("cobblemon-26.3-battle-capture");
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() == null, 600);
            singleplayer.getServer().computeOnServer(server -> {
                int count = 0;
                for (var pokemon : Cobblemon.INSTANCE.getStorage().getParty(singleplayer.getConnection().getServerPlayer())) {
                    if (pokemon.getUuid().equals(captureIds[1])) count++;
                }
                if (!ball.getCaptureFuture().getNow(false) || count != 1 || BattleRegistry.getBattle(captureIds[0]) != null) {
                    throw new AssertionError("Battle capture did not finish, store exactly one Pokémon, and close the battle");
                }
                return null;
            });
            System.out.println("[Cobblemon GameTest] Physical Master Ball collision completed capture during battle and ended it");
        } finally {
            singleplayer.getServer().computeOnServer(server -> {
                var battle = BattleRegistry.getBattle(captureIds[0]);
                if (battle != null) battle.stop();
                var wild = singleplayer.getConnection().getServerLevel().getEntity(wildId);
                if (wild != null) wild.discard();
                return null;
            });
        }
    }

    private static void verifyKnockout(ClientGameTestContext context, TestSingleplayerContext singleplayer, boolean forceSwitch) {
        UUID wildId = singleplayer.getServer().computeOnServer(server -> {
            var level = singleplayer.getConnection().getServerLevel();
            var player = singleplayer.getConnection().getServerPlayer();
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (var pokemon : party) pokemon.heal();
            var leader = party.get(0);
            leader.setLevel(50);
            leader.getMoveSet().clear();
            leader.getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy(forceSwitch ? "growl" : "quickattack").create());
            if (forceSwitch) leader.setCurrentHealth(1);
            var wild = PokemonProperties.Companion.parse(forceSwitch ? "magikarp level=100" : "magikarp level=1").createEntity(level);
            wild.getPokemon().getMoveSet().clear();
            wild.getPokemon().getMoveSet().setMove(0, Moves.INSTANCE.getByNameOrDummy(forceSwitch ? "quickattack" : "splash").create());
            wild.setPos(player.position().add(4.0, 0.0, 0.0));
            if (!level.addFreshEntity(wild)) throw new AssertionError("Could not spawn knockout target");
            return wild.getUUID();
        });
        singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);
        context.waitFor(client -> client.level.getEntity(wildId) instanceof PokemonEntity, 100);
        var battle = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var wild = (PokemonEntity) singleplayer.getConnection().getServerLevel().getEntity(wildId);
            var result = BattleBuilder.INSTANCE.pve(player, wild);
            if (!(result instanceof SuccessfulBattleStart success)) throw new AssertionError("Could not start knockout battle");
            return success.getBattle();
        });
        int previousExperience = singleplayer.getServer().computeOnServer(server ->
                Cobblemon.INSTANCE.getStorage().getParty(singleplayer.getConnection().getServerPlayer()).get(0).getExperience());
        try {
            waitForRoot(context);
            clickBattleOption(context, BattleGUI.Companion.getFightResource());
            context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                    && gui.getCurrentActionSelection() instanceof BattleMoveSelection, 100);
            context.computeOnClient(client -> {
                var gui = (BattleGUI) client.gui.screen();
                var selection = (BattleMoveSelection) gui.getCurrentActionSelection();
                var tile = selection.getMoveTiles().get(0);
                tile.getMove().setDisabled(true);
                gui.mouseClicked(new MouseButtonEvent(tile.getX() + 10, tile.getY() + 8,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                if (selection.getRequest().getResponse() != null) throw new AssertionError("Disabled move was chosen");
                tile.getMove().setDisabled(false);
                gui.mouseClicked(new MouseButtonEvent(tile.getX() + 10, tile.getY() + 8,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                return null;
            });
            if (forceSwitch) {
                context.waitFor(client -> client.gui.screen() instanceof BattleGUI gui
                        && gui.getCurrentActionSelection() instanceof BattleSwitchPokemonSelection selection
                        && selection.getRequest().getForceSwitch(), 400);
                context.takeScreenshot("cobblemon-26.3-battle-forced-switch");
                UUID replacementId = context.computeOnClient(client -> {
                    var gui = (BattleGUI) client.gui.screen();
                    var selection = (BattleSwitchPokemonSelection) gui.getCurrentActionSelection();
                    var tile = selection.getTiles().stream().filter(t -> !t.isFainted() && !t.isCurrentlyInBattle())
                            .findFirst().orElseThrow();
                    gui.mouseClicked(new MouseButtonEvent(tile.getX() + 10, tile.getY() + 8,
                            new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    return tile.getPokemon().getUuid();
                });
                context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() != null
                        && CobblemonClient.INSTANCE.getBattle().getMustChoose()
                        && java.util.stream.StreamSupport.stream(CobblemonClient.INSTANCE.getBattle().getSide1()
                                .getActiveClientBattlePokemon().spliterator(), false).anyMatch(p -> p.getBattlePokemon() != null
                                && p.getBattlePokemon().getUuid().equals(replacementId)), 400);
                singleplayer.getServer().computeOnServer(server -> {
                    if (Cobblemon.INSTANCE.getStorage().getParty(singleplayer.getConnection().getServerPlayer()).get(0).getCurrentHealth() != 0) {
                        throw new AssertionError("The knocked-out party Pokémon did not remain fainted");
                    }
                    return null;
                });
            } else {
                context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() == null, 400);
                singleplayer.getServer().computeOnServer(server -> {
                    var player = singleplayer.getConnection().getServerPlayer();
                    if (!battle.getEnded() || battle.getFaintCounter() == 0
                            || battle.getWinners().stream().noneMatch(actor -> actor.getUuid().equals(player.getUUID()))
                            || Cobblemon.INSTANCE.getStorage().getParty(player).get(0).getExperience() <= previousExperience) {
                        throw new AssertionError("Knockout did not record victory and award experience");
                    }
                    return null;
                });
            }
            System.out.println("[Cobblemon GameTest] " + (forceSwitch ? "Fainting opened forced switch and resumed with a living Pokémon" : "Knockout ended in victory and awarded experience"));
        } finally {
            singleplayer.getServer().computeOnServer(server -> {
                if (BattleRegistry.getBattle(battle.getBattleId()) != null) battle.stop();
                for (var pokemon : Cobblemon.INSTANCE.getStorage().getParty(singleplayer.getConnection().getServerPlayer())) pokemon.heal();
                var target = singleplayer.getConnection().getServerLevel().getEntity(wildId);
                if (target != null) target.discard();
                return null;
            });
            context.waitFor(client -> CobblemonClient.INSTANCE.getBattle() == null, 100);
        }
    }

    private static void clickBattleOption(ClientGameTestContext context, net.minecraft.resources.Identifier resource) {
        context.computeOnClient(client -> {
            var gui = (BattleGUI) client.gui.screen();
            var selection = (BattleGeneralActionSelection) gui.getCurrentActionSelection();
            var tile = selection.getTiles().stream().filter(option -> option.getResource().equals(resource))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing battle option " + resource));
            if (!gui.mouseClicked(new MouseButtonEvent(tile.getX() + 8, tile.getY() + 8,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false)) {
                throw new AssertionError("Screen did not dispatch click to battle option " + resource);
            }
            return null;
        });
    }

}

package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonBlocks;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.block.PCBlock;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI;
import com.cobblemon.mod.common.client.gui.config.CobblemonConfigScreen;
import com.cobblemon.mod.common.client.gui.startselection.StarterSelectionScreen;
import com.cobblemon.mod.common.client.gui.summary.Summary;
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
import com.cobblemon.mod.common.client.pokedex.PokedexType;
import com.cobblemon.mod.common.api.pokedex.Dexes;
import com.cobblemon.mod.fabric.client.integration.modmenu.CobblemonModMenu;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.OrientationControllable;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.util.ServerPlayerShoulderExtensionsKt;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.loader.api.FabricLoader;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Creates a disposable singleplayer world, waits for its first render, and saves a review screenshot. */
@SuppressWarnings("UnstableApiUsage")
public final class CobblemonClientSmokeGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        String focus = System.getProperty("cobblemon.gametest.focus", "");
        if (!focus.isEmpty() && !focus.equals("smoke")) {
            System.out.println("[Cobblemon GameTest] SKIP smoke suite: explicit " + focus + " focus");
            return;
        }
        System.out.println("[Cobblemon GameTest] Creating disposable client world");
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            System.out.println("[Cobblemon GameTest] Waiting for initial chunks to render");
            singleplayer.getConnection().waitForChunksRender();

            boolean testWorldIsActive = context.computeOnClient(client ->
                    client.level == singleplayer.getConnection().getClientLevel());
            if (!testWorldIsActive) {
                throw new AssertionError("The disposable test world did not become the active client world");
            }

            UUID[] pokemonIds = singleplayer.getServer().computeOnServer(server -> {
                var connection = singleplayer.getConnection();
                var player = connection.getServerPlayer();
                var serverLevel = connection.getServerLevel();
                BlockPos spawnPosition = player.blockPosition().offset(2, 0, 2);
                PokemonEntity pokemon = CobblemonEntities.POKEMON.spawn(
                        serverLevel,
                        spawnPosition,
                        EntitySpawnReason.COMMAND);
                if (pokemon == null || pokemon.getPokemon().getSpecies() == null) {
                    throw new AssertionError("The test world could not spawn a Pokémon with a loaded species");
                }
                PokemonEntity eevee = PokemonProperties.Companion.parse("eevee level=12").createEntity(serverLevel);
                eevee.setPos(spawnPosition.getX() + 2.5, spawnPosition.getY(), spawnPosition.getZ() + 0.5);
                PokemonEntity pikachu = PokemonProperties.Companion.parse("pikachu level=18 shiny=true").createEntity(serverLevel);
                pikachu.setPos(spawnPosition.getX() + 4.5, spawnPosition.getY(), spawnPosition.getZ() + 0.5);
                if (!serverLevel.addFreshEntity(eevee) || !serverLevel.addFreshEntity(pikachu)) {
                    throw new AssertionError("The test world could not add the configured Pokémon entities");
                }
                return new UUID[] { pokemon.getUUID(), eevee.getUUID(), pikachu.getUUID() };
            });
            singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);

            context.waitFor(client -> client.level != null && allPokemonPresent(client, pokemonIds), 100);
            boolean clientReceivedPokemon = context.computeOnClient(client -> allPokemonPresent(client, pokemonIds));
            if (!clientReceivedPokemon) {
                throw new AssertionError("The client did not receive all configured Pokémon entities");
            }

            verifyOptionalLambDynamicLights(context, singleplayer);

            long particleSpawnTime = context.computeOnClient(client -> {
                client.level.addParticle(
                        ParticleTypes.FLAME,
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        0.0,
                        0.0,
                        0.0);
                return client.level.getGameTime();
            });
            context.waitFor(client -> client.level != null && client.level.getGameTime() > particleSpawnTime, 100);

            boolean pokemonRenderersExist = context.computeOnClient(client -> {
                for (UUID pokemonId : pokemonIds) {
                    var pokemon = client.level.getEntity(pokemonId);
                    if (!(pokemon instanceof PokemonEntity)
                            || client.getEntityRenderDispatcher().getRenderer(pokemon) == null) {
                        return false;
                    }
                }
                return true;
            });
            if (!pokemonRenderersExist) {
                throw new AssertionError("A client renderer was not registered for every Pokémon species");
            }

            CobblemonMoveSetCodecClientGameTest.verify(context);
            CobblemonFlightControlClientGameTest.verify(context);
            verifyMountedCameraMatrices(context);
            verifyPCScreenOpens(context, singleplayer);
            long healingCompletesAt = startHealingMachine(context, singleplayer);
            context.waitFor(client -> client.level != null && client.level.getGameTime() >= healingCompletesAt, 100);
            verifyHealingMachineCompleted(singleplayer);

            context.computeOnClient(client -> {
                var pokemon = client.level.getEntity(pokemonIds[0]);
                var target = pokemon.position().add(0.0, pokemon.getBbHeight() * 0.5, 0.0);
                var direction = target.subtract(client.player.getEyePosition());
                float yaw = (float) (Math.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0F;
                float pitch = (float) (-Math.atan2(direction.y, direction.horizontalDistance()) * 180.0 / Math.PI);
                client.player.setYRot(yaw);
                client.player.setXRot(pitch);
                client.player.setYHeadRot(yaw);
                client.player.setYBodyRot(yaw);
                return null;
            });

            System.out.println("[Cobblemon GameTest] Client received three Pokémon species and resolved their renderers");
            System.out.println("[Cobblemon GameTest] PC screen opened and healing machine completed a real player heal");
            System.out.println("[Cobblemon GameTest] Capturing world frame with Pokémon loaded");
            context.takeScreenshot("cobblemon-26.3-world-smoke");

            context.computeOnClient(client -> {
                var categories = Cobblemon.INSTANCE.getStarterConfig().getStarters().stream()
                        .map(category -> category.asRenderableStarterCategory())
                        .toList();
                client.gui.setScreen(new StarterSelectionScreen(categories));
                return null;
            });
            long renderDeadline = System.nanoTime() + 1_000_000_000L;
            context.waitFor(client -> client.gui.screen() instanceof StarterSelectionScreen
                    && System.nanoTime() >= renderDeadline, 100);
            System.out.println("[Cobblemon GameTest] Capturing starter selection GUI with deferred Pokémon models");
            context.takeScreenshot("cobblemon-26.3-starter-selection");
            context.computeOnClient(client -> {
                client.gui.setScreen(null);
                return null;
            });

            UUID[] summaryPokemonIds = singleplayer.getServer().computeOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                String[] properties = { "charmander level=14", "eevee level=25", "nymble level=12" };
                UUID[] ids = new UUID[properties.length];
                for (int i = 0; i < properties.length; i++) {
                    Pokemon summaryPokemon = PokemonProperties.Companion.parse(properties[i]).create(player);
                    if (!party.add(summaryPokemon)) {
                        throw new AssertionError("Could not add a summary-screen Pokémon to the disposable player party");
                    }
                    ids[i] = summaryPokemon.getUuid();
                }
                return ids;
            });
            context.waitFor(client -> client.level != null && java.util.Arrays.stream(summaryPokemonIds)
                    .allMatch(id -> CobblemonClient.INSTANCE.getStorage().getParty().getSlots().stream()
                            .anyMatch(pokemon -> pokemon != null && pokemon.getUuid().equals(id))), 100);
            context.computeOnClient(client -> {
                var party = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
                Summary.Companion.open(party, false, 0);
                return null;
            });
            context.waitFor(client -> client.gui.screen() instanceof Summary, 100);
            System.out.println("[Cobblemon GameTest] Capturing party Pokémon models in the Summary screen");
            context.takeScreenshot("cobblemon-26.3-summary-party");
            CobblemonSummaryClientGameTest.verify(context, singleplayer);
            context.computeOnClient(client -> {
                client.gui.setScreen(null);
                return null;
            });
            verifySneakDismount(context, singleplayer);
            CobblemonToastClientGameTest.verify(context);
            verifyPokedexScreen(context);
            verifyOptionalClientIntegrations(context);
            verifyMountedFrontRender(context, singleplayer);
            context.waitFor(client -> client.player.onGround() && !client.player.isPassenger(), 100);
            verifyShoulderRender(context, singleplayer);
            CobblemonBattleClientGameTest.verify(context, singleplayer);
        }
    }

    private static void verifyOptionalLambDynamicLights(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        if (!FabricLoader.getInstance().isModLoaded("lambdynlights")) {
            System.out.println("[Cobblemon GameTest] LambDynamicLights absent; optional compatibility leaves startup clean");
            return;
        }

        var lightFixture = singleplayer.getServer().computeOnServer(server -> {
            var level = singleplayer.getConnection().getServerLevel();
            var player = singleplayer.getConnection().getServerPlayer();
            var chandelure = PokemonProperties.Companion.parse("chandelure level=35").createEntity(level);
            chandelure.setPos(player.getX() + 5, player.getY(), player.getZ() + 5);
            if (!level.addFreshEntity(chandelure)) {
                throw new AssertionError("Could not spawn the Chandelure dynamic-light fixture");
            }
            return new Object[] { chandelure.getUUID(), chandelure.blockPosition() };
        });
        UUID chandelureId = (UUID) lightFixture[0];
        singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);
        context.waitFor(client -> client.level != null && findEntityId(client, chandelureId) >= 0, 100);
        int wildPokemonLight = context.computeOnClient(client -> lambEntityLight(client, findEntityId(client, chandelureId)));
        if (wildPokemonLight < 13) {
            throw new AssertionError("LambDynamicLights did not apply Chandelure's form light in the world: " + wildPokemonLight);
        }

        UUID litwickId = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var level = singleplayer.getConnection().getServerLevel();
            var litwickPokemon = PokemonProperties.Companion.parse("litwick level=8").create(player);
            if (!Cobblemon.INSTANCE.getStorage().getParty(player).add(litwickPokemon)) {
                throw new AssertionError("Could not add the shoulder-light Litwick fixture to the player party");
            }
            var litwick = new PokemonEntity(level, litwickPokemon, CobblemonEntities.POKEMON);
            litwick.setPos(player.getX() + 1, player.getY(), player.getZ());
            if (!level.addFreshEntity(litwick) || !litwick.tryMountingShoulder(player)) {
                throw new AssertionError("Could not mount the Litwick dynamic-light fixture on the shoulder");
            }
            return litwickPokemon.getUuid();
        });
        context.waitFor(client -> client.player != null && lambEntityLight(client, client.player.getId()) >= 11, 100);
        int shoulderLight = context.computeOnClient(client -> lambEntityLight(client, client.player.getId()));
        if (shoulderLight < 11) {
            throw new AssertionError("LambDynamicLights did not transfer the shoulder Pokémon light to the player: " + shoulderLight);
        }

        singleplayer.getServer().computeOnServer(server -> {
            var level = singleplayer.getConnection().getServerLevel();
            var chandelure = level.getEntity(chandelureId);
            if (chandelure == null) throw new AssertionError("Chandelure fixture disappeared before recall");
            chandelure.discard();
            var player = singleplayer.getConnection().getServerPlayer();
            ((com.cobblemon.mod.common.duck.PlayerDuck) player)
                    .cobblemon$setShoulderPokemon(true, new net.minecraft.nbt.CompoundTag());
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            com.cobblemon.mod.common.pokemon.Pokemon litwick = null;
            for (int slot = 0; slot < party.size(); slot++) {
                var pokemon = party.get(slot);
                if (pokemon != null && pokemon.getUuid().equals(litwickId)) litwick = pokemon;
            }
            if (litwick == null) throw new AssertionError("Shoulder Litwick disappeared from the party");
            party.remove(litwick);
            return null;
        });
        context.waitFor(client -> client.level != null && findEntityId(client, chandelureId) < 0
                && lambEntityLight(client, client.player.getId()) == 0, 100);
        System.out.println("[Cobblemon GameTest] LambDynamicLights returned level " + wildPokemonLight
                + " for Chandelure and " + shoulderLight + " for shoulder Litwick; recall cleared both sources");
    }

    private static int lambEntityLight(net.minecraft.client.Minecraft client, int entityId) {
        try {
            var api = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
            var instance = api.getMethod("get").invoke(null);
            var manager = api.getMethod("entityLightSourceManager").invoke(instance);
            var managerApi = Class.forName("dev.lambdaurora.lambdynlights.api.entity.EntityLightSourceManager");
            return (int) managerApi.getMethod("getLuminance", net.minecraft.world.entity.Entity.class)
                    .invoke(manager, entityId == client.player.getId() ? client.player : client.level.getEntity(entityId));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not query the active LambDynamicLights entity source manager", exception);
        }
    }

    private static int findEntityId(net.minecraft.client.Minecraft client, UUID uuid) {
        var entity = client.level.getEntity(uuid);
        return entity == null ? -1 : entity.getId();
    }

    private static void verifyPokedexScreen(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            if (Dexes.INSTANCE.getDexEntryMap().isEmpty()) {
                throw new AssertionError("The client did not receive any Pokédex definitions");
            }
            PokedexGUI.Companion.open(
                    CobblemonClient.INSTANCE.getClientPokedexData(),
                    PokedexType.RED,
                    null,
                    null);
            if (!(client.gui.screen() instanceof PokedexGUI)) {
                throw new AssertionError("Opening the Pokédex did not show its GUI");
            }
            return Dexes.INSTANCE.getDexEntryMap().size();
        });
        context.waitFor(client -> client.gui.screen() instanceof PokedexGUI, 100);
        System.out.println("[Cobblemon GameTest] Client received Pokédex definitions and opened the Pokédex GUI");
        context.computeOnClient(client -> {
            client.gui.setScreen(null);
            return null;
        });
    }

    private static void verifyOptionalClientIntegrations(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var loader = FabricLoader.getInstance();
            if (!loader.isModLoaded("jei") || !loader.isModLoaded("modmenu")) {
                throw new AssertionError("JEI and Mod Menu must be available in the default 26.3 client runtime");
            }
            var cobblemonEntrypoint = loader.getEntrypoints("modmenu", ModMenuApi.class).stream()
                    .filter(CobblemonModMenu.class::isInstance)
                    .map(CobblemonModMenu.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Fabric did not register Cobblemon's Mod Menu entrypoint"));
            var configScreen = cobblemonEntrypoint.getModConfigScreenFactory().create(client.gui.screen());
            if (!(configScreen instanceof CobblemonConfigScreen)) {
                throw new AssertionError("Cobblemon's Mod Menu entrypoint did not create its configuration screen");
            }
            client.gui.setScreen(configScreen);
            return null;
        });
        context.waitFor(client -> client.gui.screen() instanceof CobblemonConfigScreen, 100);
        System.out.println("[Cobblemon GameTest] JEI and Mod Menu loaded; Cobblemon config screen opened through the Mod Menu entrypoint");
        context.computeOnClient(client -> {
            client.gui.setScreen(null);
            return null;
        });
    }

    private static void verifySneakDismount(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (var style : new RidingStyle[] { RidingStyle.LAND, RidingStyle.AIR }) {
            UUID mountId = singleplayer.getServer().computeOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                var level = singleplayer.getConnection().getServerLevel();
                // Charizard has both land and air behaviours in the bundled species data.
                var mount = PokemonProperties.Companion.parse("charizard level=40").createEntity(level);
                mount.setPos(player.position().add(3.0, 0.0, 0.0));
                if (!level.addFreshEntity(mount)) {
                    throw new AssertionError("Could not spawn the dismount test Pokémon");
                }
                return mount.getUUID();
            });
            singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);
            context.waitFor(client -> client.level.getEntity(mountId) instanceof PokemonEntity, 100);
            singleplayer.getServer().computeOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                var level = singleplayer.getConnection().getServerLevel();
                var mount = (PokemonEntity) level.getEntity(mountId);
                mount.refreshRiding();
                var controller = mount.getRidingController();
                if (controller == null || controller.getBehaviours().get(style) == null) {
                    throw new AssertionError("Missing test mount behaviour for " + style);
                }
                controller.changeBehaviour(controller.getBehaviours().get(style).getKey());
                mount.setOnGround(style == RidingStyle.LAND);
                try {
                    if (!player.startRiding(mount, true, true)) {
                        throw new AssertionError("Could not mount the sneak dismount test Pokémon");
                    }
                    player.setShiftKeyDown(true);
                    player.rideTick();
                    if (player.isPassenger()) {
                        throw new AssertionError("Sneak did not dismount the player in " + style);
                    }
                    if (style == RidingStyle.AIR && !mount.isFlying()) {
                        throw new AssertionError("The air mount did not remain flying after sneak dismount");
                    }
                } finally {
                    player.setShiftKeyDown(false);
                    player.stopRiding();
                    mount.discard();
                }
                return null;
            });
        }
        System.out.println("[Cobblemon GameTest] Sneak dismounted ground and air Pokémon through the real player ride tick");
    }

    private static void verifyShoulderRender(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var level = singleplayer.getConnection().getServerLevel();
            var eevee = PokemonProperties.Companion.parse("eevee level=25").create(player);
            if (!Cobblemon.INSTANCE.getStorage().getParty(player).add(eevee)) {
                throw new AssertionError("Could not add the shoulder-test Eevee to the party");
            }
            var entity = new PokemonEntity(level, eevee, CobblemonEntities.POKEMON);
            entity.setPos(player.position().add(1.0, 0.0, 0.0));
            if (!level.addFreshEntity(entity) || !entity.tryMountingShoulder(player)) {
                throw new AssertionError("The real Eevee shoulder-mount interaction did not start");
            }
            return null;
        });
        context.waitFor(client -> !ServerPlayerShoulderExtensionsKt.shoulderEntityData(client.player, true).isEmpty(), 100);
        var previousCameraType = context.computeOnClient(client -> {
            var previous = client.options.getCameraType();
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            client.player.setYRot(0F);
            client.player.setXRot(0F);
            client.player.setYHeadRot(0F);
            client.player.setYBodyRot(0F);
            return previous;
        });
        try {
            long renderDeadline = System.nanoTime() + 500_000_000L;
            context.waitFor(client -> System.nanoTime() >= renderDeadline, 100);
            System.out.println("[Cobblemon GameTest] Capturing Eevee attached to the player's shoulder");
            context.takeScreenshot("cobblemon-26.3-shoulder-eevee");
        } finally {
            context.computeOnClient(client -> {
                client.options.setCameraType(previousCameraType);
                return null;
            });
        }
    }

    private static void verifyMountedFrontRender(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        var previousPosition = singleplayer.getServer().computeOnServer(server ->
                singleplayer.getConnection().getServerPlayer().position());
        var previousPerspective = context.computeOnClient(client -> client.options.getCameraType());
        UUID mountId = singleplayer.getServer().computeOnServer(server -> {
            var level = singleplayer.getConnection().getServerLevel();
            var mount = PokemonProperties.Companion.parse("braviary level=57").createEntity(level);
            mount.setPos(previousPosition.add(0, 6, 0));
            mount.setNoGravity(true);
            if (!level.addFreshEntity(mount)) throw new AssertionError("Could not spawn front-view mount");
            return mount.getUUID();
        });
        singleplayer.getConnection().waitForClientboundEntityUpdates(CobblemonEntities.POKEMON);
        context.waitFor(client -> client.level.getEntity(mountId) instanceof PokemonEntity, 100);
        try {
            singleplayer.getServer().computeOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                var mount = (PokemonEntity) singleplayer.getConnection().getServerLevel().getEntity(mountId);
                mount.refreshRiding();
                var controller = mount.getRidingController();
                controller.changeBehaviour(controller.getBehaviours().get(RidingStyle.AIR).getKey());
                mount.setOnGround(false);
                if (!player.startRiding(mount, true, true)) throw new AssertionError("Could not mount Braviary");
                return null;
            });
            context.waitFor(client -> client.player.getVehicle() instanceof PokemonEntity mount
                    && mount.getUUID().equals(mountId), 100);
            long riderPoseDeadline = System.nanoTime() + 600_000_000L;
            context.waitFor(client -> System.nanoTime() >= riderPoseDeadline, 100);
            CobblemonMountedRiderClientGameTest.verify(context);
            for (var perspective : new CameraType[] { CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT }) {
                context.computeOnClient(client -> {
                    client.options.setCameraType(perspective);
                    return null;
                });
                long deadline = System.nanoTime() + 600_000_000L;
                context.waitFor(client -> System.nanoTime() >= deadline, 100);
                context.computeOnClient(client -> {
                    var state = (net.minecraft.client.renderer.entity.state.AvatarRenderState)
                            client.getEntityRenderDispatcher().extractEntity(client.player, 1F);
                    var model = client.getEntityRenderDispatcher().getRenderer(state).getModel();
                    model.setupAnim(state);
                    // In 26.3 skin overlays are children of their corresponding body parts.
                    // Their local pose must stay at rest; copying the parent applies motion twice.
                    for (var part : new net.minecraft.client.model.geom.ModelPart[] {
                            model.leftSleeve, model.rightSleeve, model.hat, model.jacket,
                            model.leftPants, model.rightPants }) {
                        if (!part.storePose().equals(part.getInitialPose())) {
                            throw new AssertionError("Mounted skin overlay duplicated its parent transform in " + perspective);
                        }
                    }
                    return null;
                });
                context.takeScreenshot("cobblemon-26.3-mounted-" + perspective.name().toLowerCase(java.util.Locale.ROOT));
            }
        } finally {
            singleplayer.getServer().computeOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                player.stopRiding();
                var mount = singleplayer.getConnection().getServerLevel().getEntity(mountId);
                if (mount != null) mount.discard();
                player.teleportTo(previousPosition.x, previousPosition.y, previousPosition.z);
                return null;
            });
            context.waitFor(client -> !client.player.isPassenger(), 100);
            context.computeOnClient(client -> {
                client.options.setCameraType(previousPerspective);
                return null;
            });
        }
        System.out.println("[Cobblemon GameTest] Rendered a real Braviary ride from the back and front cameras");
    }

    private static void verifyMountedCameraMatrices(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var player = client.player;
            var previousCameraType = client.options.getCameraType();
            float previousYaw = player.getYRot();
            float previousPitch = player.getXRot();
            boolean previousDisableRoll = Cobblemon.INSTANCE.getConfig().getDisableRoll();
            var mount = PokemonProperties.Companion.parse("charizard level=40").createEntity(client.level);
            try {
                if (!player.startRiding(mount, true, true)) {
                    throw new AssertionError("Could not mount the client camera test Pokémon");
                }
                var orientation = ((OrientationControllable) mount).getOrientationController();
                orientation.setActive(true);
                orientation.updateOrientation(matrix -> matrix.rotationYXZ(0.8F, 0.3F, -0.2F));
                Cobblemon.INSTANCE.getConfig().setDisableRoll(false);
                var passenger = (com.cobblemon.mod.common.duck.RidePassenger) player;
                passenger.cobblemon$setRideYRot(17F);
                passenger.cobblemon$setRideXRot(11F);
                Quaternionf riderView = null;
                float riderYaw = 0F;
                float riderPitch = 0F;

                for (var perspective : CameraType.values()) {
                    client.options.setCameraType(perspective);
                    var camera = new MountedTestCamera();
                    camera.setLevel(client.level);
                    camera.setEntity(player);
                    // Warm both caches before the mod replaces vanilla rotation.
                    var oldView = camera.getViewRotationMatrix(new Matrix4f());
                    var oldProjectionView = camera.getViewRotationProjectionMatrix(new Matrix4f());
                    camera.rotateForTest(0F, 0F);
                    if (riderView == null) {
                        riderView = new Quaternionf(camera.rotation());
                        riderYaw = player.getYRot();
                        riderPitch = player.getXRot();
                    } else {
                        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - riderYaw)) > 0.001F
                                || Math.abs(player.getXRot() - riderPitch) > 0.001F) {
                            throw new AssertionError("Changing mounted camera perspective rotated the rider in " + perspective);
                        }
                        var expectedRotation = new Quaternionf(riderView);
                        if (perspective.isMirrored()) expectedRotation.rotateY((float) Math.PI);
                        if (Math.abs(expectedRotation.dot(camera.rotation())) < 0.99999F) {
                            throw new AssertionError("Front camera did not reverse the fully composed rider view");
                        }
                    }
                    var expectedView = new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate());
                    if (expectedView.equals(oldView, 0.0001F)) {
                        throw new AssertionError("Mounted camera test did not produce a new orientation");
                    }
                    if (!camera.getViewRotationMatrix(new Matrix4f()).equals(expectedView, 0.0001F)) {
                        throw new AssertionError("Mounted camera kept its old view matrix in " + perspective);
                    }
                    var expectedProjectionView = new Matrix4f(oldProjectionView)
                            .mul(new Matrix4f(oldView).invert()).mul(expectedView);
                    if (!camera.getViewRotationProjectionMatrix(new Matrix4f()).equals(expectedProjectionView, 0.0001F)) {
                        throw new AssertionError("Mounted camera kept its old projection/view matrix in " + perspective);
                    }
                }
                return null;
            } finally {
                player.stopRiding();
                ((OrientationControllable) mount).getOrientationController().reset();
                mount.discard();
                client.options.setCameraType(previousCameraType);
                Cobblemon.INSTANCE.getConfig().setDisableRoll(previousDisableRoll);
                passengerReset(player);
                player.setYRot(previousYaw);
                player.setXRot(previousPitch);
            }
        });
        System.out.println("[Cobblemon GameTest] Mounted camera refreshed view and projection caches in all perspectives");
    }

    private static void passengerReset(net.minecraft.client.player.AbstractClientPlayer player) {
        var passenger = (com.cobblemon.mod.common.duck.RidePassenger) player;
        passenger.cobblemon$setRideYRot(0F);
        passenger.cobblemon$setRideXRot(0F);
    }

    private static final class MountedTestCamera extends Camera {
        void rotateForTest(float yaw, float pitch) {
            setRotation(yaw, pitch);
        }
    }

    private static boolean allPokemonPresent(net.minecraft.client.Minecraft client, UUID[] pokemonIds) {
        for (UUID pokemonId : pokemonIds) {
            if (!(client.level.getEntity(pokemonId) instanceof PokemonEntity)) {
                return false;
            }
        }
        return true;
    }

    private static void verifyPCScreenOpens(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().computeOnServer(server -> {
            var connection = singleplayer.getConnection();
            var level = connection.getServerLevel();
            var player = connection.getServerPlayer();
            BlockPos position = player.blockPosition().offset(2, 0, 0);
            level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(position, CobblemonBlocks.PC.defaultBlockState());
            var pcBlock = (PCBlock) CobblemonBlocks.PC;
            pcBlock.setPlacedBy(level, position, level.getBlockState(position), player, new ItemStack(CobblemonItems.PC));

            var result = level.getBlockState(position).useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            if (result != InteractionResult.SUCCESS) {
                throw new AssertionError("Using a placed PC did not complete the server-side interaction");
            }
            return null;
        });

        context.waitFor(client -> client.gui.screen() instanceof PCGUI, 100);
        boolean pcScreenOpened = context.computeOnClient(client -> client.gui.screen() instanceof PCGUI);
        if (!pcScreenOpened) {
            throw new AssertionError("The server opened a PC but its client screen was not displayed");
        }
        context.computeOnClient(client -> {
            client.gui.setScreen(null);
            return null;
        });
    }

    private static long startHealingMachine(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            var connection = singleplayer.getConnection();
            var level = connection.getServerLevel();
            var player = connection.getServerPlayer();
            Pokemon injuredPokemon = PokemonProperties.Companion.parse("pikachu level=5").create(player);
            injuredPokemon.setCurrentHealth(1);
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (!party.add(injuredPokemon)) {
                throw new AssertionError("Could not place the healing test Pokémon in the real player party");
            }

            BlockPos position = player.blockPosition().offset(4, 0, 0);
            level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(position, CobblemonBlocks.HEALING_MACHINE.defaultBlockState());
            var state = level.getBlockState(position);
            var blockEntity = (HealingMachineBlockEntity) level.getBlockEntity(position);
            if (blockEntity == null) {
                throw new AssertionError("Placed healing machine did not create its block entity");
            }
            blockEntity.setInfinite(true);

            var result = state.useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            if (result != InteractionResult.CONSUME || !blockEntity.isInUse() || blockEntity.getHealTimeLeft() != 24) {
                throw new AssertionError("Healing machine interaction did not start its healing cycle");
            }
            return level.getGameTime() + 30;
        });
    }

    private static void verifyHealingMachineCompleted(TestSingleplayerContext singleplayer) {
        boolean healed = singleplayer.getServer().computeOnServer(server -> {
            var player = singleplayer.getConnection().getServerPlayer();
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            Pokemon pokemon = party.get(0);
            return pokemon != null && pokemon.getCurrentHealth() == pokemon.getMaxHealth();
        });
        if (!healed) {
            throw new AssertionError("The real player party did not finish healing after the machine cycle");
        }
    }
}

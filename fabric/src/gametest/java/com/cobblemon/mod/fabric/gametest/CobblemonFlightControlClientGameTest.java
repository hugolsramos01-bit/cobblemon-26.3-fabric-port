package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.CobblemonRideSettings;
import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.BirdBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.BirdSettings;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.BirdState;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.GliderBehaviour;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.GliderSettings;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.util.MoLangExtensionsKt;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.SmoothDouble;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/** Real species/settings and native smoothers, with a deterministic mouse timeline. */
@SuppressWarnings("UnstableApiUsage")
final class CobblemonFlightControlClientGameTest {
    static void verify(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var failures = new ArrayList<String>();
            for (String species : new String[] {"charizard", "braviary"}) {
                var mount = PokemonProperties.Companion.parse(species + " level=50").createEntity(client.level);
                try {
                    mount.refreshRiding();
                    var settings = (BirdSettings) mount.getRidingController().getBehaviours().get(RidingStyle.AIR);
                    mount.getRidingController().changeBehaviour(settings.getKey());
                    var behaviour = new BirdBehaviour();
                    var global = CobblemonRideSettings.INSTANCE.getBird();
                    double speed = MoLangExtensionsKt.resolveDouble(mount.getRuntime(),
                            settings.getSpeedExpr() == null ? global.getSpeedExpr() : settings.getSpeedExpr(), Collections.emptyMap()) / 20;
                    double inputMult = MoLangExtensionsKt.resolveDouble(mount.getRuntime(),
                            settings.getMouseInputMult() == null ? global.getMouseInputMult() : settings.getMouseInputMult(), Collections.emptyMap());
                    for (double input : new double[] {-5, 5, -0.2, 0.2}) {
                        var state = new BirdState();
                        state.getNoInputTimeRoll().set(0.5, true);
                        resetOrientation(mount);
                        behaviour.rotationOnMouseXY(settings, state, mount, client.player, 0, input,
                                new SmoothDouble(), new SmoothDouble(), 1, 1.0 / 60);
                        if (state.getNoInputTimeRoll().get() != 0) failures.add(species + ": active mouse " + input + " treated as idle");
                    }
                    var hover = measure(behaviour, settings, mount, client.player, 0, 60, 0.1, false);
                    var cruise = measure(behaviour, settings, mount, client.player, speed, 60, 0.1, false);
                    double ideal = 180 * 0.1 * inputMult * 0.1;
                    if (hover.yaw < ideal * 0.5) failures.add(species + ": less than half the hover command arrived in 100 ms");
                    if (cruise.yaw < hover.yaw * 0.5) failures.add(species + ": mouse heading authority disappeared at cruise speed");
                    double min = Double.POSITIVE_INFINITY, max = 0;
                    double minBanked = Double.POSITIVE_INFINITY, maxBanked = 0;
                    double minFast = Double.POSITIVE_INFINITY, maxFast = 0;
                    double minPitch = Double.POSITIVE_INFINITY, maxPitch = 0;
                    for (int fps : new int[] {30, 60, 120, 240}) {
                        var measured = measure(behaviour, settings, mount, client.player, speed, fps, 1, false);
                        min = Math.min(min, measured.yaw);
                        max = Math.max(max, measured.yaw);
                        var banked = measure(behaviour, settings, mount, client.player, speed, fps, 1, true);
                        var opposite = measure(behaviour, settings, mount, client.player, speed, fps, 1, true, -180, 0);
                        var up = measure(behaviour, settings, mount, client.player, speed, fps, 1, true, 0, -180);
                        var down = measure(behaviour, settings, mount, client.player, speed, fps, 1, true, 0, 180);
                        var fast = measure(behaviour, settings, mount, client.player, speed, fps, 1, false, 4500, 0);
                        minBanked = Math.min(minBanked, banked.yaw);
                        maxBanked = Math.max(maxBanked, banked.yaw);
                        minFast = Math.min(minFast, fast.yaw);
                        maxFast = Math.max(maxFast, fast.yaw);
                        minPitch = Math.min(minPitch, Math.abs(up.pitch));
                        maxPitch = Math.max(maxPitch, Math.abs(up.pitch));
                        if (Math.abs(opposite.yaw - banked.yaw) > 0.01 || Math.abs(opposite.roll + banked.roll) > 0.01
                                || Math.abs(up.pitch + down.pitch) > 0.01 || Math.abs(up.pitch) < 1) {
                            failures.add(species + ": asymmetric bank/pitch response at " + fps + " FPS");
                        }
                        if (!Double.isFinite(banked.yaw) || !Double.isFinite(banked.roll) || Math.abs(banked.roll) > 70) {
                            failures.add(species + ": unstable bank at " + fps + " FPS");
                        }
                        System.out.printf(Locale.ROOT, "[Cobblemon Flight] %s fps=%d directYaw=%.4f integratedYaw=%.4f roll=%.4f%n",
                                species, fps, measured.yaw, banked.yaw, banked.roll);
                    }
                    if (max == 0 || max - min > max * 0.05) failures.add(species + ": direct mouse response differs by over 5% across frame rates");
                    if (maxBanked - minBanked > maxBanked * 0.05 || maxFast - minFast > maxFast * 0.05
                            || maxPitch - minPitch > maxPitch * 0.05) {
                        failures.add(species + ": bank/pitch/fast mouse response differs by over 5% across frame rates");
                    }
                    resetOrientation(mount);
                    var idle = new BirdState();
                    behaviour.rotationOnMouseXY(settings, idle, mount, client.player, 0, 0,
                            new SmoothDouble(), new SmoothDouble(), 1, 1.0 / 60);
                    if (Math.abs(idle.getNoInputTimeRoll().get() - 1.0 / 60) > 0.000001
                            || Math.abs(idle.getNoInputTimePitch().get() - 1.0 / 60) > 0.000001) {
                        failures.add(species + ": idle timers no longer advance with time");
                    }
                    System.out.printf(Locale.ROOT, "[Cobblemon Flight] %s hover100ms=%.4f cruise100ms=%.4f ideal100ms=%.4f%n",
                            species, hover.yaw, cruise.yaw, ideal);
                    verifyActualKeyboardTurning(client, behaviour, settings, mount, species, failures);
                } finally {
                    ((OrientationControllable) mount).getOrientationController().reset();
                    mount.discard();
                }
            }
            verifyGliderAAndDStrafing(client, failures);
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            return null;
        });
        System.out.println("[Cobblemon GameTest] Charizard/Braviary mouse and actual A/D keyboard responses are symmetric and stable at 30/60/120/240 FPS");
    }

    private static void verifyGliderAAndDStrafing(Minecraft client, ArrayList<String> failures) {
        PokemonEntity glider = PokemonProperties.Companion.parse("butterfree level=30").createEntity(client.level);
        try {
            // Butterfree's rider data is stored on its form; use the shared
            // glider defaults directly so the test exercises its real setting
            // even while the locally-created preview entity has no controller.
            var settings = new GliderSettings();
            var behaviour = new GliderBehaviour();
            double left = measureGliderStrafe(client.player, client.options.keyLeft, glider, settings, behaviour);
            double right = measureGliderStrafe(client.player, client.options.keyRight, glider, settings, behaviour);
            if (Math.abs(left) < 0.01 || Math.abs(right) < 0.01 || Math.signum(left) == Math.signum(right)) {
                failures.add("butterfree: actual configured A/D keys did not produce opposite glider strafe (left="
                        + left + ", right=" + right + ")");
            }
        } finally {
            glider.discard();
            client.player.input.tick();
            client.player.applyInput();
        }
    }

    private static double measureGliderStrafe(LocalPlayer driver, KeyMapping key, PokemonEntity glider,
                                              GliderSettings settings, GliderBehaviour behaviour) {
        key.setDown(true);
        try {
            driver.input.tick();
            driver.applyInput();
            return behaviour.velocity(settings, new RidingBehaviourState(), glider, driver, Vec3.ZERO).x;
        } finally {
            key.setDown(false);
            driver.input.tick();
            driver.applyInput();
        }
    }

    private static void verifyActualKeyboardTurning(Minecraft client, BirdBehaviour behaviour,
                                                    BirdSettings settings, PokemonEntity mount, String species,
                                                    ArrayList<String> failures) {
        var left = measureKeyboardTurn(client.player, client.options.keyLeft, behaviour, settings, mount);
        var right = measureKeyboardTurn(client.player, client.options.keyRight, behaviour, settings, mount);
        if (Math.abs(left) < 0.01 || Math.abs(right) < 0.01 || Math.signum(left) == Math.signum(right)) {
            failures.add(species + ": actual configured A/D keys did not produce opposite heading changes (left="
                    + left + ", right=" + right + ")");
        }

        // Keep a real movement key held while applying mouse input too; the two
        // controls must compose instead of one path suppressing the other.
        resetOrientation(mount);
        var state = new BirdState();
        state.getRideVelocity().set(new Vec3(0, 0, 0.6), true);
        var controller = ((OrientationControllable) mount).getOrientationController();
        KeyMapping leftKey = client.options.keyLeft;
        leftKey.setDown(true);
        try {
            client.player.input.tick();
            client.player.applyInput();
            var mouse = behaviour.rotationOnMouseXY(settings, state, mount, client.player, 0, 3,
                    new SmoothDouble(), new SmoothDouble(), 1, 1.0 / 60);
            controller.rotate((float) mouse.x, (float) mouse.y, (float) mouse.z);
            behaviour.angRollVel(settings, state, mount, client.player, 1.0 / 60);
            if (Math.abs(controller.getYaw()) < 0.01) {
                failures.add(species + ": keyboard and mouse input together produced no heading change");
            }
        } finally {
            leftKey.setDown(false);
            client.player.input.tick();
            client.player.applyInput();
        }
    }

    private static double measureKeyboardTurn(LocalPlayer driver, KeyMapping key, BirdBehaviour behaviour,
                                              BirdSettings settings, PokemonEntity mount) {
        resetOrientation(mount);
        var state = new BirdState();
        state.getRideVelocity().set(new Vec3(0, 0, 0.6), true);
        var controller = ((OrientationControllable) mount).getOrientationController();
        key.setDown(true);
        try {
            // Run Minecraft's actual configured keyboard mapper and player input
            // application; this deliberately does not write xxa directly.
            driver.input.tick();
            driver.applyInput();
            behaviour.angRollVel(settings, state, mount, driver, 1.0 / 20);
            return Mth.wrapDegrees(controller.getYaw());
        } finally {
            key.setDown(false);
            driver.input.tick();
            driver.applyInput();
        }
    }

    private static Measurement measure(BirdBehaviour behaviour, BirdSettings settings, PokemonEntity mount,
                                       LocalPlayer driver, double speed, int fps, double seconds, boolean bank) {
        return measure(behaviour, settings, mount, driver, speed, fps, seconds, bank, 180, 0);
    }

    private static Measurement measure(BirdBehaviour behaviour, BirdSettings settings, PokemonEntity mount,
                                       LocalPlayer driver, double speed, int fps, double seconds, boolean bank,
                                       double mouseXRate, double mouseYRate) {
        resetOrientation(mount);
        var controller = ((OrientationControllable) mount).getOrientationController();
        var state = new BirdState();
        state.getRideVelocity().set(new Vec3(0, 0, speed), true);
        var sx = new SmoothDouble();
        var sy = new SmoothDouble();
        double yaw = 0;
        for (int frame = 0; frame < Math.round(fps * seconds); frame++) {
            float previousYaw = controller.getYaw();
            var mouse = behaviour.rotationOnMouseXY(settings, state, mount, driver, mouseYRate / fps, mouseXRate / fps,
                    sy, sx, 1, 1.0 / fps);
            if (bank) {
                controller.rotate((float) mouse.x, (float) mouse.y, (float) mouse.z);
                behaviour.angRollVel(settings, state, mount, driver, 1.0 / fps);
            }
            yaw += Math.abs(Mth.wrapDegrees(controller.getYaw() - previousYaw));
        }
        return new Measurement(yaw, controller.getRoll(), controller.getPitch());
    }

    private static void resetOrientation(PokemonEntity mount) {
        var controller = ((OrientationControllable) mount).getOrientationController();
        controller.reset();
        controller.setActive(true);
        controller.updateOrientation(matrix -> matrix.identity());
    }

    private record Measurement(double yaw, double roll, double pitch) {}
}

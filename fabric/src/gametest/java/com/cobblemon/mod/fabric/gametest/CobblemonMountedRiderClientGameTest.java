package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Exercises the real extracted player state and native rotation hook on an occupied Braviary seat. */
@SuppressWarnings("UnstableApiUsage")
final class CobblemonMountedRiderClientGameTest {
    static void verify(ClientGameTestContext context) {
        context.computeOnClient(client -> {
            var player = client.player;
            if (!(player.getVehicle() instanceof PokemonEntity mount)) {
                throw new AssertionError("Rider pose test requires the real mounted player");
            }
            var delegate = (PokemonClientDelegate) mount.getDelegate();
            MountedPokemonAnimationRenderController.INSTANCE.reset();
            MountedPokemonAnimationRenderController.INSTANCE.setup(mount, 1F);
            var seatName = delegate.getSeatLocator(player);
            var seat = delegate.getLocatorStates().get(seatName);
            if (seat == null) throw new AssertionError("Mounted Braviary has no seat locator");
            var originalSeat = new Matrix4f(seat.getMatrix());
            var position = player.position();
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            var camera = new Quaternionf(client.gameRenderer.mainCamera().rotation());
            var controller = ((com.cobblemon.mod.common.OrientationControllable) mount).getOrientationController();
            var orientation = controller.getOrientation() == null ? null : new org.joml.Matrix3f(controller.getOrientation());
            try {
                var setupRotations = LivingEntityRenderer.class.getDeclaredMethod("setupRotations",
                        LivingEntityRenderState.class, PoseStack.class, float.class, float.class);
                setupRotations.setAccessible(true);
                // Level, opposite banks, and simultaneous pitch/yaw/roll. The locator includes animated seat tilt.
                for (float[] angles : new float[][] { {0.6F, 0.25F, 0.55F}, {-0.4F, -0.3F, -0.55F}, {0, 0, 0} }) {
                    var rotation = new Quaternionf().rotationYXZ(angles[0], angles[1], angles[2]);
                    var seatPosition = originalSeat.getTranslation(new Vector3f());
                    seat.setMatrix(new Matrix4f().translation(seatPosition).rotate(rotation));
                    var state = (net.minecraft.client.renderer.entity.state.AvatarRenderState)
                            client.getEntityRenderDispatcher().extractEntity(player, 1F);
                    var renderer = client.getEntityRenderDispatcher().getRenderer(state);
                    // Submission must consume a snapshot, even if the locator changes after extraction.
                    seat.getMatrix().identity();
                    var expected = new Matrix4f().translation(
                            seatPosition.x - (float) (player.getX() - mount.getX()),
                            seatPosition.y - (float) (player.getY() - mount.getY()),
                            seatPosition.z - (float) (player.getZ() - mount.getZ()))
                            .rotate(rotation).translate(0, -player.getBbHeight() / 2F + 0.35F, 0);
                    for (float scale : new float[] {1F, 1.5F}) {
                        var stack = new PoseStack();
                        stack.scale(scale, scale, scale);
                        setupRotations.invoke(renderer, state, stack, state.bodyRot, scale);
                        var actual = stack.last().pose();
                        var actualUp = actual.transformDirection(new Vector3f(0, 1, 0)).normalize();
                        var expectedUp = rotation.transform(new Vector3f(0, 1, 0));
                        if (actualUp.distance(expectedUp) > 0.0001F) {
                            throw new AssertionError("Rider body does not follow the seat pitch/bank: " + actualUp + " != " + expectedUp);
                        }
                        if (!actual.equals(new Matrix4f(expected).scale(scale), 0.0002F)) {
                            throw new AssertionError("Rider visual transform does not stay attached to the animated seat at scale " + scale);
                        }
                    }
                }
                // Invalid/hidden or missing seats must leave the native rider model finite.
                seat.setMatrix(new Matrix4f().zero());
                var invalidState = (LivingEntityRenderState) client.getEntityRenderDispatcher().extractEntity(player, 1F);
                if (((com.cobblemon.mod.common.duck.MountedRiderRenderState) invalidState).cobblemon$getRiderPose() != null) {
                    throw new AssertionError("A zero-scaled seat produced an invalid rider transform");
                }
                delegate.getLocatorStates().remove(seatName);
                var missingState = (LivingEntityRenderState) client.getEntityRenderDispatcher().extractEntity(player, 1F);
                if (((com.cobblemon.mod.common.duck.MountedRiderRenderState) missingState).cobblemon$getRiderPose() != null) {
                    throw new AssertionError("A missing seat kept a stale rider transform");
                }
                if (!position.equals(player.position()) || yaw != player.getYRot() || pitch != player.getXRot()
                        || Math.abs(camera.dot(client.gameRenderer.mainCamera().rotation())) < 0.99999F
                        || (orientation == null ? controller.getOrientation() != null
                            : !orientation.equals(controller.getOrientation(), 0.0001F))) {
                    throw new AssertionError("Visual rider alignment changed flight orientation, camera, or player position");
                }
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Could not exercise the native mounted rider rotation hook", exception);
            } finally {
                delegate.getLocatorStates().put(seatName, seat);
                seat.setMatrix(originalSeat);
                MountedPokemonAnimationRenderController.INSTANCE.reset();
            }
            return null;
        });
        System.out.println("[Cobblemon GameTest] Rider follows level, pitched, and opposite banked seats without changing flight/camera state");
    }
}

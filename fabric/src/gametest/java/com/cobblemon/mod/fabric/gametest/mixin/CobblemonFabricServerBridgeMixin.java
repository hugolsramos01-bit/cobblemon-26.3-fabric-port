package com.cobblemon.mod.fabric.gametest.mixin;

import com.cobblemon.mod.fabric.CobblemonFabric;
import com.cobblemon.mod.fabric.gametest.DedicatedServerTestBridge;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies the active dedicated test server only when Cobblemon's normal client lookup returns null. */
@Mixin(value = CobblemonFabric.class, remap = false)
public abstract class CobblemonFabricServerBridgeMixin {
    @Inject(method = "server", at = @At("RETURN"), cancellable = true, remap = false)
    private void cobblemonGametest$useActiveDedicatedServer(CallbackInfoReturnable<MinecraftServer> cir) {
        if (cir.getReturnValue() == null) {
            MinecraftServer activeServer = DedicatedServerTestBridge.activeServer();
            if (activeServer != null) cir.setReturnValue(activeServer);
        }
    }
}

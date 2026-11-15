package com.cobblemon.mod.fabric.gametest;

import net.minecraft.server.MinecraftServer;

/** Test-only identity-scoped bridge for Cobblemon's client-side server lookup. */
public final class DedicatedServerTestBridge {
    private static volatile MinecraftServer activeServer;

    private DedicatedServerTestBridge() {}

    public static synchronized void activate(MinecraftServer server) {
        if (activeServer != null && activeServer != server) {
            throw new IllegalStateException("A different dedicated game test server is already active");
        }
        activeServer = server;
    }

    public static synchronized void clear(MinecraftServer expectedServer) {
        if (activeServer == expectedServer) activeServer = null;
    }

    public static MinecraftServer activeServer() {
        return activeServer;
    }
}

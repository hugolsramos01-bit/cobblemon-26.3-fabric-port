package com.cobblemon.mod.fabric.gametest;

import com.cobblemon.mod.common.sherds.CobblemonSherds;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/** Native 26.3 rendering resolves each sherd's pattern through its item component. */
public final class CobblemonSherdMigrationGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void everyCustomSherdProvidesItsNativePotteryPattern(GameTestHelper context) {
        var failures = new java.util.ArrayList<String>();
        for (var sherd : CobblemonSherds.INSTANCE.getAllSherds()) {
            var stack = new ItemStack(sherd.getItem());
            var expected = ResourceKey.create(Registries.DECORATED_POT_PATTERN, sherd.getPatternId());
            var actual = stack.get(DataComponents.PROVIDES_POTTERY_PATTERN);
            if (actual == null || !actual.is(expected)) failures.add(sherd.getPatternId().toString());
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Registered Cobblemon sherds cannot supply their patterns to native pots: " + failures);
        }
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}

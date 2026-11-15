import com.cobblemon.mod.common.util.foodConsumable
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.component.Consumables
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect

fun main() {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    val normal = foodConsumable()
    check(normal == Consumables.DEFAULT_FOOD)
    check(normal.consumeTicks() == 32)
    val fast = foodConsumable(consumeSeconds = 0.8F)
    check(fast.consumeTicks() == 16)
    check(fast.animation() == ItemUseAnimation.EAT)
    check(fast.sound() == normal.sound())
    check(fast.hasConsumeParticles() == normal.hasConsumeParticles())
    val absorption = ApplyStatusEffectsConsumeEffect(MobEffectInstance(MobEffects.ABSORPTION, 900, 0), 1F)
    val dip = foodConsumable(effects = listOf(ClearAllStatusEffectsConsumeEffect.INSTANCE, absorption))
    check(dip.onConsumeEffects().size == 2)
    check(dip.onConsumeEffects()[0] === ClearAllStatusEffectsConsumeEffect.INSTANCE)
    check(dip.onConsumeEffects()[1] === absorption)
    check(absorption.effects().single().duration == 900)
    check(absorption.effects().single().amplifier == 0)
    check(absorption.probability() == 1F)
    check(foodConsumable().onConsumeEffects().isEmpty())
    println("PASS: default food parity; 16/32 tick consumption; ordered clear/absorption effects; duration, amplifier, probability and independent builders.")
}

import com.cobblemon.mod.common.item.appendHealingMachineTooltip
import java.util.function.Consumer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import com.cobblemon.mod.common.api.cooking.effects.CleanseAllEffect
import com.cobblemon.mod.common.api.cooking.effects.CleanseNegativeEffect
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

fun main() {
    val lines = mutableListOf<Component>()
    appendHealingMachineTooltip(Consumer { lines.add(it) })
    check(lines.size == 2)
    check((lines[0].contents as TranslatableContents).key == "block.cobblemon.healing_machine.tooltip1")
    check((lines[1].contents as TranslatableContents).key == "block.cobblemon.healing_machine.tooltip2")
    check(lines.all { it.style.color?.value == 0xAAAAAA })
    for (type in listOf(CleanseAllEffect::class.java, CleanseNegativeEffect::class.java)) {
        check(type.getDeclaredMethod("isInstantaneous").returnType == Boolean::class.javaPrimitiveType)
        type.getDeclaredMethod("applyInstantaneousEffect", ServerLevel::class.java, Entity::class.java,
            Entity::class.java, LivingEntity::class.java, Int::class.javaPrimitiveType, Double::class.javaPrimitiveType)
        type.getDeclaredMethod("applyEffectTick", ServerLevel::class.java, LivingEntity::class.java, Int::class.javaPrimitiveType)
    }
    println("PASS: healing machine tooltip preserves both translation keys, ordering and gray styling through Consumer.")
}

import java.lang.reflect.Modifier
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

fun main() {
    val factoryClass = RenderTypes::class.java
    val id = Identifier::class.java
    val float = java.lang.Float.TYPE
    val boolean = java.lang.Boolean.TYPE
    val expected = listOf(
        "entityCutout" to arrayOf(id),
        "entityCutout" to arrayOf(id, boolean),
        "entityTranslucent" to arrayOf(id),
        "entityTranslucent" to arrayOf(id, boolean),
        "beaconBeam" to arrayOf(id, boolean),
        "dragonRays" to emptyArray(),
        "lightning" to emptyArray(),
        "waterMask" to emptyArray(),
        "energySwirl" to arrayOf(id, float, float),
        "breezeWind" to arrayOf(id, float, float)
    )
    for ((name, parameters) in expected) {
        val method = factoryClass.getMethod(name, *parameters)
        check(Modifier.isStatic(method.modifiers))
        check(method.returnType == RenderType::class.java)
    }
    println("PASS: 10 native static factory overloads retain RenderType return and exact argument contracts")
}

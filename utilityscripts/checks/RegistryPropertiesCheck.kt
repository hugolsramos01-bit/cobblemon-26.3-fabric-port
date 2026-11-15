import com.cobblemon.mod.common.util.withBlockId
import com.cobblemon.mod.common.util.withItemId
import net.minecraft.world.item.Item
import java.lang.reflect.InvocationTargetException
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.state.BlockBehaviour
import java.util.Optional
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.level.block.FlowerBedBlock
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.BonemealSource
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState

private fun description(properties: BlockBehaviour.Properties): String {
    val method = BlockBehaviour.Properties::class.java.getDeclaredMethod("effectiveDescriptionId")
    method.isAccessible = true
    return method.invoke(properties) as String
}

private fun loot(properties: BlockBehaviour.Properties): Identifier {
    val method = BlockBehaviour.Properties::class.java.getDeclaredMethod("effectiveDrops")
    method.isAccessible = true
    val result = method.invoke(properties) as Optional<*>
    return (result.orElseThrow() as ResourceKey<*>).identifier()
}

fun main() {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    // Demonstrate the original failure before exercising the production helper.
    try {
        description(BlockBehaviour.Properties.of())
        error("Unkeyed properties unexpectedly accepted")
    } catch (exception: InvocationTargetException) {
        check(exception.cause is NullPointerException)
        check(exception.cause?.message == "Block id not set")
    }
    val red = BlockBehaviour.Properties.of().withBlockId(Identifier.fromNamespaceAndPath("cobblemon", "red_apricorn_sapling"))
    val blue = BlockBehaviour.Properties.of().withBlockId(Identifier.fromNamespaceAndPath("cobblemon", "blue_apricorn_sapling"))
    check(red !== blue)
    check(description(red) == "block.cobblemon.red_apricorn_sapling")
    check(description(blue) == "block.cobblemon.blue_apricorn_sapling")
    check(loot(red).toString() == "cobblemon:blocks/red_apricorn_sapling")
    check(loot(blue).toString() == "cobblemon:blocks/blue_apricorn_sapling")
    val copied = BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
        .withBlockId(Identifier.fromNamespaceAndPath("cobblemon", "type_gem_block"))
    check(description(copied) == "block.cobblemon.type_gem_block")
    check(loot(copied).toString() == "cobblemon:blocks/type_gem_block")
    try {
        Item.Properties().effectiveModel()
        error("Unkeyed item properties unexpectedly accepted")
    } catch (exception: NullPointerException) {
        check(exception.message == "Item id not set")
    }
    val berry = Item.Properties().withItemId(Identifier.fromNamespaceAndPath("cobblemon", "cheri_berry"))
    val seed = Item.Properties().withItemId(Identifier.fromNamespaceAndPath("cobblemon", "red_apricorn_seed"))
        .useItemDescriptionPrefix()
    check(berry !== seed)
    check(berry.effectiveModel().toString() == "cobblemon:cheri_berry")
    check(seed.effectiveModel().toString() == "cobblemon:red_apricorn_seed")
    val itemDescription = Item.Properties::class.java.getDeclaredMethod("effectiveDescriptionId")
    itemDescription.isAccessible = true
    check(itemDescription.invoke(seed) == "item.cobblemon.red_apricorn_seed")
    berry.useBlockDescriptionPrefix()
    check(itemDescription.invoke(berry) == "block.cobblemon.cheri_berry")
    check(berry.effectiveModel().toString() == "cobblemon:cheri_berry")
    // Check the exact native targets used by BlocksInvoker against the current JAR.
    val nativeFactories = listOf(
        "logProperties" to arrayOf(MapColor::class.java, MapColor::class.java, SoundType::class.java),
        "leavesProperties" to arrayOf(SoundType::class.java),
        "buttonProperties" to emptyArray(),
        "flowerPotProperties" to emptyArray()
    )
    for ((name, parameters) in nativeFactories) {
        check(Blocks::class.java.getDeclaredMethod(name, *parameters).returnType == BlockBehaviour.Properties::class.java)
    }
    ButtonBlock::class.java.getDeclaredConstructor(
        BlockSetType::class.java, Int::class.javaPrimitiveType, BlockBehaviour.Properties::class.java
    )
    check(Blocks.PINK_PETALS is FlowerBedBlock)
    check((Blocks.PINK_PETALS as FlowerBedBlock).shapeHeight == 3.0)
    BonemealableBlock::class.java.getMethod("isValidBonemealTarget", LevelReader::class.java,
        BlockPos::class.java, BlockState::class.java, BonemealSource::class.java)
    BonemealableBlock::class.java.getMethod("isBonemealSuccess", Level::class.java,
        RandomSource::class.java, BlockPos::class.java, BlockState::class.java, BonemealSource::class.java)
    BonemealableBlock::class.java.getMethod("performBonemeal", ServerLevel::class.java,
        RandomSource::class.java, BlockPos::class.java, BlockState::class.java, BonemealSource::class.java)
    BlockBehaviour::class.java.getDeclaredMethod("getAnalogOutputSignal", BlockState::class.java,
        Level::class.java, BlockPos::class.java, Direction::class.java)
    println("PASS: unkeyed block/item properties rejected; distinct IDs, item models, translation prefixes and copied loot paths preserved.")
}

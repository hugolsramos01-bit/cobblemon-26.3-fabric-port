import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.server.Bootstrap
import net.minecraft.tags.TagKey
import net.minecraft.tags.TagLoader
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.ShapedRecipePattern

fun main() {
    SharedConstants.tryDetectVersion()
    Bootstrap.bootStrap()
    val milkTag = TagKey.create(Registries.ITEM, Identifier.parse("c:drinks/milk"))
    val unionTag = TagKey.create(Registries.ITEM, Identifier.parse("cobblemon:recipe_ingredients/tea_liquids"))
    val milkMembers: List<Holder<Item>> = listOf(Items.MILK_BUCKET.builtInRegistryHolder())
    val unionMembers: List<Holder<Item>> = listOf(Items.WATER_BUCKET.builtInRegistryHolder()) + milkMembers
    BuiltInRegistries.ITEM.prepareTagReload(TagLoader.LoadResult(Registries.ITEM,
        mapOf(milkTag to milkMembers, unionTag to unionMembers))).apply()
    val ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
    fun ingredient(json: String) = Ingredient.CODEC.parse(ops, JsonParser.parseString(json))
    check(ingredient("""{"item":"minecraft:stone"}""").error().isPresent)
    val stone = ingredient(""""minecraft:stone"""").getOrThrow()
    check(stone.acceptsItem(Items.STONE.builtInRegistryHolder()))
    check(!stone.acceptsItem(Items.DIRT.builtInRegistryHolder()))
    val alternatives = ingredient("""["minecraft:stone","minecraft:dirt"]""").getOrThrow()
    check(alternatives.acceptsItem(Items.STONE.builtInRegistryHolder()))
    check(alternatives.acceptsItem(Items.DIRT.builtInRegistryHolder()))
    check(!alternatives.acceptsItem(Items.APPLE.builtInRegistryHolder()))
    check(ingredient("[]").error().isPresent)
    check(ingredient("""["minecraft:water_bucket","#c:drinks/milk"]""").error().isPresent)
    val union = ingredient(""""#cobblemon:recipe_ingredients/tea_liquids"""").getOrThrow()
    check(union.acceptsItem(Items.WATER_BUCKET.builtInRegistryHolder()))
    check(union.acceptsItem(Items.MILK_BUCKET.builtInRegistryHolder()))
    check(!union.acceptsItem(Items.APPLE.builtInRegistryHolder()))
    val pattern = ShapedRecipePattern.MAP_CODEC.codec().parse(ops, JsonParser.parseString(
        """{"pattern":["S S"],"key":{"S":"minecraft:stone"}}"""
    )).getOrThrow()
    val slots = pattern.ingredients()
    check(slots.size == 3)
    check(slots[0].isPresent && slots[1].isEmpty && slots[2].isPresent)
    check(slots[0].get().acceptsItem(Items.STONE.builtInRegistryHolder()))
    println("PASS: 26.3 rejects legacy objects and mixed item/tag arrays; identifiers, item alternatives and named unions match holders; empty ingredients rejected; shaped slots retain holes.")
}

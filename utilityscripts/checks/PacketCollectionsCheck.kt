import com.cobblemon.mod.common.util.readCollection
import com.cobblemon.mod.common.util.CobblemonInteractionResults
import com.cobblemon.mod.common.util.readList
import com.cobblemon.mod.common.util.readMap
import com.cobblemon.mod.common.util.readFixedSizeMap
import com.cobblemon.mod.common.util.writeCollection
import com.cobblemon.mod.common.util.writeMap
import com.cobblemon.mod.common.util.writeFixedSizeCollection
import com.cobblemon.mod.common.util.writeFixedSizeMap
import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.InteractionResult

private inline fun withBuffer(bytes: ByteArray? = null, check: (FriendlyByteBuf) -> Unit) {
    val buffer = FriendlyByteBuf(if (bytes == null) Unpooled.buffer() else Unpooled.wrappedBuffer(bytes))
    try {
        check(buffer)
    } finally {
        buffer.release()
    }
}

private fun FriendlyByteBuf.bytes(): List<Int> =
    (0 until writerIndex()).map { getUnsignedByte(it).toInt() }

private inline fun <reified T : Throwable> expectFailure(action: () -> Unit) {
    try {
        action()
    } catch (failure: Throwable) {
        check(failure is T) { "Expected ${T::class.java.name}, got $failure" }
        return
    }
    error("Expected ${T::class.java.name}")
}

fun main() {
    check(CobblemonInteractionResults.sidedSuccess(true) == InteractionResult.SUCCESS)
    check(CobblemonInteractionResults.sidedSuccess(false) == InteractionResult.CONSUME) {
        "Server-side sidedSuccess must retain the original no-swing consume result"
    }
    withBuffer { buffer ->
        buffer.writeCollection(listOf(10, 20, 30)) { target, value -> target.writeByte(value) }
        check(buffer.bytes() == listOf(3, 10, 20, 30)) { "Collection prefix must be VarInt, not a four-byte Int" }
        buffer.writeByte(99)
        check(buffer.readList { it.readUnsignedByte().toInt() } == listOf(10, 20, 30))
        check(buffer.readUnsignedByte().toInt() == 99) { "List decoding consumed the next field" }
        check(!buffer.isReadable)
    }
    withBuffer { buffer ->
        buffer.writeCollection(List(128) { 7 }) { target, value -> target.writeByte(value) }
        check(buffer.getUnsignedByte(0).toInt() == 0x80 && buffer.getUnsignedByte(1).toInt() == 0x01)
        check(buffer.writerIndex() == 130)
        check(buffer.readList { it.readUnsignedByte().toInt() } == List(128) { 7 })
    }
    withBuffer { buffer ->
        buffer.writeCollection(emptyList<Int>()) { target, value -> target.writeByte(value) }
        check(buffer.bytes() == listOf(0))
        check(buffer.readList { it.readByte() }.isEmpty())
    }
    withBuffer { buffer ->
        buffer.writeMap(linkedMapOf(4 to 40, 5 to 50), { target, key -> target.writeByte(key) }, { target, value -> target.writeByte(value) })
        check(buffer.bytes() == listOf(2, 4, 40, 5, 50))
        buffer.writeByte(99)
        check(buffer.readMap({ it.readUnsignedByte().toInt() }, { it.readUnsignedByte().toInt() }) == mapOf(4 to 40, 5 to 50))
        check(buffer.readUnsignedByte().toInt() == 99)
    }
    withBuffer(byteArrayOf(3, 10, 10, 20)) { buffer ->
        val values = buffer.readCollection({ capacity -> HashSet<Int>(capacity) }) { it.readUnsignedByte().toInt() }
        check(values == setOf(10, 20))
    }
    withBuffer(byteArrayOf(2, 10)) { buffer ->
        expectFailure<IndexOutOfBoundsException> { buffer.readList { it.readByte() } }
    }
    withBuffer { buffer ->
        buffer.writeVarInt(-1)
        expectFailure<DecoderException> { buffer.readList { it.readByte() } }
    }
    withBuffer { buffer ->
        buffer.writeVarInt(-1)
        expectFailure<DecoderException> { buffer.readMap({ it.readByte() }, { it.readByte() }) }
    }
    withBuffer { buffer ->
        buffer.writeVarInt(Int.MAX_VALUE)
        expectFailure<IndexOutOfBoundsException> {
            buffer.readCollection({ capacity ->
                check(capacity <= 65536) { "Untrusted packet size caused an unbounded initial allocation" }
                ArrayList<Byte>(capacity)
            }) { it.readByte() }
        }
    }
    val rawBuffer = Unpooled.buffer()
    try {
        rawBuffer.writeFixedSizeCollection(listOf(10, 20)) { target, value -> target.writeByte(value) }
        check((0 until rawBuffer.writerIndex()).map { rawBuffer.getUnsignedByte(it).toInt() } == listOf(0, 0, 0, 2, 10, 20))
        rawBuffer.clear()
        rawBuffer.writeFixedSizeMap(mapOf(4 to 40), { target, key -> target.writeByte(key) }, { target, value -> target.writeByte(value) })
        check((0 until rawBuffer.writerIndex()).map { rawBuffer.getUnsignedByte(it).toInt() } == listOf(0, 0, 0, 1, 4, 40))
        check(rawBuffer.readFixedSizeMap({ it.readUnsignedByte().toInt() }, { it.readUnsignedByte().toInt() }) == mapOf(4 to 40))
    } finally {
        rawBuffer.release()
    }
    println("PASS: client/server interaction results; VarInt and fixed-width collections/maps; empty list, 128-entry boundary, field alignment, sets, truncated/negative/oversized input")
}

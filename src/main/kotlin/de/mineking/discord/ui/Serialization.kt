package de.mineking.discord.ui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.io.DataInput
import java.io.DataOutput
import kotlin.reflect.KType

@OptIn(ExperimentalSerializationApi::class)
internal class StateListSerializer(
    serializersModule: SerializersModule,
    private val types: List<KType>
) : KSerializer<List<Any?>> {
    val serializers = types.map { serializersModule.serializer(it) }

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("StateList", StructureKind.LIST) {
        for (index in types.indices) {
            element("element$index", serializers[index].descriptor)
        }
    }

    override fun serialize(encoder: Encoder, value: List<Any?>) {
        require(value.size == types.size) { "Types and values size mismatch" }
        encoder.encodeStructure(descriptor) {
            for (index in value.indices) {
                encodeSerializableElement(descriptor, index, serializers[index], value[index])
            }
        }
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        val result = ArrayList<Any?>(types.size)

        for (index in types.indices) {
            val element = decodeSerializableElement(descriptor, index, serializers[index])
            result += element
        }

        result
    }
}

@ExperimentalSerializationApi
internal class BinaryEncoder(override val serializersModule: SerializersModule, val output: DataOutput) : AbstractEncoder() {
    override fun encodeBoolean(value: Boolean) = output.writeByte(if (value) 1 else 0)
    override fun encodeByte(value: Byte) = output.writeByte(value.toInt())
    override fun encodeShort(value: Short) = output.writeVarInt(value.toInt())
    override fun encodeInt(value: Int) = output.writeVarInt(value)
    override fun encodeLong(value: Long) = output.writeVarLong(value)
    override fun encodeFloat(value: Float) = output.writeInt(value.toBits())
    override fun encodeDouble(value: Double) = output.writeLong(value.toBits())
    override fun encodeChar(value: Char) = output.writeVarInt(value.code)
    override fun encodeString(value: String) = output.writeCompactString(value)
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = output.writeVarInt(index)

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        output.writeVarInt(collectionSize)
        return this
    }

    override fun encodeNull() = encodeBoolean(false)
    override fun encodeNotNullMark() = encodeBoolean(true)
}

@ExperimentalSerializationApi
internal class BinaryDecoder(override val serializersModule: SerializersModule, val input: DataInput, var elementsCount: Int = 0) : AbstractDecoder() {
    private var elementIndex = 0

    override fun decodeBoolean(): Boolean = input.readByte().toInt() != 0
    override fun decodeByte(): Byte = input.readByte()
    override fun decodeShort(): Short = input.readVarInt().toShort()
    override fun decodeInt(): Int = input.readVarInt()
    override fun decodeLong(): Long = input.readVarLong()
    override fun decodeFloat(): Float = Float.fromBits(input.readInt())
    override fun decodeDouble(): Double = Double.fromBits(input.readLong())
    override fun decodeChar(): Char = input.readVarInt().toChar()
    override fun decodeString(): String = input.readCompactString()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = input.readVarInt()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        BinaryDecoder(serializersModule, input, descriptor.elementsCount)

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
        input.readVarInt().also { elementsCount = it }

    override fun decodeNotNullMark(): Boolean = decodeBoolean()
}

fun DataOutput.writeVarInt(value: Int) {
    var v = value
    while ((v and 0x7F.inv()) != 0) {
        writeByte((v and 0x7F) or 0x80)
        v = v ushr 7
    }
    writeByte(v)
}

fun DataInput.readVarInt(): Int {
    var shift = 0
    var result = 0
    while (true) {
        val b = readByte().toInt()
        result = result or ((b and 0x7F) shl shift)
        if ((b and 0x80) == 0) return result
        shift += 7
    }
}

fun DataOutput.writeVarLong(value: Long) {
    var v = value
    while ((v and 0x7F.inv().toLong()) != 0L) {
        writeByte(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    writeByte(v.toInt())
}

fun DataInput.readVarLong(): Long {
    var shift = 0
    var result = 0L
    while (true) {
        val b = readByte().toInt()
        result = result or ((b and 0x7F).toLong() shl shift)
        if ((b and 0x80) == 0) return result
        shift += 7
    }
}

fun DataOutput.writeCompactString(value: String) {
    val bytes = value.encodeToByteArray()
    writeVarInt(bytes.size)
    write(bytes)
}

fun DataInput.readCompactString(): String {
    val size = readVarInt()
    val bytes = ByteArray(size)
    readFully(bytes)
    return bytes.decodeToString()
}
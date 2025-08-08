package de.mineking.discord.ui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.serializer
import java.nio.charset.StandardCharsets
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

typealias StateUpdateHandler<T> = (old: T, new: T) -> Unit

interface State<out T> {
    val value: T
}

interface MutableState<T> : State<T> {
    override var value: T

    operator fun component1(): () -> T = { value }
    operator fun component2(): (T) -> Unit = { value = it }
    operator fun component3() = this
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>) = value

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

fun <T, U> MutableState<T>.map(toOther: (T) -> U, fromOther: (U) -> T): MutableState<U> = object : MutableState<U> {
    override var value: U
        get() = toOther(this@map.value)
        set(value) {
            this@map.value = fromOther(value)
        }
}

data class InternalState<T>(val type: KType, val initial: T, val handler: StateUpdateHandler<T>?)

fun <T> virtualState(value: () -> T, setter: (value: T) -> Unit) = object : MutableState<T> {
    override var value: T
        get() = value()
        set(value) = setter(value)
}

fun <T> virtualState(initial: T) = object : MutableState<T> {
    override var value = initial
}

fun <T> virtualReadonlyState(value: T) = object : State<T> {
    override val value = value
}

class StateData(val data: MutableList<Any?>, val states: List<InternalState<*>>) {
    fun <T> getState(id: Int, handler: StateUpdateHandler<T>?) = object : MutableState<T> {
        override var value: T
            @Suppress("UNCHECKED_CAST")
            get() = data[id] as T
            set(value) {
                handler?.invoke(this.value, value)
                data[id] = value
            }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun encode() = encoder.encodeToByteArray(StateListSerializer(states.map { it.type }), data).encodeState()

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        var encoder: Cbor = Cbor

        @OptIn(ExperimentalSerializationApi::class)
        fun decode(data: String, states: List<InternalState<*>>) = StateData(
            if (data.isEmpty()) mutableListOf()
            else encoder.decodeFromByteArray(StateListSerializer(states.map { it.type }), data.decodeState()).toMutableList(),
            states
        )

        fun createInitial(states: List<InternalState<*>>) = StateData(states.map { it.initial }.toMutableList(), states)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class StateListSerializer(
    private val types: List<KType>
) : KSerializer<List<Any?>> {

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("StateList", StructureKind.LIST) {
        for (index in types.indices) {
            val type = types[index]
            element("element$index", serializer(type).descriptor)
        }
    }

    override fun serialize(encoder: Encoder, value: List<Any?>) {
        require(value.size == types.size) { "Types and values size mismatch" }
        encoder.encodeCollection(descriptor, value.size) {
            for (index in value.indices) {
                val type = types[index]

                encodeSerializableElement(descriptor, index, serializer(type), value[index])
            }
        }
    }

    override fun deserialize(decoder: Decoder) =decoder.decodeStructure(descriptor) {
        val result = ArrayList<Any?>(types.size)

        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break

            val element = decodeSerializableElement(descriptor, index, serializer(types[index]))
            result += element
        }

        result
    }
}

interface StateContainer {
    val stateData: StateData
    val cache: MutableList<Any?>
}

fun <T> MenuConfig<*, *>.state(type: KType, initial: T, handler: StateUpdateHandler<T>? = null): MutableState<T> {
    if (isBuild()) {
        configState.menu.states += InternalState(type, initial, handler)
        context.stateData.data += initial
    } else require(type == configState.menu.states[configState.currentState].type)

    return context.stateData.getState(configState.currentState, handler)
        .also { configState.currentState++ }
}

inline fun <reified T> MenuConfig<*, *>.state(initial: T, noinline handler: StateUpdateHandler<T>? = null) = state(typeOf<T>(), initial, handler)
inline fun <reified T> MenuConfig<*, *>.state(noinline handler: StateUpdateHandler<T?>? = null) = state(null, handler)

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalContracts::class)
fun <T> MenuConfig<*, *>.cache(block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val value = if (context.cache.size <= configState.currentCache) block().also { context.cache += it }
    else context.cache[configState.currentCache] as T

    configState.currentCache++

    return value
}

internal fun List<String>.decodeState(parts: Int) = joinToString("") {
    val original = it.split(":", limit = parts)[parts - 1]
    val length = original.take(2).toInt()
    original.drop(2).take(length)
}

@PublishedApi
internal fun String.decodeState() = toByteArray(StandardCharsets.ISO_8859_1)

@PublishedApi
internal fun ByteArray.encodeState() = toString(StandardCharsets.ISO_8859_1)
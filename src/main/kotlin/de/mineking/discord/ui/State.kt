package de.mineking.discord.ui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
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

data class StateData(val data: MutableList<String>) {
    private val cache = arrayOfNulls<Pair<KType, Any?>>(data.size).toMutableList()

    fun pushInitial(type: KType, value: Any?) {
        data += encodeSingle(type, value)
        cache += type to value
    }

    private fun get(id: Int, type: KType): Any? =
        cache.getOrNull(id)?.second ?: decodeSingle(type, data[id])
            .also { cache[id] = type to it }
    private fun set(id: Int, value: Any?, type: KType) {
        cache[id] = type to value
    }

    fun <T> getState(type: KType, id: Int, handler: StateUpdateHandler<T>?) = object : MutableState<T> {
        override var value: T
            @Suppress("UNCHECKED_CAST")
            get() = get(id, type) as T
            set(value) {
                handler?.invoke(this.value, value)
                set(id, value, type)
            }
    }

    fun effectiveData(id: Int) = cache[id]?.let { (type, value) -> encodeSingle(type, value) } ?: data[id]

    fun encode() = encode(typeOf<List<String>>(), data.indices.map { effectiveData(it) })

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        var serializersModule = Cbor.serializersModule

        @OptIn(ExperimentalSerializationApi::class)
        private fun encode(type: KType, value: Any?) = Cbor.encodeToByteArray(serializersModule.serializer(type), value).map { it.toInt().toChar() }.joinToString("")

        @OptIn(ExperimentalSerializationApi::class)
        private fun decode(type: KType, data: String) = Cbor.decodeFromByteArray(serializersModule.serializer(type), data.map { it.code.toByte() }.toByteArray())

        @Suppress("UNCHECKED_CAST")
        fun decode(data: String) = StateData(if (data.isEmpty()) mutableListOf() else (decode(typeOf<List<String>>(), data) as List<String>).toMutableList())

        fun createInitial(states: List<InternalState<*>>) = StateData(states.map { encodeSingle(it.type, it.initial) }.toMutableList())

        fun encodeSingle(type: KType, value: Any?): String = encode(type, value)
        fun decodeSingle(type: KType, value: String): Any? = decode(type, value)
    }
}

interface StateContainer {
    val stateData: StateData
    val cache: MutableList<Any?>
}

fun MenuConfig<*, *>.skipState(amount: Int = 1) {
    if (isBuild()) repeat(amount) {
        context.stateData.pushInitial(typeOf<Unit>(), Unit)
    }

    configState.currentState += amount
}

fun <T> MenuConfig<*, *>.state(type: KType, initial: T, handler: StateUpdateHandler<T>? = null): MutableState<T> {
    if (isBuild()) {
        configState.menu.states += InternalState(type, initial, handler)
        context.stateData.pushInitial(type, initial)
    }

    return context.stateData.getState(type, configState.currentState, handler)
        .also { configState.currentState++ }
}

inline fun <reified T> MenuConfig<*, *>.state(initial: T, noinline handler: StateUpdateHandler<T>? = null) = state(typeOf<T>(), initial, handler)
inline fun <reified T> MenuConfig<*, *>.state(noinline handler: StateUpdateHandler<T?>? = null) = state(null, handler)

internal fun List<String>.decodeState(parts: Int) = joinToString("") {
    val original = it.split(":", limit = parts)[parts - 1]
    val length = original.take(2).toInt()
    original.drop(2).take(length)
}
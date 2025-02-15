package de.mineking.discord.ui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.serializer
import kotlin.collections.toByteArray
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.text.map

typealias StateGetter<T> = StateContext<*>.() -> T
typealias StateSetter<T> = StateContext<*>.(value: T) -> Unit
typealias StateHandler<T> = (old: T, new: T) -> Unit

interface IState {
    val id: Int
    fun ref() = this
}

interface ReadState<T> : IState {
    fun get(context: StateContext<*>?): T
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = get(null)

    fun <O> transform(toOther: (value: T) -> O): ReadState<O> = object : ReadState<O> {
        override val id: Int = this@ReadState.id
        override fun get(context: StateContext<*>?): O = toOther(this@ReadState.get(context))
    }
}

interface WriteState<T> : IState {
    fun set(context: StateContext<*>?, value: T)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(null, value)

    fun <O> transform(fromOther: (value: O) -> T): WriteState<O> = object : WriteState<O> {
        override val id: Int = this@WriteState.id
        override fun set(context: StateContext<*>?, value: O) = this@WriteState.set(context, fromOther(value))
    }
}

interface State<T> : ReadState<T>, WriteState<T> {
    override fun ref() = this

    fun <O> transform(toOther: (value: T) -> O, fromOther: (value: O) -> T): State<O> = object : State<O> {
        override val id: Int = this@State.id

        override fun get(context: StateContext<*>?): O = toOther(this@State.get(context))
        override fun set(context: StateContext<*>?, value: O) = this@State.set(context, fromOther(value))
    }

    fun update(context: StateContext<*>? = null, handler: (current: T) -> Unit) {
        val current = get(context)
        handler(current)
        set(context, current)
    }

    operator fun component1(): StateGetter<T> = { get(this) }
    operator fun component2(): StateSetter<T> = { set(this, it) }
    operator fun component3() = ref()
    operator fun component4() = id
}

data class InternalState<T>(val type: KType, val initial: T, val handler: StateHandler<T>?)

fun <T> constantState(value: T) = object : ReadState<T> {
    override val id: Int = -1
    override fun get(context: StateContext<*>?): T = value
}

fun <T> sinkState(value: T? = null) = object : State<T?> {
    override val id: Int = -1
    override fun get(context: StateContext<*>?): T? = value
    override fun set(context: StateContext<*>?, value: T?) {}
}

fun <T> dynamicState(value: () -> T, setter: (value: T) -> Unit) = object : State<T> {
    override val id: Int = -1

    override fun get(context: StateContext<*>?) = value()
    override fun set(context: StateContext<*>?, value: T) = setter(value)
}

interface StateAccessor {
    fun currentState(): Int

    fun skipState(amount: Int)
    fun <T> nextState(type: KType, handler: StateHandler<T>?): State<T>
}

data class StateData(val data: MutableList<String>) {
    fun pushInitial(type: KType, value: Any?) {
        data += encodeSingle(type, value)
    }

    fun get(id: Int, type: KType): Any? = decodeSingle(type, data[id])
    fun set(id: Int, value: Any?, type: KType) {
        data[id] = encodeSingle(type, value)
    }

    fun <T> getState(type: KType, id: Int, handler: StateHandler<T>? = null): State<T> {
        return object : State<T> {
            override val id: Int get() = id

            @Suppress("UNCHECKED_CAST")
            override fun get(context: StateContext<*>?): T {
                val state = context?.stateData ?: this@StateData
                return state.get(id, type) as T
            }

            override fun set(context: StateContext<*>?, value: T) {
                val state = context?.stateData ?: this@StateData

                handler?.invoke(get(context), value)
                state.set(id, value, type)
            }
        }
    }

    fun access() = object : StateAccessor {
        private var currentState = 0

        override fun currentState(): Int = currentState

        override fun skipState(amount: Int) {
            currentState += amount
        }

        override fun <T> nextState(type: KType, handler: StateHandler<T>?): State<T> = getState(type, currentState++, handler)
    }

    fun encode() = encode(typeOf<List<String>>(), data)

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        internal fun encode(type: KType, value: Any?) = Cbor.encodeToByteArray(Cbor.serializersModule.serializer(type), value).map { it.toInt().toChar() }.joinToString("")

        @OptIn(ExperimentalSerializationApi::class)
        internal fun decode(type: KType, data: String) = Cbor.decodeFromByteArray(Cbor.serializersModule.serializer(type), data.map { it.code.toByte() }.toByteArray())

        @Suppress("UNCHECKED_CAST")
        fun decode(data: String) = StateData(if (data.isEmpty()) mutableListOf() else (decode(typeOf<List<String>>(), data) as List<String>).toMutableList())

        fun createInitial(states: List<InternalState<*>>) = StateData(states.map { encodeSingle(it.type, it.initial) }.toMutableList())

        fun encodeSingle(type: KType, value: Any?): String = encode(type, value)
        fun decodeSingle(type: KType, value: String): Any? = decode(type, value)
    }
}

@MenuMarker
interface StateContext<M> {
    val menuInfo: MenuInfo<M>
    val stateData: StateData
}

@MenuMarker
class SendState<M>(override val menuInfo: MenuInfo<M>, override val stateData: StateData, val param: M) : StateContext<M>

@MenuMarker
interface StateConfig {
    fun currentState(): Int

    fun skipState(amount: Int = 1)
    fun <T> state(type: KType, initial: T, handler: StateHandler<T>? = null): State<T>
}

inline fun <reified T> StateConfig.state(initial: T, noinline handler: StateHandler<T>? = null) = state(typeOf<T>(), initial, handler)
inline fun <reified T> StateConfig.state(noinline handler: StateHandler<T?>? = null) = state(null, handler)
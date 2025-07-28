package de.mineking.discord.ui

class StateBuilder<M, N>(val base: StateContext<M>, val target: Menu<N, *, *>) {
    val data = mutableListOf<String>()
    private var baseIndex = 0
    private var targetIndex = 0

    fun set(id: Int, value: Any?) {
        data[id] = StateData.encodeSingle(target.states[id].type, value)
    }

    fun <T> push(value: T) {
        data += ""
        set(targetIndex++, value)
    }

    fun pushDefault() {
        push(target.states[targetIndex].initial)
    }

    fun pushDefaults() {
        while (targetIndex < target.states.size) pushDefault()
    }

    fun copy(amount: Int = 1) {
        repeat(amount) {
            data += base.stateData.effectiveData(baseIndex++)
            targetIndex++
        }
    }

    fun skip(amount: Int = 1) {
        baseIndex += amount
    }

    fun copyAll() {
        while (baseIndex < base.stateData.data.size && targetIndex < target.states.size) copy()
    }

    fun build() = StateData(data)
}

typealias StateBuilderConfig = StateBuilder<*, *>.() -> Unit

val DEFAULT_STATE_BUILDER: StateBuilderConfig = { copyAll(); pushDefaults() }
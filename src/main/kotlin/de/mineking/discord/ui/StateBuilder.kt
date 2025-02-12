package de.mineking.discord.ui

class StateBuilder<M, N>(val base: StateContext<M>, val target: Menu<N, *, *>) {
    val data = mutableListOf<String>()
    private var index = 0

    fun set(id: Int, value: Any?) {
        data[id] = StateData.encodeSingle(target.states[id].type, value)
    }

    fun <T> push(value: T) {
        data += ""
        set(index++, value)
    }

    fun pushDefault() {
        push(target.states[index].initial)
    }

    fun pushDefaults() {
        while (index < target.states.size) pushDefault()
    }

    fun copy(amount: Int = 1) {
        repeat(amount) {
            data += base.stateData.data[index++]
        }
    }

    fun skip(amount: Int = 1) {
        index += amount
    }

    fun copyAll() {
        while (index < base.stateData.data.size) copy()
    }

    fun build() = StateData(data)
}

typealias StateBuilderConfig = StateBuilder<*, *>.() -> Unit
val DEFAULT_STATE_BUILDER: StateBuilderConfig = { copyAll(); pushDefaults() }
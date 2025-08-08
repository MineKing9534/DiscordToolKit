package de.mineking.discord.ui

class StateBuilder(val base: StateContainer, val target: Menu<*, *, *>) {
    private companion object {
        val EMPTY_DATA = ByteArray(0)
    }

    val data = mutableListOf<Any?>()
    private var baseIndex = 0
    private var targetIndex = 0

    fun set(id: Int, value: Any?) {
        data[id] = value
    }

    fun <T> push(value: T) {
        data += EMPTY_DATA
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
            data += base.stateData.data[baseIndex++]
            targetIndex++
        }
    }

    fun skip(amount: Int = 1) {
        baseIndex += amount
    }

    fun copyAll() {
        while (baseIndex < base.stateData.data.size && targetIndex < target.states.size) copy()
    }

    fun build() = StateData(data, target.states)
}

typealias StateBuilderConfig = StateBuilder.() -> Unit

val DEFAULT_STATE_BUILDER: StateBuilderConfig = { copyAll(); pushDefaults() }
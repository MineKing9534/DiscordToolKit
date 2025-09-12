package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.message.MessageMenuConfig
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent

@DslMarker
annotation class MenuMarker

enum class DeferMode { NEVER, ALWAYS, UNLESS_PREVENTED }

var DEFAULT_DEFER_MODE = DeferMode.NEVER

abstract class Menu<M, E : GenericInteractionCreateEvent, L : LocalizationFile?>(
    val manager: UIManager, val name: String, val defer: DeferMode,
    val localization: L
) {
    @PublishedApi
    internal val setup = mutableListOf<Any?>()
    @PublishedApi
    internal val states = mutableListOf<InternalState<*>>()

    val parent = name
        .lastIndexOf('.')
        .takeIf { it != -1 }
        ?.let { name.substring(0, it) }
        ?.let { manager.menus[it] }

    fun decodeState(data: String) = StateData.decode(data, states)

    abstract suspend fun handle(event: E)

    override fun toString() = "${javaClass.simpleName}[$name]"
}

internal fun String.menuName() = substring(lastIndexOf('.') + 1)

interface IdGenerator {
    fun nextId(base: String): String
    fun withParameter(parameter: String): IdGenerator
}

fun IdGenerator.nextId(config: MenuConfig<*, *>, name: String) =
    if (config is MessageMenuConfig<*, *>) nextId("${config.menu.name}:$name:")
    else nextId("$name:")

object EmptyIdGenerator : IdGenerator {
    override fun nextId(base: String) = "-"
    override fun withParameter(parameter: String) = this
}

open class IdGeneratorImpl(private val state: String, private val postfix: String = "") : IdGenerator {
    internal var pos = 0

    @Synchronized
    override fun nextId(base: String): String {
        val length = (Button.ID_MAX_LENGTH - base.length - postfix.length - 2).coerceIn(0, state.length - pos)
        val result = base + String.format("%02d", length) + state.substring(pos, pos + length) + postfix

        pos += length
        return result
    }

    override fun withParameter(parameter: String) = object : IdGeneratorImpl(state, parameter) {
        override fun nextId(base: String) = super.nextId(base)
            .also { this@IdGeneratorImpl.pos = this.pos }
    }

    fun charactersLeft() = state.length - pos
}

class MenuLazyImpl<out T>(val menu: Menu<*, *, *>, var active: Boolean = false, val default: T, provider: suspend () -> T) : Lazy<T> {
    private val _value = menu.manager.manager.coroutineScope.async(start = CoroutineStart.LAZY) { provider() }

    override suspend fun getValue() = if (active) _value.await() else default
}

fun MenuConfig<out MessageChannel, *>.channel() = lazy {
    parameter({ error("Cannot access the menu channel during the BUILD phase") }, { it }, { event.messageChannel })
}

fun MenuConfig<out GenericInteractionCreateEvent, *>.event() = lazy {
    parameter({ error("Cannot access the menu event during the BUILD phase") }, { it }, { event })
}

@Suppress("ObjectInheritsException")
object RenderTermination : RuntimeException() {
    private fun readResolve(): Any = RenderTermination
}

fun terminateRender(): Nothing = throw RenderTermination
package de.mineking.discord.ui

import de.mineking.discord.ui.builder.components.BackReference
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import kotlin.reflect.typeOf

typealias ComponentHandler<M, E> = suspend ComponentContext<M, E>.() -> Unit

@MenuMarker
class ComponentContext<M, out E : GenericComponentInteractionCreateEvent>(menu: MenuInfo<M>, stateData: StateData, event: E) : HandlerContext<M, E>(menu, stateData, event) {
    override val message: Message get() = event.message
    var update: Boolean? = null

    fun forceUpdate() {
        update = true
    }

    fun preventUpdate() {
        update = false
    }

    fun defer() {
        event.deferEdit().queue()
    }

    suspend fun render() = (menuInfo.menu as MessageMenu).render(this)
    suspend fun update() = (menuInfo.menu as MessageMenu).update(this)

    suspend fun cloneMenu(ephemeral: Boolean = true) =
        if (isAcknowledged) hook.sendMessage(render().toCreateData()).setEphemeral(ephemeral).queue()
        else reply(render().toCreateData()).setEphemeral(ephemeral).queue()

    suspend fun <N> switchMenu(menu: Menu<N, *, *>, builder: StateBuilderConfig = DEFAULT_STATE_BUILDER) {
        preventUpdate()

        val state = StateBuilder(this, menu)
        state.builder()

        when (menu) {
            is MessageMenu -> {
                val context = ComponentContext(menu.info, state.build(), event)

                if (menu.defer == DeferMode.ALWAYS && !context.event.isAcknowledged) context.disableComponents(context.message).queue()
                context.update()
            }

            is ModalMenu -> {
                val context = TransferContext(menu.info, state.build(), event, event.message)
                event.replyModal(menu.build(context)).queue()
            }
        }
    }

    suspend operator fun BackReference.invoke(state: StateBuilderConfig = {
        copy(stateCount)
        pushDefaults()
    }) = switchMenu(menu.menu, state)
}

fun <C : Component> createMessageComponent(vararg components: MessageComponent<out C>) = createMessageComponent(components.toList())
fun <C : Component> createMessageComponent(components: List<MessageComponent<out C>>) = object : MessageComponent<C> {
    override fun elements() = components.flatMap { it.elements() }
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = components.flatMap { it.render(config, generator) }
    override fun toString() = "MessageComponent"
}

class MessageComponentBuilder<C : Component> {
    internal val components = mutableListOf<MessageComponent<out C>>()

    operator fun MessageComponent<out C>.unaryPlus() {
        components += this
    }
}

fun <C : Component> createMessageComponent(builder: MessageComponentBuilder<C>.() -> Unit) =
    createMessageComponent(MessageComponentBuilder<C>().apply(builder).components)

interface MessageComponent<C : Component> : IComponent<C> {
    fun elements(): List<MessageElement<*, *>>
    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<C>) -> List<C>) = object : MessageComponent<C> {
        override fun elements() = this@MessageComponent.elements()
        override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@MessageComponent.render(config, it) }
        override fun toString() = this@MessageComponent.toString()
    }
}

class MessageElement<C : Component, E : GenericComponentInteractionCreateEvent>(
    val name: String,
    val handler: ComponentHandler<*, E>,
    val renderer: suspend (MenuConfig<*, *>, IdGenerator) -> C?
) : MessageComponent<C> {
    override fun elements() = listOf(this)
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderer(config, generator)?.let { listOf(it) } ?: emptyList()
    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<C>) -> List<C>) = MessageElement(name, handler) { config, id -> mapper(id) { render(config, it) }.firstOrNull() }

    override fun toString() = "MessageElement[$name]"
}

fun <C : Component, E : GenericComponentInteractionCreateEvent> createMessageElement(
    name: String,
    handler: ComponentHandler<*, E> = {},
    renderer: suspend (MenuConfig<*, *>, String) -> C?
) = MessageElement(name, handler) { config, id -> renderer(config, id.nextId("${config.menuInfo.name}:$name:")) }

fun <C : Component> createLayoutComponent(
    vararg children: MessageComponent<*>,
    renderer: suspend (MenuConfig<*, *>, IdGenerator) -> C
) = createLayoutComponent(children.toList()) { config, id -> listOf(renderer(config, id)) }

fun <C : Component> createLayoutComponent(
    children: List<MessageComponent<*>>,
    renderer: suspend (MenuConfig<*, *>, IdGenerator) -> List<C>
) = object : MessageComponent<C> {
    override fun elements() = children.flatMap { it.elements() }
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderer(config, generator)
    override fun toString() = "MessageComponent"
}

fun <C : Component> createMessageComponent(
    renderer: suspend (MenuConfig<*, *>, String) -> C
) = createLayoutComponent { config, id -> renderer(config, "::") }

@Suppress("UNCHECKED_CAST")
inline fun <reified T, C : Component, W : IComponent<C>> W.withParameter(parameter: T) = transform { id, render ->
    require(id.postfix.isEmpty())

    id.postfix = StateData.encodeSingle(typeOf<T>(), parameter)
    render(id).also { id.postfix = "" }
} as W

inline fun <reified T> ComponentContext<*, *>.parameter(): T {
    val base = event.componentId.split(":", limit = 3)[2]
    val length = base.take(2).toInt()
    val value = base.drop(2 + length)

    return StateData.decodeSingle(typeOf<T>(), value) as T
}
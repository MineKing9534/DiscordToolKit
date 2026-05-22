package de.mineking.discord.ui.message

import de.mineking.discord.ui.*
import de.mineking.discord.ui.builder.components.message.BackReference
import de.mineking.discord.ui.modal.ModalMenu
import kotlinx.serialization.serializer
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import kotlin.reflect.KType
import kotlin.reflect.typeOf

typealias ComponentHandler<M, E> = suspend ComponentContext<M, E>.() -> Unit

class ComponentContext<M, out E : GenericComponentInteractionCreateEvent>(val menu: MessageMenu<M, *>, stateData: StateData, event: E) :
    HandlerContext<M, E>(stateData, event)
{
    override val message = event.message

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

    suspend fun render() = menu.render(this)
    suspend fun update() = menu.update(this)

    suspend fun cloneMenu(ephemeral: Boolean = true) =
        if (isAcknowledged) hook.sendMessage(render().toCreateData()).setEphemeral(ephemeral).queue()
        else reply(render().toCreateData()).setEphemeral(ephemeral).queue()

    suspend fun <N> switchMenu(menu: Menu<N, *, *>, builder: StateBuilderConfig = DEFAULT_STATE_BUILDER) {
        preventUpdate()

        val state = StateBuilder(this, menu)
        state.builder()

        when (menu) {
            is MessageMenu -> {
                val context = ComponentContext(menu, state.build(), event)

                if (menu.defer == DeferMode.ALWAYS && !context.event.isAcknowledged) context.disableComponents(context.message).queue()
                context.update()
            }

            is ModalMenu -> {
                val context = TransferContext<N, _>(state.build(), event, event.message)
                event.replyModal(menu.build(context)).queue()
            }
        }
    }

    suspend operator fun BackReference.invoke(state: StateBuilderConfig = {
        copy(stateCount)
        pushDefaults()
    }) = switchMenu(menu, state)
}

fun <C : Component> createMessageComponent(vararg components: MessageComponent<out C>) = createMessageComponent(components.toList())
fun <C : Component> createMessageComponent(components: List<MessageComponent<out C>>) = object : MessageComponent<C> {
    override suspend fun elements() = components.flatMap { it.elements() }
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = components.flatMap { it.render(config, generator) }
    override fun toString() = "MessageComponent"
}

class MessageComponentBuilder<C : Component> {
    internal val components = mutableListOf<MessageComponent<out C>>()

    operator fun MessageComponent<out C>.unaryPlus() {
        components += this
    }
}

fun <C : Component> createMessageComponent(builder: suspend MessageComponentBuilder<C>.() -> Unit) = object : MessageComponent<C> {
    private val components = SuspendLazy<_, Unit> { MessageComponentBuilder<C>().apply { builder() }.components }

    override suspend fun elements() = components.resolve().flatMap { it.elements() }
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = components.resolve().flatMap { it.render(config, generator) }
    override fun toString() = "MessageComponent"
}

interface MessageComponent<C : Component> : IComponent<C> {
    suspend fun elements(): List<MessageElement<*, *>>
    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<C>) -> List<C>) = object : MessageComponent<C> {
        override suspend fun elements() = this@MessageComponent.elements()
        override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@MessageComponent.render(config, it) }
        override fun toString() = this@MessageComponent.toString()
    }
}

open class MessageElement<C : Component, E : GenericComponentInteractionCreateEvent>(
    override val name: String,
    val handler: ComponentHandler<*, E>?,
    val renderer: suspend (MenuConfig<*, *>, IdGenerator) -> C?
) : MessageComponent<C>, IElement<C> {
    override suspend fun elements() = listOf(this)
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderer(config, generator)?.let { listOf(it) } ?: emptyList()
    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<C>) -> List<C>) = MessageElement(name, handler) { config, id -> mapper(id) { render(config, it) }.firstOrNull() }

    override fun toString() = "MessageElement[$name]"
}

fun <C : Component, E : GenericComponentInteractionCreateEvent> createMessageElement(
    name: String,
    handler: ComponentHandler<*, E>? = null,
    renderer: suspend (MenuConfig<*, *>, String) -> C?
) = MessageElement(name, handler) { config, id -> renderer(config, id.nextId(config, name)) }

fun <C : Component> createMessageLayoutComponent(
    vararg children: MessageComponent<*>,
    renderer: suspend (MenuConfig<*, *>, IdGenerator) -> C
) = createMessageLayoutComponent(children.toList()) { config, id -> listOf(renderer(config, id)) }

fun <C : Component> createMessageLayoutComponent(
    children: List<MessageComponent<*>>,
    renderer: suspend (MenuConfig<*, *>, IdGenerator) -> List<C>
) = object : MessageComponent<C> {
    override suspend fun elements() = children.flatMap { it.elements() }
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderer(config, generator)
    override fun toString() = "MessageComponent"
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T, C : Component, W : IComponent<C>> W.withParameter(parameter: T) = transform { id, render ->
    val parameter = StateData.encode(StateData.serializers.serializer(typeOf<T>()), parameter)
    render(id.withParameter(parameter))
} as W

fun <T> ComponentContext<*, *>.parameter(type: KType): T {
    val base = event.componentId.split(":", limit = 3)[2]
    val length = base.take(2).toInt()
    val value = base.drop(2 + length)

    @Suppress("UNCHECKED_CAST")
    return StateData.decode(StateData.serializers.serializer(type), value) as T
}

inline fun <reified T> ComponentContext<*, *>.parameter() = parameter<T>(typeOf<T>())
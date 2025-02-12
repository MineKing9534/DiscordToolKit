package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.components.ActionComponent

typealias ComponentHandler<M, E> = ComponentContext<M, E>.() -> Unit

@MenuMarker
class ComponentContext<M, out E : GenericComponentInteractionCreateEvent>(menu: MenuInfo<M>, stateData: StateData, event: E) : HandlerContext<M, E>(menu, stateData, event) {
    override val message: Message get() = event.message
    internal var update: Boolean? = null

    fun forceUpdate() {
        update = true
    }

    fun preventUpdate() {
        update = false
    }

    fun defer() {
        event.deferEdit().queue()
    }

    fun <N> switchMenu(menu: Menu<N, *, *>, builder: StateBuilderConfig = DEFAULT_STATE_BUILDER) {
        preventUpdate()

        val state = StateBuilder(this, menu)
        state.builder()

        when (menu) {
            is MessageMenu -> {
                val context = ComponentContext(menu.info, state.build(), event)

                if (menu.defer == DeferMode.ALWAYS && !context.event.isAcknowledged) context.disableComponents(context.message).queue()
                menu.update(context)
            }

            is ModalMenu -> {
                val context = TransferContext(menu.info, state.build(), event, event.message)
                event.replyModal(menu.build(context)).queue()
            }
        }
    }
}

fun createMessageComponent(components: List<IMessageComponent>) = MessageComponent(components)
fun createMessageComponent(vararg components: IMessageComponent) = createMessageComponent(components.toList())

internal open class Transform(val other: IMessageComponent) : IMessageComponent by other

interface IMessageComponent {
    fun children(): List<IMessageComponent>
    fun elements(): List<MessageElement<*, *>> = children().flatMap { if (it is MessageElement<*, *>) listOf(it) else it.elements() }

    fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ActionComponent?, MessageElement<*, *>>>
    fun transform(transform: (ActionComponent?) -> ActionComponent?): IMessageComponent = object : Transform(this) {
        override fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ActionComponent?, MessageElement<*, *>>> = other.render(menu, generator).map { (component, element) -> transform(component) to element }
    }

    fun disabled(state: Boolean = true) = transform { it?.withDisabled(state || it.isDisabled) }
    fun enabled(state: Boolean = true) = disabled(!state)

    fun hide(hide: Boolean = true) = transform { if (hide) null else it }
    fun show(show: Boolean = true) = hide(!show)
}

abstract class MessageElement<C : ActionComponent, E : GenericComponentInteractionCreateEvent>(override val name: String, override val localization: LocalizationFile?) : Element, IMessageComponent {
    override fun children() = listOf(this)

    abstract fun handle(context: ComponentContext<*, E>)
    open fun finalize(component: C?): C? = component

    override fun toString(): String = "MessageElement[$name]"
}

internal fun <C : ActionComponent, E : GenericComponentInteractionCreateEvent> createElement(
    name: String,
    localization: LocalizationFile? = null,
    renderer: (menu: MessageMenu<*, *>, id: IdGenerator) -> List<ActionComponent?>,
    handler: ComponentHandler<*, E>,
    finalizer: C?.() -> C?
): MessageElement<C, E> = object : MessageElement<C, E>(name, localization) {
    override fun handle(context: ComponentContext<*, E>) = context.handler()
    override fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ActionComponent?, MessageElement<*, *>>> = renderer(menu, generator).map { it to this }
    override fun transform(transform: (ActionComponent?) -> ActionComponent?): IMessageComponent = createElement(name, localization, { menu, id -> render(menu, id).map { (component) -> transform(component) } }, handler, finalizer)
    override fun finalize(component: C?): C? = finalizer(component)
}

fun <C : ActionComponent, E : GenericComponentInteractionCreateEvent> element(
    name: String,
    localization: LocalizationFile?,
    render: (menu: MessageMenu<*, *>, id: String) -> ActionComponent?,
    handler: ComponentHandler<*, E>,
    finalizer: C?.() -> C? = { this }
): MessageElement<C, E> = createElement(name, localization, { menu, id -> listOf(render(menu, id.nextId("${menu.name}:$name:"))) }, handler, finalizer)

class MessageComponent(private val children: List<IMessageComponent>) : IMessageComponent {
    override fun children(): List<IMessageComponent> = children
    override fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ActionComponent?, MessageElement<*, *>>> = elements().flatMap { it.render(menu, generator) }
}
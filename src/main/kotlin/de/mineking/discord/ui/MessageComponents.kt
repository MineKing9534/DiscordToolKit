package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.builder.components.BackReference
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

    fun render() = (menuInfo.menu as MessageMenu).render(this)
    fun update() = (menuInfo.menu as MessageMenu).update(this)

    fun cloneMenu(ephemeral: Boolean = true) =
        if (isAcknowledged) hook.sendMessage(render().toCreateData()).setEphemeral(ephemeral).queue()
        else reply(render().toCreateData()).setEphemeral(ephemeral).queue()

    fun <N> switchMenu(menu: Menu<N, *, *>, builder: StateBuilderConfig = DEFAULT_STATE_BUILDER) {
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

    operator fun BackReference.invoke(state: StateBuilderConfig = {
        copy(stateCount)
        pushDefaults()
    }) = switchMenu(menu.menu, state)
}

fun createMessageComponent(components: List<IMessageComponent>) = MessageComponent(components)
fun createMessageComponent(vararg components: IMessageComponent) = createMessageComponent(components.toList())

typealias ComponentProvider = () -> ActionComponent?

interface IMessageComponent {
    fun children(): List<IMessageComponent>
    fun elements(): List<MessageElement<*, *>> = children().flatMap { if (it is MessageElement<*, *>) listOf(it) else it.elements() }

    fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ComponentProvider, MessageElement<*, *>>>
    fun transform(transform: (component: ComponentProvider) -> ComponentProvider): IMessageComponent = object : IMessageComponent {
        override fun children() = this@IMessageComponent.children()
        override fun elements() = this@IMessageComponent.elements()

        override fun render(menu: MessageMenu<*, *>, generator: IdGenerator) = this@IMessageComponent.render(menu, generator).map { (component, element) -> transform(component) to element }

        override fun format() = this@IMessageComponent.format()
    }

    fun disabled(state: Boolean = true) = transform { { it()?.let { it.withDisabled(state || it.isDisabled) } } }
    fun enabled(state: Boolean = true) = disabled(!state)

    fun hide(hide: Boolean = true) = show(!hide)
    fun show(show: Boolean = true) = if (show) this else transform { { null } }

    fun format() = children().toString()
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
    renderer: (menu: MessageMenu<*, *>, id: IdGenerator) -> List<ComponentProvider>,
    handler: ComponentHandler<*, E>,
    finalizer: C?.() -> C?
): MessageElement<C, E> = object : MessageElement<C, E>(name, localization) {
    override fun handle(context: ComponentContext<*, E>) = context.handler()
    override fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ComponentProvider, MessageElement<*, *>>> = renderer(menu, generator).map { it to this }
    override fun transform(transform: (component: ComponentProvider) -> ComponentProvider): IMessageComponent = createElement(name, localization, { menu, id -> render(menu, id).map { (component) -> transform(component) } }, handler, finalizer)
    override fun finalize(component: C?): C? = finalizer(component)
}

fun <C : ActionComponent, E : GenericComponentInteractionCreateEvent> element(
    name: String,
    localization: LocalizationFile?,
    render: (menu: MessageMenu<*, *>, id: String) -> ActionComponent?,
    handler: ComponentHandler<*, E>,
    finalizer: C?.() -> C? = { this }
): MessageElement<C, E> = createElement(name, localization, { menu, id -> listOf({ render(menu, id.nextId("${menu.name}:$name:")) }) }, handler, finalizer)

class MessageComponent(private val children: List<IMessageComponent>) : IMessageComponent {
    override fun children(): List<IMessageComponent> = children
    override fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ComponentProvider, MessageElement<*, *>>> = elements().flatMap { it.render(menu, generator) }
}
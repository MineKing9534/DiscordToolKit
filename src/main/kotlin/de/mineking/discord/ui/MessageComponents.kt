package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.builder.components.BackReference
import net.dv8tion.jda.api.components.ActionComponent
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.attribute.IDisableable
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent

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

fun <C : Component> createMessageComponent(vararg components: MessageComponent<C>) = createMessageComponent(components.toList())
fun <C : Component> createMessageComponent(components: List<MessageComponent<C>>) = object : MessageComponent<C> {
    override fun elements() = components.flatMap { it.elements() }
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = components.flatMap { it.render(config, generator) }
}

interface MessageComponent<C : Component> : IComponent<C> {
    fun elements(): List<MessageElement<*>>

    override fun transform(mapper: (() -> List<C>) -> List<C>) = object : MessageComponent<C> {
        override fun elements() = this@MessageComponent.elements()
        override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper { this@MessageComponent.render(config, generator) }

        override fun toString() = this@MessageComponent.toString()
    }
}

open class MessageElement<C : Component>(
    val name: String, val localization: LocalizationFile? = null,
    val renderer: (MenuConfig<*, *>, String) -> C?
) : MessageComponent<C> {
    override fun elements() = listOf(this)
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderer(config, generator.nextId(name)).let { if (it != null) listOf(it) else emptyList() }

    override fun toString() = "MessageElement[$name]"
}

abstract class ActionMessageElement<C : Component, E : GenericComponentInteractionCreateEvent>(
    name: String, localization: LocalizationFile? = null,
    renderer: (MenuConfig<*, *>, String) -> C?
) : MessageElement<C>(name, localization, renderer) {
    abstract fun handle(context: ComponentContext<*, E>)
}

fun <C : Component> createElement(
    name: String,
    localization: LocalizationFile?,
    render: (MenuConfig<*, *>, String) -> C?
) = MessageElement(name, localization, render)

fun <C : Component, E : GenericComponentInteractionCreateEvent> createActionElement(
    name: String,
    localization: LocalizationFile?,
    handler: ComponentHandler<*, E> = {},
    render: (MenuConfig<*, *>, String) -> C?
) = object : ActionMessageElement<C, E>(name, localization, render) {
    override fun handle(context: ComponentContext<*, E>) = handler(context)
}

fun <C : Component> createLayout(children: List<MessageComponent<*>> = emptyList(), renderer: (MenuConfig<*, *>, IdGenerator) -> C) = object : MessageComponent<C> {
    override fun elements() = children.flatMap { it.elements() }
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = listOf(renderer(config, generator))

    override fun toString() = "LayoutComponent[$children]"
}
package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.builder.IMessage
import de.mineking.discord.ui.builder.Message
import de.mineking.discord.ui.builder.components.BREAKPOINT
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import kotlin.reflect.KType

fun IMessageEditCallback.disableComponents(message: net.dv8tion.jda.api.entities.Message) = editComponents(message.components.map { ActionRow.of(it.actionComponents.map { it.asDisabled() }) })

const val MAX_COMPONENTS = 5

@Suppress("UNCHECKED_CAST")
fun renderMessageComponents(id: IdGenerator, config: MessageMenuConfigImpl<*, *>, force: Boolean = false) = config.components
    .map { if (force) it.show() else it }
    .flatMap {
        try {
            it.render(config.menuInfo.menu as MessageMenu, id)
        } catch (e: Exception) {
            throw RuntimeException("Error rendering component ${it.format()}", e)
        }
    }
    .mapNotNull { (component, element) -> (element as MessageElement<ActionComponent, *>).finalize(component)?.let { element to it } }
    .map { (element, component) -> config.menuInfo.manager.localization.localize(config, element, component) }

class MessageMenu<M, L : LocalizationFile?>(
    manager: UIManager, name: String, defer: DeferMode,
    localization: L,
    setup: List<*>,
    states: List<InternalState<*>>,
    private val config: LocalizedMessageMenuConfigurator<M, L>
) : Menu<M, GenericComponentInteractionCreateEvent, L>(manager, name, defer, localization, setup, states) {
    private fun buildComponents(renderer: MessageMenuConfigImpl<M, L>): List<ActionRow> {
        val generator = IdGenerator(renderer.stateData.encode())

        @Suppress("UNCHECKED_CAST")
        val components = renderMessageComponents(generator, renderer)

        val left = generator.left()
        if (left != 0) error("Not enough component id space to store state. $left characters left")

        val temp = mutableListOf<ActionComponent>()
        var currentSize = 0

        val rows = mutableListOf<ActionRow>()

        for (component in components) {
            val size = 6 - component.maxPerRow

            if (component == BREAKPOINT || currentSize + size > MAX_COMPONENTS && temp.isNotEmpty()) {
                rows += ActionRow.of(temp)

                temp.clear()
                currentSize = 0
            }

            if (component != BREAKPOINT) {
                temp += component
                currentSize += size
            }
        }

        if (temp.isNotEmpty()) rows += ActionRow.of(temp)

        return rows
    }

    fun render(state: StateContext<M>): MessageEditData {
        @Suppress("UNCHECKED_CAST")
        val renderer = MessageMenuConfigImpl(MenuConfigPhase.RENDER, state, info, localization, config)
        renderer.config(localization)

        return renderer.build()
            .setComponents(buildComponents(renderer))
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    override fun handle(event: GenericComponentInteractionCreateEvent) {
        if (defer == DeferMode.ALWAYS) event.disableComponents(event.message).queue()

        val name = event.componentId.split(":", limit = 3)[1]

        val data = event.message.decodeState()
        val context = ComponentContext(info, StateData.decode(data), event)

        val renderer = MessageMenuConfigImpl(MenuConfigPhase.COMPONENTS, context, info, localization, config)
        renderer.config(localization)

        val element = renderer.components.flatMap { it.elements() }.firstOrNull { it.name == name } as MessageElement<*, GenericComponentInteractionCreateEvent>? ?: error("Component $name not found")

        try {
            element.handle(context)
        } catch (_: RenderTermination) {
            return
        }

        val newData = context.stateData.encode()
        if (context.update != false) {
            //Rerender if: state changed, update forced, or we deferred before
            if (data != newData || context.update == true || defer == DeferMode.ALWAYS) context.update()
            else if (!context.event.isAcknowledged) context.defer()
        }

        context.after.forEach { it() }
    }

    fun update(context: HandlerContext<M, *>) {
        try {
            if (defer != DeferMode.NEVER) {
                if (defer == DeferMode.UNLESS_PREVENTED && !context.event.isAcknowledged) context.disableComponents(context.message).queue()
                context.hook.editOriginal(render(context)).queue()
            } else context.editMessage(render(context)).queue()
        } catch (_: RenderTermination) {
            if (!context.event.isAcknowledged) context.deferEdit().queue()
        }
    }

    fun createInitial(param: M): MessageEditData = render(SendState(info, StateData.createInitial(states), param))
}

fun MessageEditData.toCreateData() = MessageCreateData.fromEditData(this)

fun MessageChannel.sendMenu(menu: MessageMenu<Unit, *>) = sendMenu(menu, Unit)
fun <C : MessageChannel> C.sendChannelMenu(menu: MessageMenu<in C, *>) = sendMenu(menu, this)
fun <M> MessageChannel.sendMenu(menu: MessageMenu<in M, *>, param: M) = sendMessage(menu.createInitial(param).toCreateData())

fun IReplyCallback.replyMenu(menu: MessageMenu<Unit, *>, ephemeral: Boolean = true) = replyMenu(menu, Unit, ephemeral)
fun IReplyCallback.replyChannelMenu(menu: MessageMenu<in MessageChannel, *>, ephemeral: Boolean = true) = replyMenu(menu, messageChannel, ephemeral)
fun <C : IReplyCallback> C.replyEventMenu(menu: MessageMenu<in C, *>, ephemeral: Boolean = true) = replyMenu(menu, this, ephemeral)
fun <M> IReplyCallback.replyMenu(menu: MessageMenu<in M, *>, param: M, ephemeral: Boolean = true): RestAction<*> =
    if (isAcknowledged) hook.sendMessage(menu.createInitial(param).toCreateData()).setEphemeral(true)
    else reply(menu.createInitial(param).toCreateData()).setEphemeral(ephemeral)

typealias JDAMessage = net.dv8tion.jda.api.entities.Message

fun JDAMessage.decodeState() = components.flatMap { it.components.filterIsInstance<ActionComponent>().map { it.id } }.filterNotNull().joinToString("") { it.split(":", limit = 3)[2] }
fun <M, E> JDAMessage.rerender(menu: MessageMenu<M, *>, event: E): RestAction<*> where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback {
    val context = TransferContext(menu.info, StateData.decode(decodeState()), event, this)
    return editMessage(menu.render(context))
}

typealias MessageMenuConfigurator<M> = MessageMenuConfig<M, *>.() -> Unit
typealias LocalizedMessageMenuConfigurator<M, L> = MessageMenuConfig<M, L>.(localization: L) -> Unit

interface MessageMenuConfig<M, L : LocalizationFile?> : MenuConfig<M, L>, IMessage {
    operator fun IMessageComponent.unaryPlus()
    fun render(handler: IMessage.() -> Unit)

    fun <L : LocalizationFile?> localizedSubmenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: L, detach: Boolean = false, init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L>
    fun submenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: LocalizationFile? = null, detach: Boolean = false, init: MessageMenuConfigurator<M>): MessageMenu<M, LocalizationFile?> {
        return localizedSubmenu(name, defer, localization, detach) { init() }
    }

    fun <L : LocalizationFile?> localizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: L, detach: Boolean = false, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L>
    fun modal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: LocalizationFile? = null, detach: Boolean = false, init: ModalConfigurator<M>): ModalMenu<M, LocalizationFile?> {
        return localizedModal(name, defer, localization, detach) { init() }
    }
}

inline fun <M, reified L : LocalizationFile> MessageMenuConfig<M, *>.localizedSubmenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, detach: Boolean = false, noinline init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
    val file = menuInfo.manager.manager.localizationManager.read<L>()
    return localizedSubmenu(name, defer, file, detach, init)
}

inline fun <M, reified L : LocalizationFile> MessageMenuConfig<M, *>.localizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, detach: Boolean = false, noinline init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
    val file = menuInfo.manager.manager.localizationManager.read<L>()
    return localizedModal(name, defer, file, detach, init)
}

internal fun String.menuName() = substring(lastIndexOf('.') + 1)

open class MessageMenuConfigImpl<M, L : LocalizationFile?>(
    phase: MenuConfigPhase,
    state: StateContext<M>?,
    menu: MenuInfo<M>,
    val localization: L,
    val config: LocalizedMessageMenuConfigurator<M, L>
) : MenuConfigImpl<M, L>(phase, state, menu), MessageMenuConfig<M, L>, IMessage by Message() {
    private companion object {
        val TARGET_CONFIG = ThreadLocal<Any>()

        object InternalTermination : RuntimeException() {
            private fun readResolve(): Any = InternalTermination
        }

        fun end(config: Any): Nothing {
            TARGET_CONFIG.set(config)
            throw InternalTermination
        }
    }

    internal val components = mutableListOf<IMessageComponent>()

    override fun IMessageComponent.unaryPlus() {
        components += this
    }

    override fun render(handler: IMessage.() -> Unit) {
        if (phase == MenuConfigPhase.RENDER) handler()
    }

    override fun <CL : LocalizationFile?> localizedSubmenu(name: String, defer: DeferMode, localization: CL, detach: Boolean, init: LocalizedMessageMenuConfigurator<M, CL>): MessageMenu<M, CL> {
        @Suppress("UNCHECKED_CAST")
        return setup {
            menuInfo.manager.registerLocalizedMenu<M, CL>(
                "${menuInfo.name}.$name", defer, localization ?: this.localization as CL, if (detach) init
                else { localization ->
                    require(this is MessageMenuConfigImpl)

                    try {
                        val context = object : MessageMenuConfigImpl<M, L>(phase, state, this@MessageMenuConfigImpl.menuInfo, this@MessageMenuConfigImpl.localization, this@MessageMenuConfigImpl.config) {
                            var currentSetup = 0

                            override fun render(handler: IMessage.() -> Unit) {}

                            override fun <L : LocalizationFile?> localizedSubmenu(name: String, defer: DeferMode, localization: L, detach: Boolean, init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
                                if (this@registerLocalizedMenu.menuInfo.name.menuName() == name) end(init)
                                return super.localizedSubmenu(name, defer, localization, detach, init)
                            }

                            @Suppress("UNCHECKED_CAST")
                            override fun <T> setup(value: () -> T): T = this@MessageMenuConfigImpl.setup[currentSetup++] as T

                            override val stateData: StateData = this@registerLocalizedMenu.stateData

                            override fun currentState(): Int = this@registerLocalizedMenu.currentState()
                            override fun skipState(amount: Int) = this@registerLocalizedMenu.skipState(amount)
                            override fun <T> state(type: KType, initial: T, handler: StateHandler<T>?): State<T> = this@registerLocalizedMenu.state(type, initial, handler)

                            override fun localize(locale: DiscordLocale, init: LocalizationConfig.() -> Unit) {
                                super.localize(locale, init)
                                this@registerLocalizedMenu.localize(locale, init)
                            }
                        }

                        this@MessageMenuConfigImpl.config.invoke(context, this@MessageMenuConfigImpl.localization)

                        error("Unable to match parent render to child entrypoint")
                    } catch (_: InternalTermination) {
                        @Suppress("UNCHECKED_CAST")
                        (TARGET_CONFIG.get() as LocalizedMessageMenuConfigurator<M, CL>).invoke(this, localization)
                    }
                })
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <CL : LocalizationFile?> localizedModal(name: String, defer: DeferMode, localization: CL, detach: Boolean, init: LocalizedModalConfigurator<M, CL>): ModalMenu<M, CL> {
        return setup {
            menuInfo.manager.registerLocalizedModal(
                "${menuInfo.name}.$name", defer, localization ?: this.localization as CL, if (detach) init
                else { localization ->
                    require(this is ModalConfigImpl)

                    try {
                        val context = object : MessageMenuConfigImpl<M, L>(phase, state, this@MessageMenuConfigImpl.menuInfo, this@MessageMenuConfigImpl.localization, this@MessageMenuConfigImpl.config) {
                            var currentSetup = 0

                            override fun render(handler: IMessage.() -> Unit) {}

                            override fun <L : LocalizationFile?> localizedModal(name: String, defer: DeferMode, localization: L, detach: Boolean, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
                                if (this@registerLocalizedModal.menuInfo.name.menuName() == name) end(init)
                                return super.localizedModal(name, defer, localization, detach, init)
                            }

                            @Suppress("UNCHECKED_CAST")
                            override fun <T> setup(value: () -> T): T = this@MessageMenuConfigImpl.setup[currentSetup++] as T

                            override val stateData: StateData = this@registerLocalizedModal.stateData

                            override fun currentState(): Int = this@registerLocalizedModal.currentState()
                            override fun skipState(amount: Int) = this@registerLocalizedModal.skipState(amount)
                            override fun <T> state(type: KType, initial: T, handler: StateHandler<T>?): State<T> = this@registerLocalizedModal.state(type, initial, handler)

                            override fun localize(locale: DiscordLocale, init: LocalizationConfig.() -> Unit) {
                                super.localize(locale, init)
                                this@registerLocalizedModal.localize(locale, init)
                            }
                        }

                        this@MessageMenuConfigImpl.config.invoke(context, this@MessageMenuConfigImpl.localization)

                        error("Unable to match parent render to child entrypoint")
                    } catch (_: InternalTermination) {
                        @Suppress("UNCHECKED_CAST")
                        (TARGET_CONFIG.get() as LocalizedModalConfigurator<M, CL>).invoke(this, localization)
                    }
                })
        }
    }
}
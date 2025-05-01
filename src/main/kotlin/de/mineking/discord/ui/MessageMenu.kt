package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.builder.IMessage
import de.mineking.discord.ui.builder.Message
import net.dv8tion.jda.api.components.ActionComponent
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.attribute.IDisableable
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import kotlin.reflect.KProperty
import kotlin.reflect.KType

fun IMessageEditCallback.disableComponents(message: net.dv8tion.jda.api.entities.Message) = editComponents(message.components.map {
    if (it is IDisableable) it.asDisabled() as MessageTopLevelComponent
    else it
})

@Suppress("UNCHECKED_CAST")
fun renderMessageComponents(id: IdGenerator, config: MessageMenuConfigImpl<*, *>, force: Boolean = false) = config.components
    .map { if (force) it.show() else it }
    .flatMap {
        try {
            it.render(config, id)
        } catch (e: Exception) {
            throw RuntimeException("Error rendering component $it", e)
        }
    }

class MessageMenu<M, L : LocalizationFile?>(
    manager: UIManager, name: String, defer: DeferMode,
    localization: L,
    setup: List<*>,
    states: List<InternalState<*>>,
    private val config: LocalizedMessageMenuConfigurator<M, L>
) : Menu<M, GenericComponentInteractionCreateEvent, L>(manager, name, defer, localization, setup, states) {
    private fun buildComponents(renderer: MessageMenuConfigImpl<M, L>): List<MessageTopLevelComponent> {
        val generator = IdGenerator(renderer.stateData.encode())

        @Suppress("UNCHECKED_CAST")
        val components = renderMessageComponents(generator, renderer)

        val left = generator.left()
        if (left != 0) error("Not enough component id space to store state. $left characters left")

        return components
    }

    fun render(state: StateContext<M>): MessageEditData {
        @Suppress("UNCHECKED_CAST")
        val renderer = MessageMenuConfigImpl(MenuConfigPhase.RENDER, state, info, localization, config)
        renderer.config(localization)

        return renderer.build()
            .useComponentsV2()
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

        renderer.activate() //Activate lazy values => Allow them to load in the handler
        val element = renderer.components.flatMap { it.elements() }.firstOrNull { it.name == name } as ActionMessageElement<*, GenericComponentInteractionCreateEvent>? ?: error("Component $name not found")

        try {
            element.handle(context)
        } catch (_: RenderTermination) {
            return
        }

        val newData = context.stateData.encode()
        if (context.update != false) {
            //Rerender if: state changed, update forced
            if (data != newData || context.update == true) context.update()

            //Recreate previous state if we deferred but state didn't change
            else if (defer == DeferMode.ALWAYS) event.hook.editOriginal(MessageEditData.fromMessage(event.message)).queue()

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

fun JDAMessage.decodeState() = componentTree.findAll(ActionComponent::class.java).mapNotNull { it.customId }.joinToString("") { it.split(":", limit = 3)[2] }
fun <M, E> JDAMessage.rerender(menu: MessageMenu<M, *>, event: E): RestAction<*> where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback {
    val context = TransferContext(menu.info, StateData.decode(decodeState()), event, this)
    return editMessage(menu.render(context))
}

typealias MessageMenuConfigurator<M> = MessageMenuConfig<M, *>.() -> Unit
typealias LocalizedMessageMenuConfigurator<M, L> = MessageMenuConfig<M, L>.(localization: L) -> Unit

class Lazy<T>(var active: Boolean = false, val default: T, provider: () -> T) {
    private val _value by lazy(provider)

    val value get() = if (active) _value else default
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value
}

interface MessageMenuConfig<M, L : LocalizationFile?> : MenuConfig<M, L>, IMessage {
    operator fun MessageComponent<out MessageTopLevelComponent>.unaryPlus()

    fun <T> lazy(default: T, provider: () -> T): Lazy<T>
    fun <T> lazy(provider: () -> T) = lazy(null, provider)

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

    internal val components = mutableListOf<MessageComponent<out MessageTopLevelComponent>>()

    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() {
        components += this
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

                            override fun <T> render(default: T, handler: () -> T) = default

                            override fun <T> lazy(default: T, provider: () -> T) = this@registerLocalizedMenu.lazy(default, provider)

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

                            override fun <T> render(default: T, handler: () -> T) = default
                            override fun <T> lazy(default: T, provider: () -> T) = this@registerLocalizedModal.lazy(default, provider)

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
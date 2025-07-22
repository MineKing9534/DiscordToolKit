package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.builder.IMessage
import de.mineking.discord.ui.builder.Message
import de.mineking.discord.ui.builder.components.statefulMultiEnumSelect
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
}).useComponentsV2(message.isUsingComponentsV2)

@Suppress("UNCHECKED_CAST")
suspend fun renderMessageComponents(id: IdGenerator, config: MessageMenuConfigImpl<*, *>, force: Boolean = false) = config.components
    .map { if (force) it.show() else it }
    .flatMap {
        try {
            it.render(config, id)
        } catch (e: Exception) {
            throw RuntimeException("Error rendering component $it", e)
        }
    }

interface MessageMenuHandler {
    suspend fun <M, L : LocalizationFile?> handleComponent(state: ComponentContext<M, *>, oldState: String, name: String)
    suspend fun <M, L : LocalizationFile?> render(state: StateContext<M>): MessageEditData

    companion object {
        val DEFAULT = object : MessageMenuHandler {
            override suspend fun <M, L : LocalizationFile?> handleComponent(state: ComponentContext<M, *>, oldState: String, name: String) {
                @Suppress("UNCHECKED_CAST")
                val menu = state.menuInfo.menu as MessageMenu<M, L>

                val renderer = MessageMenuComponentFinder(name, state, state.menuInfo, menu.localization, menu.config)

                try {
                    menu.config(renderer, menu.localization)
                    error("Component $name not found")
                } catch (_: ComponentFinderResult) {}

                try {
                    renderer.activate() //Activate lazy values => Allow them to load in the handler
                    renderer.execute(state)
                } catch (_: RenderTermination) {
                    return
                }

                val newState = state.stateData.encode()
                if (state.update != false) {
                    //Rerender if: state changed, update forced
                    if (oldState != newState || state.update == true) state.update()

                    //Recreate previous state if we deferred but state didn't change
                    else if (state.menuInfo.menu.defer == DeferMode.ALWAYS) state.hook.editOriginal(MessageEditData.fromMessage(state.message)).queue()

                    else if (!state.isAcknowledged) state.defer()
                }

                state.after.forEach { it() }
            }

            override suspend fun <M, L : LocalizationFile?> render(state: StateContext<M>): MessageEditData {
                @Suppress("UNCHECKED_CAST")
                val menu = state.menuInfo.menu as MessageMenu<M, L>

                @Suppress("UNCHECKED_CAST")
                val renderer = MessageMenuConfigImpl(MenuConfigPhase.RENDER, state, state.menuInfo, menu.localization, menu.config)
                menu.config(renderer, menu.localization)

                return renderer.build()
                    .useComponentsV2(menu.useComponentsV2)
                    .setComponents(menu.buildComponents(renderer))
                    .build()
            }
        }
    }
}

inline fun <reified E: Throwable> MessageMenuHandler.handleException(
    crossinline component: suspend ComponentContext<*, *>.(E) -> Unit,
    crossinline render: suspend StateContext<*>.(E) -> MessageEditData
) = object : MessageMenuHandler {
    override suspend fun <M, L : LocalizationFile?> handleComponent(state: ComponentContext<M, *>, oldState: String, name: String) = try {
        this@handleException.handleComponent<M, L>(state, oldState, name)
    } catch (e: Throwable) {
        if (e !is E) throw e
        try {
            component(state, e)
        } catch (_: RenderTermination) {}
    }

    override suspend fun <M, L : LocalizationFile?> render(state: StateContext<M>): MessageEditData = try {
        this@handleException.render<M, L>(state)
    } catch (e: Throwable) {
        if (e !is E) throw e
        render(state, e)
    }
}

class MessageMenu<M, L : LocalizationFile?>(
    manager: UIManager, name: String, defer: DeferMode,
    val useComponentsV2: Boolean,
    localization: L,
    setup: List<*>,
    states: List<InternalState<*>>,
    val config: LocalizedMessageMenuConfigurator<M, L>,
    val handler: MessageMenuHandler
) : Menu<M, GenericComponentInteractionCreateEvent, L>(manager, name, defer, localization, setup, states) {
    suspend fun buildComponents(renderer: MessageMenuConfigImpl<M, L>): List<MessageTopLevelComponent> {
        val generator = IdGenerator(renderer.stateData.encode())

        @Suppress("UNCHECKED_CAST")
        val components = renderMessageComponents(generator, renderer)

        val left = generator.left()
        if (left != 0) error("Not enough component id space to store state. $left characters left")

        return components
    }

    suspend fun render(state: StateContext<M>) = handler.render<M, L>(state)

    @Suppress("UNCHECKED_CAST")
    override suspend fun handle(event: GenericComponentInteractionCreateEvent) {
        if (defer == DeferMode.ALWAYS) event.disableComponents(event.message).queue()

        val name = event.componentId.split(":", limit = 3)[1]

        val data = event.message.decodeState()
        val context = ComponentContext(info, StateData.decode(data), event)

        handler.handleComponent<M, L>(context, data, name)
    }

    suspend fun update(context: HandlerContext<M, *>) {
        try {
            if (defer != DeferMode.NEVER) {
                if (defer == DeferMode.UNLESS_PREVENTED && !context.isAcknowledged) context.disableComponents(context.message).queue()
                context.hook.editOriginal(render(context)).queue()
            } else if (!context.isAcknowledged) context.editMessage(render(context)).queue()
            else context.hook.editOriginal(render(context)).queue()
        } catch (_: RenderTermination) {
            if (!context.event.isAcknowledged) context.deferEdit().queue()
        }
    }

    suspend fun createInitial(param: M): MessageEditData = render(SendState(info, StateData.createInitial(states), param))
}

fun MessageEditData.toCreateData() = MessageCreateData.fromEditData(this)

suspend fun MessageChannel.sendMenu(menu: MessageMenu<Unit, *>) = sendMenu(menu, Unit)
suspend fun <C : MessageChannel> C.sendChannelMenu(menu: MessageMenu<in C, *>) = sendMenu(menu, this)
suspend fun <M> MessageChannel.sendMenu(menu: MessageMenu<in M, *>, param: M) = sendMessage(menu.createInitial(param).toCreateData())

suspend fun IReplyCallback.replyMenu(menu: MessageMenu<Unit, *>, ephemeral: Boolean = true) = replyMenu(menu, Unit, ephemeral)
suspend fun IReplyCallback.replyChannelMenu(menu: MessageMenu<in MessageChannel, *>, ephemeral: Boolean = true) = replyMenu(menu, messageChannel, ephemeral)
suspend fun <C : IReplyCallback> C.replyEventMenu(menu: MessageMenu<in C, *>, ephemeral: Boolean = true) = replyMenu(menu, this, ephemeral)
suspend fun <M> IReplyCallback.replyMenu(menu: MessageMenu<in M, *>, param: M, ephemeral: Boolean = true): RestAction<*> =
    if (isAcknowledged) hook.sendMessage(menu.createInitial(param).toCreateData()).setEphemeral(true)
    else reply(menu.createInitial(param).toCreateData()).setEphemeral(ephemeral)

typealias JDAMessage = net.dv8tion.jda.api.entities.Message

fun JDAMessage.decodeState() = componentTree.findAll(ActionComponent::class.java).mapNotNull { it.customId }.decodeState(3)

fun <M, E> JDAMessage.rerenderBlocking(menu: MessageMenu<M, *>, event: E): RestAction<*> where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback = runBlocking {
    rerender(menu, event)
}

suspend fun <M, E> JDAMessage.rerender(menu: MessageMenu<M, *>, event: E): RestAction<*> where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback {
    val context = TransferContext(menu.info, StateData.decode(decodeState()), event, this)
    return editMessage(menu.render(context))
}

typealias MessageMenuConfigurator<M> = suspend MessageMenuConfig<M, *>.() -> Unit
typealias LocalizedMessageMenuConfigurator<M, L> = suspend MessageMenuConfig<M, L>.(localization: L) -> Unit

fun interface Lazy<out T> {
    suspend fun getValue(): T

    val value get() = runBlocking { getValue() }
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value
}

class MenuLazyImpl<out T>(val menu: MenuInfo<*>, var active: Boolean = false, val default: T, provider: suspend () -> T) : Lazy<T> {
    private val _value = menu.manager.manager.coroutineScope.async(start = CoroutineStart.LAZY) { provider() }

    override suspend fun getValue() = if (active) _value.await() else default
}

interface MessageMenuConfig<M, L : LocalizationFile?> : MenuConfig<M, L>, IMessage {
    fun <C : MessageComponent<*>> register(component: C): C
    operator fun MessageComponent<out MessageTopLevelComponent>.unaryPlus()

    fun <T> lazy(default: T, provider: suspend () -> T): Lazy<T>
    fun <T> lazy(provider: suspend () -> T) = lazy(null, provider)

    suspend fun <L : LocalizationFile?> localizedSubmenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, localization: L, detach: Boolean = false, init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L>
    suspend fun submenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, localization: LocalizationFile? = null, detach: Boolean = false, init: MessageMenuConfigurator<M>): MessageMenu<M, LocalizationFile?> {
        return localizedSubmenu(name, defer, useComponentsV2, localization, detach) { init() }
    }

    suspend fun <L : LocalizationFile?> localizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: L, detach: Boolean = false, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L>
    suspend fun modal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: LocalizationFile? = null, detach: Boolean = false, init: ModalConfigurator<M>): ModalMenu<M, LocalizationFile?> {
        return localizedModal(name, defer, localization, detach) { init() }
    }
}

suspend inline fun <M, reified L : LocalizationFile> MessageMenuConfig<M, *>.localizedSubmenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, detach: Boolean = false, noinline init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
    val file = menuInfo.manager.manager.localizationManager.read<L>()
    return localizedSubmenu(name, defer, useComponentsV2, file, detach, init)
}

suspend inline fun <M, reified L : LocalizationFile> MessageMenuConfig<M, *>.localizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, detach: Boolean = false, noinline init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
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

    override fun <C : MessageComponent<*>> register(component: C) = component
    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() {
        components += this
    }

    override suspend fun <CL : LocalizationFile?> localizedSubmenu(name: String, defer: DeferMode, useComponentsV2: Boolean?, localization: CL, detach: Boolean, init: LocalizedMessageMenuConfigurator<M, CL>): MessageMenu<M, CL> {
        @Suppress("UNCHECKED_CAST")
        return setup {
            menuInfo.manager.registerLocalizedMenu<M, CL>(
                "${menuInfo.name}.$name", defer, useComponentsV2, localization ?: this.localization as CL, null, if (detach) init
                else { localization ->
                    require(this is MessageMenuConfigImpl)

                    try {
                        val context = object : MessageMenuConfigImpl<M, L>(phase, state, this@MessageMenuConfigImpl.menuInfo, this@MessageMenuConfigImpl.localization, this@MessageMenuConfigImpl.config) {
                            var currentSetup = 0

                            override suspend fun render(handler: suspend () -> Unit) {}

                            override fun <T> lazy(default: T, provider: suspend () -> T) = this@registerLocalizedMenu.lazy(default, provider)

                            override suspend fun <L : LocalizationFile?> localizedSubmenu(name: String, defer: DeferMode, useComponentsV2: Boolean?, localization: L, detach: Boolean, init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
                                if (this@registerLocalizedMenu.menuInfo.name.menuName() == name) end(init)
                                return super.localizedSubmenu(name, defer, useComponentsV2, localization, detach, init)
                            }

                            @Suppress("UNCHECKED_CAST")
                            override suspend fun <T> setup(value: suspend () -> T): T = this@MessageMenuConfigImpl.setup[currentSetup++] as T

                            override val stateData: StateData = this@registerLocalizedMenu.stateData

                            override fun currentState(): Int = this@registerLocalizedMenu.currentState()
                            override fun skipState(amount: Int) = this@registerLocalizedMenu.skipState(amount)
                            override fun <T> state(type: KType, initial: T, handler: StateHandler<T>?): State<T> = this@registerLocalizedMenu.state(type, initial, handler)

                            override suspend fun localize(locale: DiscordLocale, init: suspend LocalizationConfig.() -> Unit) {
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
    override suspend fun <CL : LocalizationFile?> localizedModal(name: String, defer: DeferMode, localization: CL, detach: Boolean, init: LocalizedModalConfigurator<M, CL>): ModalMenu<M, CL> {
        return setup {
            menuInfo.manager.registerLocalizedModal(
                "${menuInfo.name}.$name", defer, localization ?: this.localization as CL, null, if (detach) init
                else { localization ->
                    require(this is ModalConfigImpl)

                    try {
                        val context = object : MessageMenuConfigImpl<M, L>(phase, state, this@MessageMenuConfigImpl.menuInfo, this@MessageMenuConfigImpl.localization, this@MessageMenuConfigImpl.config) {
                            var currentSetup = 0

                            override suspend fun render(handler: suspend () -> Unit) {}
                            override fun <T> lazy(default: T, provider: suspend () -> T) = this@registerLocalizedModal.lazy(default, provider)

                            override suspend fun <L : LocalizationFile?> localizedModal(name: String, defer: DeferMode, localization: L, detach: Boolean, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
                                if (this@registerLocalizedModal.menuInfo.name.menuName() == name) end(init)
                                return super.localizedModal(name, defer, localization, detach, init)
                            }

                            @Suppress("UNCHECKED_CAST")
                            override suspend fun <T> setup(value: suspend () -> T): T = this@MessageMenuConfigImpl.setup[currentSetup++] as T

                            override val stateData: StateData = this@registerLocalizedModal.stateData

                            override fun currentState(): Int = this@registerLocalizedModal.currentState()
                            override fun skipState(amount: Int) = this@registerLocalizedModal.skipState(amount)
                            override fun <T> state(type: KType, initial: T, handler: StateHandler<T>?): State<T> = this@registerLocalizedModal.state(type, initial, handler)

                            override suspend fun localize(locale: DiscordLocale, init: suspend LocalizationConfig.() -> Unit) {
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

class MessageMenuComponentFinder<M, L : LocalizationFile?>(
    val component: String,
    state: StateContext<M>?,
    menu: MenuInfo<M>,
    localization: L,
    config: LocalizedMessageMenuConfigurator<M, L>
) : MessageMenuConfigImpl<M, L>(MenuConfigPhase.COMPONENTS, state, menu, localization, config) {
    private var handler: ComponentHandler<*, *>? = null

    private fun findHandler(component: MessageComponent<*>) = component.elements().forEach {
        if (it.name == this.component) {
            @Suppress("UNCHECKED_CAST")
            this@MessageMenuComponentFinder.handler = it.handler as ComponentHandler<*, *>
            throw ComponentFinderResult
        }
    }

    override fun <C : MessageComponent<*>> register(component: C) = component.also { findHandler(component) }
    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() = findHandler(this)

    suspend fun execute(context: ComponentContext<*, *>) {
        handler!!(context)
    }
}

object ComponentFinderResult : RuntimeException() {
    private fun readResolve(): Any = ComponentFinderResult
}
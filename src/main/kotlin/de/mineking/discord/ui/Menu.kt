package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.IntegrationOwners
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.buttons.Button
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@DslMarker
annotation class MenuMarker

enum class DeferMode { NEVER, ALWAYS, UNLESS_PREVENTED }

val DEFAULT_DEFER_MODE = DeferMode.ALWAYS

@MenuMarker
abstract class HandlerContext<M, out E>(
    override val menuInfo: MenuInfo<M>,
    override val stateData: StateData,
    val event: E
) : StateContext<M>, IMessageEditCallback by event, IReplyCallback by event
        where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback
{
    abstract val message: Message

    internal val after: MutableList<() -> Unit> = mutableListOf()

    fun then(handler: () -> Unit) {
        after += handler
    }

    override fun getIdLong(): Long = event.idLong
    override fun getTypeRaw(): Int = event.typeRaw
    override fun getToken(): String = event.token
    override fun getGuild(): Guild? = event.guild
    override fun getUser(): User = event.user
    override fun getMember(): Member? = event.member
    override fun isAcknowledged(): Boolean = event.isAcknowledged
    override fun getChannel(): Channel? = event.channel
    override fun getChannelIdLong(): Long = event.channelIdLong
    override fun getUserLocale(): DiscordLocale = event.userLocale
    override fun getEntitlements(): MutableList<Entitlement> = event.entitlements
    override fun getJDA(): JDA = event.jda
    override fun getHook(): InteractionHook = event.hook
    override fun getContext(): InteractionContextType = event.context
    override fun getIntegrationOwners(): IntegrationOwners = event.integrationOwners
}

@MenuMarker
class TransferContext<M, E>(
    menu: MenuInfo<M>,
    stateData: StateData,
    event: E,
    override val message: Message
) : HandlerContext<M, E>(menu, stateData, event) where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback

sealed interface Element {
    val name: String
    val localization: LocalizationFile?
}

sealed class Menu<M, E : GenericInteractionCreateEvent, L : LocalizationFile?>(
    val manager: UIManager, val name: String, val defer: DeferMode,
    val localization: L,
    val setup: List<*>,
    val states: List<InternalState<*>>
) {
    val info = MenuInfo.create(this)

    abstract fun handle(event: E)
}

class IdGenerator(private val state: String) {
    private var pos = 0

    fun nextId(base: String): String {
        val length = clamp(Button.ID_MAX_LENGTH - base.length, 0, state.length - pos)
        val result = base + state.substring(pos, pos + length)

        pos += length
        return result
    }

    fun left() = state.length - pos
}

fun interface Parameter<T> : () -> T {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = this.invoke()
}

fun <T, U> Parameter<T>.map(mapper: (value: T) -> U): Parameter<U> = Parameter { mapper(this@map()) }

@MenuMarker
interface MenuInfo<M> {
    val name: String
    val manager: UIManager

    val menu: Menu<M, *, *>

    val parentName: String? get() = name.lastIndexOf('.').takeIf { it != -1 }?.let { name.substring(0, it) }
    val parent: Menu<*, *, *>? get() = parentName?.let { manager.menus[it] }

    val parentContext get() = parentName?.let { create<M>(it, manager) }

    companion object {
        fun <M> create(menu: Menu<M, *, *>) = object: MenuInfo<M> {
            override val name: String get() = menu.name
            override val manager: UIManager get() = menu.manager

            override val menu: Menu<M, *, *> get() = menu
        }

        fun <M> create(name: String, manager: UIManager) = object : MenuInfo<M> {
            override val name: String get() = name
            override val manager: UIManager get() = manager

            override val menu: Menu<M, *, *> get() = manager.getMenu<M>(name)
        }
    }
}

fun MenuConfig<out MessageChannel, *>.channel(): Parameter<MessageChannel> = parameter({ it }, { event.messageChannel })
fun MenuConfig<out GenericInteractionCreateEvent, *>.event(): Parameter<GenericInteractionCreateEvent> = parameter({ it }, { event })

enum class MenuConfigPhase { BUILD, COMPONENTS, RENDER }

@MenuMarker
interface MenuConfig<M, L : LocalizationFile?> : StateContext<M>, StateConfig {
    val phase: MenuConfigPhase
    val localizationConfig: LocalizationConfig?

    fun <T> setup(value: () -> T): T
    fun initialize(handler: (param: M) -> Unit)

    fun <T> parameter(initial: (param: M) -> T, render: HandlerContext<M, *>.() -> T): Parameter<T> = parameter({ error("Render-Only param value used during build") }, initial, render)
    fun <T> parameter(default: () -> T, initial: (param: M) -> T, render: HandlerContext<M, *>.() -> T): Parameter<T>

    fun localize(locale: DiscordLocale, init: LocalizationConfig.() -> Unit = {})
}

interface MenuConfigData {
    val states: MutableList<InternalState<*>>
    val setup: MutableList<Any?>
}

sealed class MenuConfigImpl<M, L : LocalizationFile?>(
    override val phase: MenuConfigPhase,
    val state: StateContext<M>?,
    override val menuInfo: MenuInfo<M>
) : MenuConfig<M, L>, MenuConfigData {
    override var localizationConfig: LocalizationConfig? = null

    override val stateData: StateData = state?.stateData ?: StateData(mutableListOf())
    private val stateAccess = stateData?.access() //This not-null check is required to allow stateData to be overridden

    override val states = mutableListOf<InternalState<*>>()

    override val setup = mutableListOf<Any?>()
    private var currentSetup = 0

    override fun currentState(): Int = stateAccess!!.currentState()
    override fun skipState(amount: Int) {
        if (phase == MenuConfigPhase.BUILD) {
            val type = typeOf<Unit?>()

            repeat(amount) {
                states += InternalState(type, null, null)
                stateData.pushInitial(type, null)
            }
        }

        stateAccess!!.skipState(amount)
    }

    override fun <T> state(type: KType, initial: T, handler: StateHandler<T>?): State<T> {
        if (phase == MenuConfigPhase.BUILD) {
            states += InternalState(type, initial, handler)
            stateData.pushInitial(type, initial)
        }

        return stateAccess!!.nextState(type, handler)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> setup(value: () -> T): T =
        if (phase == MenuConfigPhase.BUILD) value().apply { setup += this }
        else menuInfo.menu.setup[currentSetup++] as T

    override fun initialize(handler: (param: M) -> Unit) {
        if (state is SendState<M>) handler(state.param) else Unit
    }

    override fun <T> parameter(default: () -> T, initial: (param: M) -> T, render: HandlerContext<M, *>.() -> T): Parameter<T> = Parameter {
        if (phase == MenuConfigPhase.BUILD) default()
        else if (state is SendState) initial(state.param)
        else if (state is HandlerContext<M, *>) render(state)
        else error("")
    }

    override fun localize(locale: DiscordLocale, init: LocalizationConfig.() -> Unit) {
        localizationConfig = LocalizationConfig(locale).apply(init)
    }
}

object RenderTermination : RuntimeException() {
    private fun readResolve(): Any = RenderTermination
}

fun MenuConfig<*, *>.terminateRender(): Nothing = throw RenderTermination
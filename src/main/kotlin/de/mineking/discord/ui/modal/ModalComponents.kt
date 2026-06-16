package de.mineking.discord.ui.modal

import de.mineking.discord.ui.*
import de.mineking.discord.ui.message.MessageMenu
import de.mineking.discord.ui.message.disableComponents
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.ModalTopLevelComponent
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import kotlin.reflect.full.primaryConstructor

typealias ModalHandler<M> = suspend ModalContext<M>.() -> Unit
typealias ModalResultHandler<T> = suspend ModalContext<*>.(value: T) -> Unit

fun <T, U> ModalResultHandler<T>.map(mapper: (U) -> T): ModalResultHandler<U> = { this@map(mapper(it)) }

class ModalContext<M>(val menu: ModalMenu<M, *>, stateData: StateData, event: ModalInteractionEvent) :
    HandlerContext<M, ModalInteractionEvent>(stateData, event) {
    override val message: Message get() = event.message!!

    suspend fun <N> switchMenu(menu: MessageMenu<N, *>, builder: StateBuilderConfig = DEFAULT_STATE_BUILDER) {
        val state = StateBuilder(this, menu)
        state.builder()

        val context = TransferContext<N, _>(state.build(), event, event.message!!)

        if (menu.defer == DeferMode.ALWAYS) context.disableComponents(context.message).queue()
        menu.update(context)
    }
}

interface ModalComponent<C : Component, T> : IComponent<C> {
    suspend fun handle(context: ModalContext<*>): T

    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<C>) -> List<C>) = object : ModalComponent<C, T> {
        override suspend fun handle(context: ModalContext<*>) = this@ModalComponent.handle(context)
        override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@ModalComponent.render(config, it) }

        override fun toString() = this@ModalComponent.toString()
    }
}

fun <C : Component, T, O> ModalComponent<C, T>.map(handler: suspend ModalContext<*>.(value: T) -> O) = object : ModalComponent<C, O> {
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = this@map.render(config, generator)
    override suspend fun handle(context: ModalContext<*>): O = handler.invoke(context, this@map.handle(context))

    override fun toString() = this@map.toString()
}

interface ModalElement<C : Component, T> : ModalComponent<C, T>, IElement<C>

fun <C : Component, T> createModalElement(
    name: String,
    handler: suspend ModalContext<*>.() -> T,
    renderer: suspend (MenuConfig<*, *>, String) -> C
) = object : ModalElement<C, T> {
    override val name = name

    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = listOf(renderer(config, generator.nextId(config, name)))
    override suspend fun handle(context: ModalContext<*>) = handler.invoke(context)

    override fun toString() = "ModalElement[$name]"
}

fun <C : Component, T, O> ModalElement<C, T>.map(handler: suspend ModalContext<*>.(value: T) -> O): ModalElement<C, O> = object : ModalElement<C, O>, ModalComponent<C, O> by (this as ModalComponent<C, T>).map(handler) {
    override val name = this@map.name
}

fun <C : Component, T> createModalLayoutComponent(
    handler: suspend ModalContext<*>.() -> T,
    renderer: suspend (MenuConfig<*, *>, IdGenerator) -> List<C>
) = object : ModalComponent<C, T> {
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderer(config, generator)
    override fun toString() = "ModalComponent"

    override suspend fun handle(context: ModalContext<*>) = context.handler()
}

sealed interface ModalComponentBuilder<C: Component, T> : MenuCallbackContext {
    operator fun <U> ModalComponent<out C, U>.unaryPlus(): ModalResult<U>
    fun produce(handler: suspend (ModalContext<*>) -> T)
}

private class ModalComponentRenderer<C: Component, T> : ModalComponentBuilder<C, T> {
    override val phase = MenuCallbackPhase.RENDER

    val components = mutableListOf<ModalComponent<out C, *>>()

    override operator fun <U> ModalComponent<out C, U>.unaryPlus(): ModalResult<U> {
        components += this
        return emptyModalResult()
    }

    override fun produce(handler: suspend (ModalContext<*>) -> T) {}
}

private class ModalComponentHandler<C: Component, T>(val context: ModalContext<*>) : ModalComponentBuilder<C, T> {
    override val phase = MenuCallbackPhase.HANDLE

    var producer: (suspend (ModalContext<*>) -> T)? = null

    override operator fun <U> ModalComponent<out C, U>.unaryPlus() = ModalResult { handle(context) }

    override fun produce(handler: suspend (ModalContext<*>) -> T) {
        require(producer == null)
        producer = handler
    }
}

fun <C : Component, T> createModalComponent(config: suspend ModalComponentBuilder<C, T>.() -> Unit) = object : ModalComponent<C, T> {
    private val components = SuspendLazy<_, Unit> { ModalComponentRenderer<C, T>().apply { config() }.components }
    private val producer = SuspendLazy<_, ModalContext<*>> { ModalComponentHandler<C, T>(it).apply { config() }.producer!! }

    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = components.resolve().flatMap { it.render(config, generator) }
    override suspend fun handle(context: ModalContext<*>) = producer.resolve(context)(context)

    override fun toString() = "ModalComponent"
}

fun <C : Component, T> createModalArrayComponent(vararg inputs: ModalComponent<C, T>) = createModalComponent {
    val values = inputs.map { +it }
    produce { values.map { it.getValue() } }
}

inline fun <reified T : Any> createModalComponentFor(vararg inputs: ModalComponent<out ModalTopLevelComponent, *>) = createModalComponent {
    val values = inputs.map { +it }
    produce { T::class.primaryConstructor!!.call(*values.map { it.getValue() }.toTypedArray()) }
}

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
typealias ModalResultHandler<T> = ModalContext<*>.(value: T) -> Unit

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
    fun handle(context: ModalContext<*>): T

    override fun transform(mapper: (IdGenerator, (IdGenerator) -> List<C>) -> List<C>) = object : ModalComponent<C, T> {
        override fun handle(context: ModalContext<*>) = this@ModalComponent.handle(context)
        override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@ModalComponent.render(config, it) }

        override fun toString() = this@ModalComponent.toString()
    }
}

fun <C : Component, T, O> ModalComponent<C, T>.map(handler: ModalContext<*>.(value: T) -> O) = object : ModalComponent<C, O> {
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = this@map.render(config, generator)
    override fun handle(context: ModalContext<*>): O = handler.invoke(context, this@map.handle(context))

    override fun toString() = this@map.toString()
}

interface ModalElement<C : Component, T> : ModalComponent<C, T>, IElement<C>

fun <C : Component, T> createModalElement(
    name: String,
    handler: ModalContext<*>.() -> T,
    renderer: (MenuConfig<*, *>, String) -> C
) = object : ModalElement<C, T> {
    override val name = name

    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = listOf(renderer(config, generator.nextId(config, name)))
    override fun handle(context: ModalContext<*>) = handler.invoke(context)

    override fun toString() = "ModalElement[$name]"
}

fun <C : Component, T, O> ModalElement<C, T>.map(handler: ModalContext<*>.(value: T) -> O): ModalElement<C, O> = object : ModalElement<C, O>, ModalComponent<C, O> by (this as ModalComponent<C, T>).map(handler) {
    override val name = this@map.name
}

fun <C : Component, T> createModalLayoutComponent(
    handler: ModalContext<*>.() -> T,
    renderer: (MenuConfig<*, *>, IdGenerator) -> List<C>
) = object : ModalComponent<C, T> {
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderer(config, generator)
    override fun toString() = "ModalComponent"

    override fun handle(context: ModalContext<*>) = context.handler()
}

sealed interface ModalComponentBuilder<C: Component, T> : MenuCallbackContext {
    operator fun <U> ModalComponent<out C, U>.unaryPlus(): ModalResult<U>
    fun produce(handler: (ModalContext<*>) -> T)
}

private class ModalComponentRenderer<C: Component, T>() : ModalComponentBuilder<C, T> {
    override val phase = MenuCallbackPhase.RENDER

    val components = mutableListOf<ModalComponent<out C, *>>()

    override operator fun <U> ModalComponent<out C, U>.unaryPlus(): ModalResult<U> {
        components += this
        return emptyModalResult()
    }

    override fun produce(handler: (ModalContext<*>) -> T) {}
}

private class ModalComponentHandler<C: Component, T>(val context: ModalContext<*>) : ModalComponentBuilder<C, T> {
    override val phase = MenuCallbackPhase.HANDLE

    var producer: ((ModalContext<*>) -> T)? = null

    override operator fun <U> ModalComponent<out C, U>.unaryPlus() = object : ModalResult<U> {
        override fun getValue() = handle(context)
    }

    override fun produce(handler: (ModalContext<*>) -> T) {
        require(producer == null)
        producer = handler
    }
}

fun <C : Component, T> createModalComponent(config: ModalComponentBuilder<C, T>.() -> Unit) = object : ModalComponent<C, T> {
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = ModalComponentRenderer<C, T>().apply { config() }.components.flatMap { it.render(config, generator) }
    override fun handle(context: ModalContext<*>) = ModalComponentHandler<C, T>(context).apply(config).producer!!(context)

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

private data class LazyModalComponentContext(override val phase: MenuCallbackPhase) : MenuCallbackContext

fun <C : Component, T> createLazyModalComponent(component: MenuCallbackContext.() -> ModalComponent<C, T>) = object : ModalComponent<C, T> {
    val renderComponent by lazy { LazyModalComponentContext(MenuCallbackPhase.RENDER).component() }
    val handleComponent by lazy { LazyModalComponentContext(MenuCallbackPhase.HANDLE).component() }

    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderComponent.render(config, generator)
    override fun handle(context: ModalContext<*>) = handleComponent.handle(context)
}
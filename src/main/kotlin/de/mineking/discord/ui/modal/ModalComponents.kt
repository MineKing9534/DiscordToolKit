package de.mineking.discord.ui.modal

import de.mineking.discord.ui.*
import de.mineking.discord.ui.message.MessageMenu
import de.mineking.discord.ui.message.disableComponents
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import kotlin.reflect.full.primaryConstructor

typealias ModalHandler<M> = suspend ModalContext<M>.() -> Unit

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

interface ModalComponent<T> : IComponent<TextInput> {
    fun handle(context: ModalContext<*>): T

    override fun transform(mapper: (IdGenerator, (IdGenerator) -> List<TextInput>) -> List<TextInput>) = object : ModalComponent<T> {
        override fun handle(context: ModalContext<*>) = this@ModalComponent.handle(context)
        override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@ModalComponent.render(config, it) }

        override fun toString() = this@ModalComponent.toString()
    }
}

fun <T, O> ModalComponent<T>.map(handler: ModalContext<*>.(value: T) -> O) = object : ModalComponent<O> {
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = this@map.render(config, generator)
    override fun handle(context: ModalContext<*>): O = handler.invoke(context, this@map.handle(context))

    override fun toString() = this@map.toString()
}

fun <T> createModalElement(
    name: String,
    handler: ModalContext<*>.() -> T,
    renderer: (MenuConfig<*, *>, String) -> TextInput
) = object : ModalComponent<T> {
    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = listOf(renderer(config, generator.nextId("$name:")))
    override fun handle(context: ModalContext<*>) = handler.invoke(context)

    override fun toString() = "ModalElement[$name]"
}

class ModalComponentBuilder<T>(override val phase: MenuCallbackPhase) : MenuCallbackContext {
    internal val components = mutableListOf<ModalComponent<*>>()
    internal var producer: ((ModalContext<*>) -> T)? = null

    operator fun <T> ModalComponent<T>.unaryPlus(): ModalResult<T> {
        components += this
        return emptyModalResult()
    }

    fun produce(handler: (ModalContext<*>) -> T) {
        require(producer == null)
        producer = handler
    }
}

fun <T> createModalComponent(config: ModalComponentBuilder<T>.() -> Unit) = object : ModalComponent<T> {
    val render by lazy { ModalComponentBuilder<T>(MenuCallbackPhase.RENDER).apply(config) }
    val handle by lazy { ModalComponentBuilder<T>(MenuCallbackPhase.HANDLE).apply(config) }

    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = render.components.flatMap { it.render(config, generator) }
    override fun handle(context: ModalContext<*>) = handle.producer!!.invoke(context)

    override fun toString() = "ModalComponent"
}

fun <T> createModalArrayComponent(vararg inputs: ModalComponent<T>) = createModalComponent {
    val values = inputs.map { +it }
    produce { values.map { it.getValue() } }
}

inline fun <reified T : Any> createModalComponentFor(vararg inputs: ModalComponent<*>) = createModalComponent {
    val values = inputs.map { +it }
    produce { T::class.primaryConstructor!!.call(*values.map { it.getValue() }.toTypedArray()) }
}

private data class LazyModalComponentContext(override val phase: MenuCallbackPhase) : MenuCallbackContext

fun <T> createLazyModalComponent(component: MenuCallbackContext.() -> ModalComponent<T>) = object : ModalComponent<T> {    
    val renderComponent by lazy { LazyModalComponentContext(MenuCallbackPhase.RENDER).component() }
    val handleComponent by lazy { LazyModalComponentContext(MenuCallbackPhase.HANDLE).component() }

    override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderComponent.render(config, generator)
    override fun handle(context: ModalContext<*>) = handleComponent.handle(context)
}
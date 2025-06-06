package de.mineking.discord.ui

import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import kotlin.reflect.full.primaryConstructor

typealias ModalResult<M, T> = suspend ModalContext<M>.() -> T

typealias ModalHandler<M> = suspend ModalContext<M>.() -> Unit

@MenuMarker
class ModalContext<M>(menu: MenuInfo<M>, stateData: StateData, event: ModalInteractionEvent) : HandlerContext<M, ModalInteractionEvent>(menu, stateData, event) {
    override val message: Message get() = event.message!!

    suspend fun <N> switchMenu(menu: MessageMenu<N, *>, builder: StateBuilderConfig = DEFAULT_STATE_BUILDER) {
        val state = StateBuilder(this, menu)
        state.builder()

        val context = TransferContext(menu.info, state.build(), event, event.message!!)

        if (menu.defer == DeferMode.ALWAYS) context.disableComponents(context.message).queue()
        menu.update(context)
    }
}

interface ModalComponent<T> : IComponent<TextInput> {
    suspend fun handle(context: ModalContext<*>): T

    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<TextInput>) -> List<TextInput>) = object : ModalComponent<T> {
        override suspend fun handle(context: ModalContext<*>) = this@ModalComponent.handle(context)

        override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@ModalComponent.render(config, it) }

        override fun toString() = this@ModalComponent.toString()
    }

    fun <O> map(handler: suspend (value: T) -> O) = object : ModalComponent<O> {
        override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = this@ModalComponent.render(config, generator)

        override suspend fun handle(context: ModalContext<*>): O = handler.invoke(this@ModalComponent.handle(context))

        override fun toString() = this@ModalComponent.toString()
    }
}

fun <T> createModalElement(
    name: String,
    handler: ModalContext<*>.() -> T,
    renderer: suspend (MenuConfig<*, *>, String) -> TextInput
) = object : ModalComponent<T> {
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = listOf(renderer(config, generator.nextId("$name:")))
    override suspend fun handle(context: ModalContext<*>) = handler.invoke(context)

    override fun toString() = "ModalElement[$name]"
}

@MenuMarker
class ModalComponentBuilder<T>(override val phase: MenuConfigPhase) : IMenuContext {
    internal val components = mutableListOf<ModalComponent<*>>()
    internal var producer: ModalResult<*, T>? = null

    operator fun <T> ModalComponent<T>.unaryPlus(): ModalResult<*, T> {
        components.add(this)
        return this::handle
    }

    fun produce(handler: ModalResult<*, T>) {
        require(producer == null)
        producer = handler
    }
}

fun <T> createModalComponent(config: ModalComponentBuilder<T>.() -> Unit) = object : ModalComponent<T> {
    val render by lazy { ModalComponentBuilder<T>(MenuConfigPhase.RENDER).apply(config) }
    val handle by lazy { ModalComponentBuilder<T>(MenuConfigPhase.COMPONENTS).apply(config) }

    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = render.components.flatMap { it.render(config, generator) }
    override suspend fun handle(context: ModalContext<*>) = handle.producer!!.invoke(context)

    override fun toString() = "ModalComponent"
}

fun <T> createModalArrayComponent(vararg inputs: ModalComponent<T>) = createModalComponent {
    val values = inputs.map { +it }
    produce { values.map { it() } }
}

inline fun <reified T : Any> createModalComponentFor(vararg inputs: ModalComponent<*>) = createModalComponent {
    val values = inputs.map { +it }
    produce { T::class.primaryConstructor!!.call(*values.map { it() }.toTypedArray()) }
}

private data class MenuContext(override val phase: MenuConfigPhase) : IMenuContext
fun <T> createLazyModalComponent(component: IMenuContext.() -> ModalComponent<T>) = object : ModalComponent<T> {
    val renderComponent by lazy { MenuContext(MenuConfigPhase.RENDER).component() }
    val handleComponent by lazy { MenuContext(MenuConfigPhase.COMPONENTS).component() }

    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = renderComponent.render(config, generator)
    override suspend fun handle(context: ModalContext<*>) = handleComponent.handle(context)
}
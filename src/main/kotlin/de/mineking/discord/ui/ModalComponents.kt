package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.components.text.TextInput

typealias ModalResult<M, T> = ModalContext<M>.() -> T

typealias ModalHandler<M> = ModalContext<M>.() -> Unit

@MenuMarker
class ModalContext<M>(menu: MenuInfo<M>, stateData: StateData, event: ModalInteractionEvent) : HandlerContext<M, ModalInteractionEvent>(menu, stateData, event) {
    override val message: Message get() = event.message!!

    fun <N> switchMenu(menu: MessageMenu<N, *>, builder: StateBuilderConfig = DEFAULT_STATE_BUILDER) {
        val state = StateBuilder(this, menu)
        state.builder()

        val context = TransferContext(menu.info, state.build(), event, event.message!!)

        if (menu.defer == DeferMode.ALWAYS) context.disableComponents(context.message).queue()
        menu.update(context)
    }
}

interface IModalComponent<out T> {
    fun children(): List<IModalComponent<*>>
    fun elements(): List<ModalElement<*>> = children().flatMap { if (it is ModalElement) listOf(it) else it.elements() }

    fun handle(context: ModalContext<*>): T

    fun render(generator: IdGenerator): List<Pair<TextInput?, ModalElement<*>>>
    fun transform(transform: (current: TextInput?) -> TextInput?): IModalComponent<T> = object : IModalComponent<T> by this {
        override fun render(generator: IdGenerator): List<Pair<TextInput?, ModalElement<*>>> = this@IModalComponent.render(generator).map { (component, element) -> transform(component) to element }
    }

    fun hide(hide: Boolean = true) = transform { if (hide) null else it }
    fun show(show: Boolean = true) = hide(!show)

    fun <O> transformResult(handler: (value: T) -> O): IModalComponent<O> = object : IModalComponent<O> {
        override fun children() = this@IModalComponent.children()
        override fun render(generator: IdGenerator) = this@IModalComponent.render(generator)

        override fun handle(context: ModalContext<*>): O = handler.invoke(this@IModalComponent.handle(context))
    }
}

abstract class ModalElement<T>(override val name: String, override val localization: LocalizationFile?) : Element, IModalComponent<T> {
    override fun children() = listOf(this)
    open fun finalize(component: TextInput?): TextInput? = component

    override fun toString(): String = "ModalElement[$name]"
}

internal fun <T> createModalElement(
    name: String,
    renderer: (id: IdGenerator) -> List<TextInput?>,
    localization: LocalizationFile? = null,
    finalizer: (TextInput?) -> TextInput? = { it },
    handler: ModalContext<*>.() -> T
): IModalComponent<T> = object : ModalElement<T>(name, localization) {
    override fun render(generator: IdGenerator): List<Pair<TextInput?, ModalElement<*>>> = renderer(generator).map { it to this }
    override fun transform(transform: (TextInput?) -> TextInput?) = createModalElement(name, { id -> render(id).map { (component) -> transform(component) } }, localization, finalizer, handler)

    override fun handle(context: ModalContext<*>) = context.handler()
    override fun finalize(component: TextInput?): TextInput? = finalizer(component)
}

fun <T> modalElement(
    name: String,
    render: (id: String) -> TextInput?,
    localization: LocalizationFile? = null,
    finalizer: (TextInput?) -> TextInput? = { it },
    handler: ModalContext<*>.() -> T
) = createModalElement(name, { listOf(render(it.nextId("$name:"))) }, localization, finalizer, handler)

class ModalComponent<T>(private val children: List<IModalComponent<*>>, private val handler: ModalContext<*>.() -> T) : IModalComponent<T> {
    override fun children(): List<IModalComponent<*>> = children
    override fun handle(context: ModalContext<*>): T = context.handler()

    override fun render(generator: IdGenerator): List<Pair<TextInput?, ModalElement<*>>> = elements().flatMap { it.render(generator) }
}

class ModalComponentBuilder<T> {
    internal val components = mutableListOf<IModalComponent<*>>()
    internal var producer: (ModalContext<*>.() -> T)? = null

    operator fun <T> IModalComponent<T>.unaryPlus(): ModalContext<*>.() -> T {
        components.add(this)
        return this::handle
    }

    fun produce(handler: ModalContext<*>.() -> T) {
        require(producer == null)
        producer = handler
    }
}

fun <T> composeInputs(builder: ModalComponentBuilder<T>.() -> Unit): ModalComponent<T> {
    val initial = ModalComponentBuilder<T>()
    initial.builder()

    return ModalComponent(initial.components) {
        val temp = ModalComponentBuilder<T>()
        temp.builder()
        temp.producer!!.invoke(this)
    }
}
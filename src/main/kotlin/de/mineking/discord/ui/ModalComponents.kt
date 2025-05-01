package de.mineking.discord.ui

import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent

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

interface ModalComponent<T> : IComponent<TextInput> {
    fun handle(context: ModalContext<*>): T

    override fun transform(mapper: (() -> List<TextInput>) -> List<TextInput>) = object : ModalComponent<T> {
        override fun handle(context: ModalContext<*>) = this@ModalComponent.handle(context)

        override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper { this@ModalComponent.render(config, generator) }

        override fun toString() = this@ModalComponent.toString()
    }

    fun <O> map(handler: (value: T) -> O) = object : ModalComponent<O> {
        override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = this@ModalComponent.render(config, generator)

        override fun handle(context: ModalContext<*>): O = handler.invoke(this@ModalComponent.handle(context))

        override fun toString() = this@ModalComponent.toString()
    }
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

//TODO
fun createModalComponent() {

}
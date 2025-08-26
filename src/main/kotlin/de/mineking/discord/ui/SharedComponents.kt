package de.mineking.discord.ui

import de.mineking.discord.ui.message.ComponentHandler
import de.mineking.discord.ui.message.MessageComponent
import de.mineking.discord.ui.message.MessageElement
import de.mineking.discord.ui.modal.ModalComponent
import de.mineking.discord.ui.modal.ModalContext
import de.mineking.discord.ui.modal.ModalElement
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent

interface SharedComponent<C : Component, T> : MessageComponent<C>, ModalComponent<C, T> {
    override fun transform(mapper: (IdGenerator, (IdGenerator) -> List<C>) -> List<C>) = object : SharedComponent<C, T> {
        override fun elements() = this@SharedComponent.elements()
        override fun handle(context: ModalContext<*>) = this@SharedComponent.handle(context)
        override fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@SharedComponent.render(config, it) }
        override fun toString() = this@SharedComponent.toString()
    }
}

class SharedElement<C : Component, E : GenericComponentInteractionCreateEvent, T>(
    name: String,
    handler: ComponentHandler<*, E>?,
    val modalHandler: ModalContext<*>.() -> T,
    renderer: (MenuConfig<*, *>, IdGenerator) -> C?
) : MessageElement<C, E>(name, handler, renderer), ModalElement<C, T>, SharedComponent<C, T> {
    override fun transform(mapper: (IdGenerator, (IdGenerator) -> List<C>) -> List<C>) = SharedElement(name, handler, modalHandler) { config, id -> mapper(id) { render(config, it) }.firstOrNull() }
    override fun handle(context: ModalContext<*>) = context.modalHandler()
}

fun <C : Component, E : GenericComponentInteractionCreateEvent, T> createSharedElement(
    name: String,
    handler: ComponentHandler<*, E>? = null,
    modalHandler: ModalContext<*>.() -> T,
    renderer: (MenuConfig<*, *>, String) -> C?
) = SharedElement(name, handler, modalHandler) { config, id -> renderer(config, id.nextId(config, name)) }
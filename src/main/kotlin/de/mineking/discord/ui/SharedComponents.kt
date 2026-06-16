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
    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<C>) -> List<C>) = object : SharedComponent<C, T> {
        override suspend fun elements() = this@SharedComponent.elements()
        override suspend fun handle(context: ModalContext<*>) = this@SharedComponent.handle(context)
        override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = mapper(generator) { this@SharedComponent.render(config, it) }
        override fun toString() = this@SharedComponent.toString()
    }
}

class SharedElement<C : Component, E : GenericComponentInteractionCreateEvent, T>(
    name: String,
    handler: ComponentHandler<*, E>?,
    val modalHandler: suspend ModalContext<*>.() -> T,
    renderer: suspend (MenuConfig<*, *>, IdGenerator) -> C?
) : MessageElement<C, E>(name, handler, renderer), ModalElement<C, T>, SharedComponent<C, T> {
    override fun transform(mapper: suspend (IdGenerator, suspend (IdGenerator) -> List<C>) -> List<C>) = SharedElement(name, handler, modalHandler) { config, id -> mapper(id) { render(config, it) }.firstOrNull() }
    override suspend fun handle(context: ModalContext<*>) = context.modalHandler()
}

fun <C : Component, E : GenericComponentInteractionCreateEvent, T> createSharedElement(
    name: String,
    handler: ComponentHandler<*, E>? = null,
    modalHandler: suspend ModalContext<*>.() -> T,
    renderer: suspend (MenuConfig<*, *>, String) -> C?
) = SharedElement(name, handler, modalHandler) { config, id -> renderer(config, id.nextId(config, name)) }

fun <C : Component> createSharedLayoutComponent(
    renderer: suspend (MenuConfig<*, *>, IdGenerator) -> C
) = object : SharedComponent<C, Nothing> {
    override suspend fun elements() = emptyList<MessageElement<*, *>>()
    override suspend fun render(config: MenuConfig<*, *>, generator: IdGenerator) = listOf(renderer(config, generator))
    override suspend fun handle(context: ModalContext<*>): Nothing = error("Cannot read value for layout component")

    override fun toString() = "SharedComponent"
}
package de.mineking.discord.ui.modal

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.IdGeneratorImpl
import de.mineking.discord.ui.MenuContext
import de.mineking.discord.ui.RenderTermination
import de.mineking.discord.ui.readLocalizedString
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.ModalTopLevelComponent
import net.dv8tion.jda.api.modals.Modal

interface ModalMenuHandler {
    suspend fun handle(handler: ModalMenuExecutor<*, *>, menu: ModalMenu<*, *>, state: ModalContext<*>)
    suspend fun build(renderer: ModalMenuRenderer<*, *>, menu: ModalMenu<*, *>, state: MenuContext<*>): Modal
}

inline fun <reified E: Throwable> ModalMenuHandler.handleException(
    crossinline handle: suspend ModalContext<*>.(ModalMenuExecutor<*, *>, E) -> Unit,
    crossinline build: suspend MenuContext<*>.(ModalMenuRenderer<*, *>, E) -> Modal
) = object : ModalMenuHandler {
    override suspend fun handle(handler: ModalMenuExecutor<*, *>, menu: ModalMenu<*, *>, state: ModalContext<*>) = try {
        this@handleException.handle(handler, menu, state)
    } catch (e: Throwable) {
        if (e !is E) throw e
        try {
            handle(state, handler, e)
        } catch (_: RenderTermination) {}
    }

    override suspend fun build(renderer: ModalMenuRenderer<*, *>, menu: ModalMenu<*, *>, state: MenuContext<*>) = try {
        this@handleException.build(renderer, menu, state)
    } catch (e: Throwable) {
        if (e !is E) throw e
        build(state, renderer, e)
    }
}

object DefaultModalHandler : ModalMenuHandler {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <M, L: LocalizationFile?> runConfig(config: ModalMenuConfig<*, *>, menu: ModalMenu<M, L>) =
        menu.config(config as ModalMenuConfig<M, L>, menu.localization)

    override suspend fun handle(handler: ModalMenuExecutor<*, *>, menu: ModalMenu<*, *>, state: ModalContext<*>) {
        runConfig(handler, menu)
        handler.context.lazy.forEach { it.active = true } //Activate lazy values => Allow them to load in the handler

        try {
            @Suppress("UNCHECKED_CAST")
            suspend fun <M> runHandlers(handlers: List<ModalHandler<M>>) =
                handlers.forEach { handler -> (state as ModalContext<M>).handler() }

            runHandlers(handler.handlers)
        } catch (_: RenderTermination) { }

        state.after.forEach { it() }
    }

    fun buildComponents(generator: IdGeneratorImpl, renderer: ModalMenuRenderer<*, *>): List<ModalTopLevelComponent> {
        val components = renderer.components.render(generator, renderer)

        val left = generator.charactersLeft()
        if (left != 0) error("Not enough component id space to store state. $left characters left")

        return components
    }

    override suspend fun build(renderer: ModalMenuRenderer<*, *>, menu: ModalMenu<*, *>, state: MenuContext<*>): Modal {
        runConfig(renderer, menu)
        val generator = IdGeneratorImpl(state.stateData.encode())

        return Modal.create(generator.nextId("${menu.name}:"), renderer.readLocalizedString(menu.localization, null, renderer.title, "title") ?: ZERO_WIDTH_SPACE)
            .addComponents(buildComponents(generator, renderer))
            .build()
    }
}
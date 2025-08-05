package de.mineking.discord.ui.message

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.DeferMode
import de.mineking.discord.ui.IdGenerator
import de.mineking.discord.ui.MenuContext
import de.mineking.discord.ui.RenderTermination
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import net.dv8tion.jda.api.utils.messages.MessageEditData

interface MessageMenuHandler {
    suspend fun handleComponent(finder: MessageMenuComponentFinder<*, *>, menu: MessageMenu<*, *>, state: ComponentContext<*, *>, oldState: String, name: String)
    suspend fun render(renderer: MessageMenuRenderer<*, *>, menu: MessageMenu<*, *>, state: MenuContext<*>): MessageEditData
}

inline fun <reified E: Throwable> MessageMenuHandler.handleException(
    crossinline component: suspend ComponentContext<*, *>.(MessageMenuComponentFinder<*, *>, E) -> Unit,
    crossinline render: suspend MenuContext<*>.(MessageMenuConfigImpl<*, *>, E) -> MessageEditData
) = object : MessageMenuHandler {
    override suspend fun handleComponent(finder: MessageMenuComponentFinder<*, *>, menu: MessageMenu<*, *>, state: ComponentContext<*, *>, oldState: String, name: String) = try {
        this@handleException.handleComponent(finder, menu, state, oldState, name)
    } catch (e: Throwable) {
        if (e !is E) throw e
        try {
            component(state, finder, e)
        } catch (_: RenderTermination) {}
    }

    override suspend fun render(renderer: MessageMenuRenderer<*, *>, menu: MessageMenu<*, *>, state: MenuContext<*>): MessageEditData = try {
        this@handleException.render(renderer, menu, state)
    } catch (e: Throwable) {
        if (e !is E) throw e
        render(state, renderer, e)
    }
}

object DefaultMessageMenuHandler : MessageMenuHandler {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <M, L: LocalizationFile?> runConfig(config: MessageMenuConfig<*, *>, menu: MessageMenu<M, L>) =
        menu.config(config as MessageMenuConfig<M, L>, menu.localization)

    override suspend fun handleComponent(finder: MessageMenuComponentFinder<*, *>, menu: MessageMenu<*, *>, state: ComponentContext<*, *>, oldState: String, name: String) {
        try {
            runConfig(finder, menu)
            error("Component $name not found")
        } catch (_: ComponentFinderResult) {
        } catch (_: RenderTermination) {
            error("A RenderTermination was thrown during component resolution")
        }

        try {
            finder.context.lazy.forEach { it.active = true } //Activate lazy values => Allow them to load in the handler
            finder.execute(state)
        } catch (_: RenderTermination) {
            return
        }

        val newState = state.stateData.encode()
        if (state.update != false) {
            //Rerender if: state changed, update forced
            if (oldState != newState || state.update == true) state.update()

            //Recreate the previous state if we deferred but state didn't change
            else if (menu.defer == DeferMode.ALWAYS) state.hook.editOriginal(MessageEditData.fromMessage(state.message)).queue()

            else if (!state.isAcknowledged) state.defer()
        }

        state.after.forEach { it() }
    }

    fun buildComponents(renderer: MessageMenuRenderer<*, *>): List<MessageTopLevelComponent> {
        val generator = IdGenerator(renderer.context.stateData.encode())

        @Suppress("UNCHECKED_CAST")
        val components = renderMessageComponents(generator, renderer)

        val left = generator.charactersLeft()
        if (left != 0) error("Not enough component id space to store state. $left characters left")

        return components
    }

    override suspend fun render(renderer: MessageMenuRenderer<*, *>, menu: MessageMenu<*, *>, state: MenuContext<*>): MessageEditData {
        runConfig(renderer, menu)
        val builder = renderer.message ?: MessageEditBuilder()

        return builder
            .useComponentsV2(menu.useComponentsV2)
            .setComponents(buildComponents(renderer))
            .build()
    }
}
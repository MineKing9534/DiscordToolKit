package de.mineking.discord.ui

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.interactions.modals.ModalTopLevelComponent

suspend fun renderModalComponents(id: IdGenerator, config: ModalConfigImpl<*, *>, force: Boolean = false) = config.components
    .map { if (force) it.show() else it }
    .flatMap {
        try {
            it.render(config, id)
        } catch (e: Exception) {
            throw RuntimeException("Error rendering component $it", e)
        }
    }

interface ModalMenuHandler {
    suspend fun <M, L : LocalizationFile?> handle(state: ModalContext<M>)
    suspend fun <M, L : LocalizationFile?> build(state: StateContext<M>): Modal

    companion object {
        val DEFAULT = object : ModalMenuHandler {
            override suspend fun <M, L : LocalizationFile?> handle(state: ModalContext<M>) {
                @Suppress("UNCHECKED_CAST")
                val menu = state.menuInfo.menu as ModalMenu<M, L>

                val renderer = ModalConfigImpl<M, L>(MenuConfigPhase.COMPONENTS, state, state.menuInfo)
                menu.config(renderer, menu.localization)

                renderer.activate()

                try {
                    renderer.handlers.forEach { handler -> state.handler() }
                } catch (_: RenderTermination) { }

                state.after.forEach { it() }
            }

            override suspend fun <M, L : LocalizationFile?> build(state: StateContext<M>): Modal {
                @Suppress("UNCHECKED_CAST")
                val menu = state.menuInfo.menu as ModalMenu<M, L>

                val renderer = ModalConfigImpl<M, L>(MenuConfigPhase.RENDER, state, state.menuInfo)
                menu.config(renderer, menu.localization)

                val generator = IdGenerator(state.stateData.encode())

                return Modal.create(generator.nextId("${menu.name}:"), renderer.readLocalizedString(menu.localization, null, renderer.title, "title") ?: ZERO_WIDTH_SPACE)
                    .addComponents(menu.buildComponents(generator, renderer))
                    .build()
            }
        }
    }
}

inline fun <reified E: Throwable> ModalMenuHandler.handleException(
    crossinline handle: suspend ModalContext<*>.(E) -> Unit,
    crossinline build: suspend StateContext<*>.(E) -> Modal
) = object : ModalMenuHandler {
    override suspend fun <M, L : LocalizationFile?> handle(state: ModalContext<M>) = try {
        this@handleException.handle<M, L>(state)
    } catch (e: Throwable) {
        if (e !is E) throw e
        handle(state, e)
    }

    override suspend fun <M, L : LocalizationFile?> build(state: StateContext<M>) = try {
        this@handleException.build<M, L>(state)
    } catch (e: Throwable) {
        if (e !is E) throw e
        build(state, e)
    }
}

class ModalMenu<M, L : LocalizationFile?>(
    manager: UIManager, name: String, defer: DeferMode,
    localization: L,
    setup: List<*>,
    states: List<InternalState<*>>,
    val config: LocalizedModalConfigurator<M, L>,
    val handler: ModalMenuHandler
) : Menu<M, ModalInteractionEvent, L>(manager, name, defer, localization, setup, states) {
    suspend fun buildComponents(generator: IdGenerator, renderer: ModalConfigImpl<M, L>): List<ModalTopLevelComponent> {
        val components = renderModalComponents(generator, renderer)

        val rows = components.map { ActionRow.of(it) }

        val left = generator.left()
        if (left != 0) error("Not enough component id space to store state. $left characters left")

        return rows
    }

    override suspend fun handle(event: ModalInteractionEvent) {
        if (defer == DeferMode.ALWAYS) event.deferEdit().queue()

        val data = (listOf(event.modalId) + event.values.map { it.customId }).decodeState(2)
        val context = ModalContext(info, StateData.decode(data), event)

        handler.handle<M, L>(context)
    }

    suspend fun build(state: StateContext<M>) = handler.build<M, L>(state)

    suspend fun createInitial(param: M): Modal {
        val state = SendState(info, StateData.createInitial(states), param)
        return build(state)
    }
}

suspend fun IModalCallback.replyModal(menu: ModalMenu<Unit, *>) = replyModal(menu, Unit)
suspend fun <C : IModalCallback> C.replyEventModal(menu: ModalMenu<in C, *>) = replyModal(menu, this)
suspend fun <M> IModalCallback.replyModal(menu: ModalMenu<M, *>, param: M) = replyModal(menu.createInitial(param))

typealias ModalConfigurator<M> = suspend ModalConfig<M, *>.() -> Unit
typealias LocalizedModalConfigurator<M, L> = suspend ModalConfig<M, L>.(localization: L) -> Unit

interface ModalConfig<M, L : LocalizationFile?> : MenuConfig<M, L> {
    operator fun <T> ModalComponent<T>.unaryPlus(): ModalResult<M, T>

    fun title(title: CharSequence)
    fun execute(handler: ModalHandler<M>)
}

class ModalConfigImpl<M, L : LocalizationFile?>(
    phase: MenuConfigPhase,
    state: StateContext<M>?,
    menu: MenuInfo<M>
) : MenuConfigImpl<M, L>(phase, state, menu), ModalConfig<M, L> {
    val components = mutableListOf<ModalComponent<*>>()

    var title: CharSequence = DEFAULT_LABEL
    val handlers = mutableListOf<ModalHandler<M>>()

    override fun <T> ModalComponent<T>.unaryPlus(): ModalResult<M, T> {
        components += this
        return { handle(this) }
    }

    override fun title(title: CharSequence) {
        this.title = title
    }

    override fun execute(handler: ModalHandler<M>) {
        handlers += handler
    }
}
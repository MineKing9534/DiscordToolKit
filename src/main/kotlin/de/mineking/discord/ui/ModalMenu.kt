package de.mineking.discord.ui

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.interactions.modals.ModalTopLevelComponent

fun renderModalComponents(id: IdGenerator, config: ModalConfigImpl<*, *>, force: Boolean = false) = config.components
    .map { if (force) it.show() else it }
    .flatMap {
        try {
            it.render(config, id)
        } catch (e: Exception) {
            throw RuntimeException("Error rendering component $it", e)
        }
    }

class ModalMenu<M, L : LocalizationFile?>(
    manager: UIManager, name: String, defer: DeferMode,
    localization: L,
    setup: List<*>,
    states: List<InternalState<*>>,
    private val config: LocalizedModalConfigurator<M, L>
) : Menu<M, ModalInteractionEvent, L>(manager, name, defer, localization, setup, states) {
    private fun buildComponents(generator: IdGenerator, renderer: ModalConfigImpl<M, L>): List<ModalTopLevelComponent> {
        val components = renderModalComponents(generator, renderer)

        val rows = components.map { ActionRow.of(it) }

        val left = generator.left()
        if (left != 0) error("Not enough component id space to store state. $left characters left")

        return rows
    }

    override fun handle(event: ModalInteractionEvent) {
        if (defer == DeferMode.ALWAYS) event.deferEdit().queue()

        val data = (listOf(event.modalId) + event.values.map { it.customId }).decodeState(2)
        val context = ModalContext(info, StateData.decode(data), event)

        val renderer = ModalConfigImpl<M, L>(MenuConfigPhase.COMPONENTS, context, this.info)
        renderer.config(localization)

        renderer.activate()

        try {
            renderer.handlers.forEach { handler -> context.handler() }
        } catch (_: RenderTermination) {
        }

        context.after.forEach { it() }
    }

    fun build(state: StateContext<M>): Modal {
        val renderer = ModalConfigImpl<M, L>(MenuConfigPhase.RENDER, state, info)
        renderer.config(localization)

        val generator = IdGenerator(state.stateData.encode())

        return Modal.create(generator.nextId("$name:"), renderer.readLocalizedString(localization, null, renderer.title, "title") ?: ZERO_WIDTH_SPACE)
            .addComponents(buildComponents(generator, renderer))
            .build()
    }

    fun createInitial(param: M): Modal {
        val state = SendState(info, StateData.createInitial(states), param)
        return build(state)
    }
}

fun IModalCallback.replyModal(menu: ModalMenu<Unit, *>) = replyModal(menu, Unit)
fun <C : IModalCallback> C.replyEventModal(menu: ModalMenu<in C, *>) = replyModal(menu, this)
fun <M> IModalCallback.replyModal(menu: ModalMenu<M, *>, param: M) = replyModal(menu.createInitial(param))

typealias ModalConfigurator<M> = ModalConfig<M, *>.() -> Unit
typealias LocalizedModalConfigurator<M, L> = ModalConfig<M, L>.(localization: L) -> Unit

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
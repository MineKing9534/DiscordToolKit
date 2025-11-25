package de.mineking.discord.ui.modal

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.DeferMode
import de.mineking.discord.ui.IdGenerator
import de.mineking.discord.ui.InitialMenuContext
import de.mineking.discord.ui.Menu
import de.mineking.discord.ui.MenuContext
import de.mineking.discord.ui.StateData
import de.mineking.discord.ui.UIManager
import de.mineking.discord.ui.decodeState
import de.mineking.discord.ui.visible
import net.dv8tion.jda.api.components.ModalTopLevelComponent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.modals.Modal

fun Collection<ModalComponent<out ModalTopLevelComponent, *>>.render(id: IdGenerator, config: ModalMenuConfig<*, *>, force: Boolean = false) = this
    .map { if (force) it.visible() else it }
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
    val config: LocalizedModalConfigurator<M, L>,
    val handler: ModalMenuHandler
) : Menu<M, ModalInteractionEvent, L>(manager, name, defer, localization) {
    suspend fun build(state: MenuContext<M>): Modal {
        val renderer = ModalMenuRenderer(this, state)
        return handler.build(renderer, this, state)
    }

    override suspend fun handle(event: ModalInteractionEvent) {
        if (defer == DeferMode.ALWAYS) event.deferEdit().queue()

        val data = (listOf(event.modalId) + event.values.map { it.customId }).decodeState(2)
        val context = ModalContext(this, decodeState(data), event)

        val executor = ModalMenuExecutor(this, context)
        handler.handle(executor, this, context)
    }

    suspend fun createInitial(param: M): Modal {
        val state = InitialMenuContext(StateData.createInitial(states), param)
        return build(state)
    }
}

suspend fun IModalCallback.replyModal(menu: ModalMenu<Unit, *>) = replyModal(menu, Unit)
suspend fun <C : IModalCallback> C.replyEventModal(menu: ModalMenu<in C, *>) = replyModal(menu, this)
suspend fun <M> IModalCallback.replyModal(menu: ModalMenu<M, *>, param: M) = replyModal(menu.createInitial(param))
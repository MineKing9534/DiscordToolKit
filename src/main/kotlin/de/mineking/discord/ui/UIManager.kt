package de.mineking.discord.ui

import de.mineking.discord.DiscordToolKit
import de.mineking.discord.Manager
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.message.*
import de.mineking.discord.ui.modal.*
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.utils.messages.MessageRequest
import org.kodein.emoji.Emoji

var DEFAULT_COMPONENTS_V2 = MessageRequest.isDefaultUseComponentsV2()

fun Emoji.jda() = fromUnicode(this.details.string)

typealias MessageMenuInitializer<M, L> = suspend MessageMenu<M, L>.() -> Unit
typealias ModalMenuInitializer<M, L> = suspend ModalMenu<M, L>.() -> Unit

class UIManager(manager: DiscordToolKit<*>) : Manager(manager) {
    internal val menus: MutableMap<String, Menu<*, *, *>> = mutableMapOf()
    var localization: MenuLocalizationHandler = UnlocalizedLocalizationHandler()
        private set

    private var messageMenuHandler: MessageMenuHandler = DefaultMessageMenuHandler
    private var modalMenuHandler: ModalMenuHandler = DefaultModalHandler

    init {
        manager.listen<GenericComponentInteractionCreateEvent>(this::handleComponent)
        manager.listen<ModalInteractionEvent>(this::handleModal)
    }

    fun localize(localization: MenuLocalizationHandler = DefaultLocalizationHandler("menu")) {
        this.localization = localization
    }

    fun handleMessageMenu(handler: MessageMenuHandler) {
        this.messageMenuHandler = handler
    }

    fun handleModalMenu(handler: ModalMenuHandler) {
        this.modalMenuHandler = handler
    }

    fun <M, L : LocalizationFile?> registerLocalizedMenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        localization: L,
        init: MessageMenuInitializer<M, L>? = null,
        handler: MessageMenuHandler? = null,
        config: LocalizedMessageMenuConfigurator<M, L>
    ): MessageMenu<M, L> = runBlocking {
        require(name !in menus) { "Menu $name already defined" }

        val menu = MessageMenu(this@UIManager, name, defer, useComponentsV2 ?: DEFAULT_COMPONENTS_V2, localization, config, handler ?: messageMenuHandler)
        menus[name] = menu

        if (init != null) menu.init()
        else {
            val builder = MessageMenuBuilder(menu)
            builder.config(localization)
        }

        menu
    }

    fun <M, L : LocalizationFile?> registerLocalizedModal(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        localization: L,
        init: ModalMenuInitializer<M, L>? = null,
        handler: ModalMenuHandler? = null,
        config: LocalizedModalConfigurator<M, L>
    ): ModalMenu<M, L> = runBlocking {
        require(name !in menus) { "Menu $name already defined" }

        val menu = ModalMenu(this@UIManager, name, defer, localization, config, handler ?: modalMenuHandler)
        menus[name] = menu

        if (init != null) menu.init()
        else {
            val builder = ModalMenuBuilder(menu)
            builder.config(localization)
        }

        menu
    }

    @Suppress("UNCHECKED_CAST")
    fun <M> getMenu(name: String) = menus[name] as Menu<M, *, *>? ?: error("Menu not found")
    fun <M> getMessageMenu(name: String) = getMenu<M>(name) as MessageMenu
    fun <M> getModalMenu(name: String) = getMenu<M>(name) as ModalMenu

    private suspend fun handleComponent(event: GenericComponentInteractionCreateEvent) {
        val id = event.componentId.split(":", limit = 3)
        val menu = menus[id[0]] as MessageMenu<*, *>? ?: return

        menu.handle(event)
    }

    private suspend fun handleModal(event: ModalInteractionEvent) {
        val id = event.modalId.split(":", limit = 2)
        val menu = menus[id[0]] as ModalMenu<*, *>? ?: return

        menu.handle(event)
    }
}

fun <M> UIManager.registerMenu(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean? = null,
    localization: LocalizationFile? = null,
    init: MessageMenuInitializer<M, *>? = null,
    handler: MessageMenuHandler? = null,
    config: MessageMenuConfigurator<M>
) = registerLocalizedMenu(name, defer, useComponentsV2, localization, init, handler) { config() }

inline fun <M, reified L : LocalizationFile> UIManager.registerLocalizedMenu(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean? = null,
    noinline init: MessageMenuInitializer<M, L>? = null,
    handler: MessageMenuHandler? = null,
    noinline config: LocalizedMessageMenuConfigurator<M, L>
): MessageMenu<M, L> {
    val file = manager.localizationManager.read<L>()
    return registerLocalizedMenu(name, defer, useComponentsV2, file, init, handler, config)
}

fun <M> UIManager.registerModal(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    localization: LocalizationFile? = null,
    init: ModalMenuInitializer<M, *>? = null,
    handler: ModalMenuHandler? = null,
    config: ModalConfigurator<M>
) = registerLocalizedModal(name, defer, localization, init, handler) { config() }

inline fun <M, reified L : LocalizationFile> UIManager.registerLocalizedModal(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    noinline init: ModalMenuInitializer<M, *>? = null,
    handler: ModalMenuHandler? = null,
    noinline config: LocalizedModalConfigurator<M, L>
): ModalMenu<M, L> {
    val file = manager.localizationManager.read<L>()
    return registerLocalizedModal(name, defer, file, init, handler, config)
}
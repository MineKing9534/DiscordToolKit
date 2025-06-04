package de.mineking.discord.ui

import de.mineking.discord.DiscordToolKit
import de.mineking.discord.Manager
import de.mineking.discord.commands.MenuCommandConfigImpl
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.utils.messages.MessageRequest
import org.kodein.emoji.Emoji
import kotlin.math.max
import kotlin.math.min

var DEFAULT_COMPONENTS_V2 = MessageRequest.isDefaultUseComponentsV2()

fun Emoji.jda() = fromUnicode(this.details.string)

class UIManager(manager: DiscordToolKit<*>) : Manager(manager) {
    internal val menus: MutableMap<String, Menu<*, *, *>> = mutableMapOf()
    var localization: MenuLocalizationHandler = UnlocalizedLocalizationHandler()
        private set

    private var messageMenuHandler = MessageMenuHandler.DEFAULT

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

    private suspend fun prepareLocalization(config: MenuConfigData) {
        val id = IdGenerator("")
        when (config) {
            is MessageMenuConfigImpl<*, *> -> renderMessageComponents(id, config, force = true)
            is MenuCommandConfigImpl<*> -> prepareLocalization(config.parent as MessageMenuConfigImpl<*, *>)
            is ModalConfigImpl<*, *> -> renderModalComponents(id, config, force = true)
        }
    }

    fun <M> registerMenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        localization: LocalizationFile? = null,
        handler: MessageMenuHandler? = null,
        init: MessageMenuConfigurator<M>
    ): MessageMenu<M, *> = runBlocking {
        val builder = MessageMenuConfigImpl(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this@UIManager), localization) { init() }
        builder.init()
        registerMenu(name, defer, useComponentsV2, localization, builder, handler, init)
    }

    fun <M> registerMenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        localization: LocalizationFile? = null,
        config: MenuConfigData,
        handler: MessageMenuHandler? = null,
        init: MessageMenuConfigurator<M>
    ) = registerLocalizedMenu(name, defer, useComponentsV2, localization, config, handler) { init() }

    inline fun <M, reified L : LocalizationFile> registerLocalizedMenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        handler: MessageMenuHandler? = null,
        noinline init: LocalizedMessageMenuConfigurator<M, L>
    ): MessageMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedMenu(name, defer, useComponentsV2, file, handler, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedMenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        localization: L,
        handler: MessageMenuHandler? = null,
        init: LocalizedMessageMenuConfigurator<M, L>
    ): MessageMenu<M, L> = runBlocking {
        @Suppress("UNCHECKED_CAST")
        val builder = MessageMenuConfigImpl(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this@UIManager), localization, init as LocalizedMessageMenuConfigurator<M, LocalizationFile?>)
        builder.init(localization)
        registerLocalizedMenu(name, defer, useComponentsV2, localization, builder, handler, init)
    }

    inline fun <M, reified L : LocalizationFile> registerLocalizedMenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        config: MenuConfigData,
        handler: MessageMenuHandler? = null,
        noinline init: LocalizedMessageMenuConfigurator<M, L>
    ): MessageMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedMenu(name, defer, useComponentsV2, file, config, handler, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedMenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        localization: L,
        config: MenuConfigData,
        handler: MessageMenuHandler? = null,
        init: LocalizedMessageMenuConfigurator<M, L>
    ): MessageMenu<M, L> = runBlocking {
        require(name !in menus) { "Menu $name already defined" }

        val menu = MessageMenu(this@UIManager, name, defer, useComponentsV2 ?: DEFAULT_COMPONENTS_V2, localization, config.setup, config.states, init, handler ?: messageMenuHandler)
        menus[name] = menu

        prepareLocalization(config)

        menu
    }

    fun <M> registerModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: LocalizationFile? = null, init: ModalConfigurator<M>): ModalMenu<M, *> = runBlocking {
        val builder = ModalConfigImpl<M, LocalizationFile?>(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this@UIManager))
        builder.init()
        registerModal(name, defer, localization, builder, init)
    }

    fun <M> registerModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: LocalizationFile? = null, config: MenuConfigData, init: ModalConfigurator<M>): ModalMenu<M, *> {
        return registerLocalizedModal(name, defer, localization, config) { init() }
    }

    inline fun <M, reified L : LocalizationFile> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, noinline init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedModal(name, defer, file, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: L, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> = runBlocking {
        val builder = ModalConfigImpl<M, L>(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this@UIManager))
        builder.init(localization)
        registerLocalizedModal(name, defer, localization, builder, init)
    }

    inline fun <M, reified L : LocalizationFile> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, config: MenuConfigData, noinline init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedModal(name, defer, file, config, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: L, config: MenuConfigData, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> = runBlocking {
        require(name !in menus) { "Menu $name already defined" }

        val menu = ModalMenu(this@UIManager, name, defer, localization, config.setup, config.states, init)
        menus[name] = menu

        prepareLocalization(config)

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

internal fun clamp(value: Int, min: Int, max: Int) = min(max, max(value, min))
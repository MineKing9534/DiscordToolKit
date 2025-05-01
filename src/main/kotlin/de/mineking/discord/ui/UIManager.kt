package de.mineking.discord.ui

import de.mineking.discord.DiscordToolKit
import de.mineking.discord.Manager
import de.mineking.discord.commands.MenuCommandConfigImpl
import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.builder.Message
import de.mineking.discord.ui.builder.MessageBuilder
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

    fun localize(localization: MenuLocalizationHandler = DefaultLocalizationHandler("menu")) {
        this.localization = localization
    }

    private fun prepareLocalization(config: MenuConfigData) {
        val id = IdGenerator("")
        when (config) {
            is MessageMenuConfigImpl<*, *> -> renderMessageComponents(id, config, force = true)
            is MenuCommandConfigImpl<*> -> prepareLocalization(config.parent as MessageMenuConfigImpl<*, *>)
            is ModalConfigImpl<*, *> -> renderModalComponents(id, config, force = true)
        }
    }

    fun <M> registerMenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, localization: LocalizationFile? = null, init: MessageMenuConfigurator<M>): MessageMenu<M, *> {
        val builder = MessageMenuConfigImpl(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this), localization) { init() }
        builder.init()
        return registerMenu(name, defer, useComponentsV2, localization, builder, init)
    }

    fun <M> registerMenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, localization: LocalizationFile? = null, config: MenuConfigData, init: MessageMenuConfigurator<M>): MessageMenu<M, *> {
        return registerLocalizedMenu<M, LocalizationFile?>(name, defer, useComponentsV2, localization, config) { init() }
    }

    inline fun <M, reified L : LocalizationFile> registerLocalizedMenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, noinline init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedMenu(name, defer, useComponentsV2, file, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedMenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, localization: L, init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
        @Suppress("UNCHECKED_CAST")
        val builder = MessageMenuConfigImpl(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this), localization, init as LocalizedMessageMenuConfigurator<M, LocalizationFile?>)
        builder.init(localization)
        return registerLocalizedMenu(name, defer, useComponentsV2, localization, builder, init)
    }

    inline fun <M, reified L : LocalizationFile> registerLocalizedMenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, config: MenuConfigData, noinline init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedMenu(name, defer, useComponentsV2, file, config, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedMenu(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, useComponentsV2: Boolean? = null, localization: L, config: MenuConfigData, init: LocalizedMessageMenuConfigurator<M, L>): MessageMenu<M, L> {
        require(!menus.containsKey(name)) { "Menu $name already defined" }

        val menu = MessageMenu(this, name, defer, useComponentsV2 ?: DEFAULT_COMPONENTS_V2, localization, config.setup, config.states, init)
        menus[name] = menu

        prepareLocalization(config)

        return menu
    }

    fun <M> registerModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: LocalizationFile? = null, init: ModalConfigurator<M>): ModalMenu<M, *> {
        val builder = ModalConfigImpl<M, LocalizationFile?>(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this))
        builder.init()
        return registerModal(name, defer, localization, builder, init)
    }

    fun <M> registerModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: LocalizationFile? = null, config: MenuConfigData, init: ModalConfigurator<M>): ModalMenu<M, *> {
        return registerLocalizedModal(name, defer, localization, config) { init() }
    }

    inline fun <M, reified L : LocalizationFile> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, noinline init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedModal(name, defer, file, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: L, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
        val builder = ModalConfigImpl<M, L>(MenuConfigPhase.BUILD, null, MenuInfo.create(name, this))
        builder.init(localization)
        return registerLocalizedModal(name, defer, localization, builder, init)
    }

    inline fun <M, reified L : LocalizationFile> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, config: MenuConfigData, noinline init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
        val file = manager.localizationManager.read<L>()
        return registerLocalizedModal(name, defer, file, config, init)
    }

    fun <M, L : LocalizationFile?> registerLocalizedModal(name: String, defer: DeferMode = DEFAULT_DEFER_MODE, localization: L, config: MenuConfigData, init: LocalizedModalConfigurator<M, L>): ModalMenu<M, L> {
        require(!menus.containsKey(name)) { "Menu $name already defined" }

        val menu = ModalMenu(this, name, defer, localization, config.setup, config.states, init)
        menus[name] = menu

        prepareLocalization(config)

        return menu
    }

    @Suppress("UNCHECKED_CAST")
    fun <M> getMenu(name: String) = menus[name] as Menu<M, *, *>? ?: error("Menu not found")
    fun <M> getMessageMenu(name: String) = getMenu<M>(name) as MessageMenu
    fun <M> getModalMenu(name: String) = getMenu<M>(name) as ModalMenu

    override fun onGenericComponentInteractionCreate(event: GenericComponentInteractionCreateEvent) {
        val id = event.componentId.split(":", limit = 3)
        val menu = menus[id[0]] as MessageMenu<*, *>? ?: return

        menu.handle(event)
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        val id = event.modalId.split(":", limit = 2)
        val menu = menus[id[0]] as ModalMenu<*, *>? ?: return

        menu.handle(event)
    }
}

internal fun clamp(value: Int, min: Int, max: Int) = min(max, max(value, min))
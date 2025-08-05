package de.mineking.discord.ui.message

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.*
import de.mineking.discord.ui.MenuConfigState
import de.mineking.discord.ui.builder.MessageConfigurator
import de.mineking.discord.ui.builder.buildMessage
import de.mineking.discord.ui.modal.LocalizedModalConfigurator
import de.mineking.discord.ui.modal.ModalConfigurator
import de.mineking.discord.ui.modal.ModalMenu
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder

typealias MessageMenuConfigurator<M> = suspend MessageMenuConfig<M, *>.() -> Unit
typealias LocalizedMessageMenuConfigurator<M, L> = suspend MessageMenuConfig<M, L>.(localization: L) -> Unit

interface MessageMenuConfig<M, L : LocalizationFile?> : MenuConfig<M, L> {
    override val menu: MessageMenu<M, L>

    fun <C : MessageComponent<*>> register(component: C): C
    operator fun MessageComponent<out MessageTopLevelComponent>.unaryPlus()

    fun message(data: MessageEditBuilder)

    fun <L : LocalizationFile?> localizedSubmenu(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        useComponentsV2: Boolean? = null,
        localization: L,
        detach: Boolean = false,
        config: LocalizedMessageMenuConfigurator<M, L>
    ): MessageMenu<M, L>

    fun <L : LocalizationFile?> localizedModal(
        name: String,
        defer: DeferMode = DEFAULT_DEFER_MODE,
        localization: L,
        detach: Boolean = false,
        config: LocalizedModalConfigurator<M, L>
    ): ModalMenu<M, L>
}

inline fun MessageMenuConfig<*, *>.message(builder: MessageConfigurator) {
    if (!isRender()) return
    message(buildMessage(builder).build())
}

fun <M> MessageMenuConfig<M, *>.submenu(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean? = null,
    localization: LocalizationFile? = null,
    detach: Boolean = false,
    config: MessageMenuConfigurator<M>
) = localizedSubmenu(name, defer, useComponentsV2, localization, detach) { config() }

fun <M> MessageMenuConfig<M, *>.modal(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    localization: LocalizationFile? = null,
    detach: Boolean = false,
    config: ModalConfigurator<M>
) = localizedModal(name, defer, localization, detach) { config() }

inline fun <M, reified L : LocalizationFile> MessageMenuConfig<M, *>.localizedSubmenu(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean? = null,
    detach: Boolean = false,
    noinline config: LocalizedMessageMenuConfigurator<M, L>
): MessageMenu<M, L> {
    val file = menu.manager.manager.localizationManager.read<L>()
    return localizedSubmenu(name, defer, useComponentsV2, file, detach, config)
}

inline fun <M, reified L : LocalizationFile> MessageMenuConfig<M, *>.localizedModal(
    name: String,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    noinline config: LocalizedModalConfigurator<M, L>
): ModalMenu<M, L> {
    val file = menu.manager.manager.localizationManager.read<L>()
    return localizedModal(name, defer, file, detach, config)
}

sealed class MessageMenuConfigImpl<M, L : LocalizationFile?>(
    override val menu: MessageMenu<M, L>,
    override val phase: MenuCallbackPhase,
    override val context: MenuContext<M>
) : MessageMenuConfig<M, L> {
    private companion object {
        class InternalTermination(val config: Any) : RuntimeException() {
            override fun fillInStackTrace() = this //This should decrease performance loss when creating the exception
        }
    }

    override val configState = MenuConfigState(menu)

    @Suppress("UNCHECKED_CAST")
    override fun <CL : LocalizationFile?> localizedSubmenu(name: String, defer: DeferMode, useComponentsV2: Boolean?, localization: CL, detach: Boolean, config: LocalizedMessageMenuConfigurator<M, CL>) = setup {
        val config =
            if (detach) config
            else submenu@{ localization ->
                val context = object : MessageMenuConfig<M, L> {
                    override val configState = this@submenu.configState

                    override val context = this@submenu.context
                    override val phase = this@submenu.phase
                    override val menu = this@MessageMenuConfigImpl.menu

                    override fun <C : MessageComponent<*>> register(component: C): C = component
                    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() {}

                    override fun message(data: MessageEditBuilder) {}

                    override fun <L : LocalizationFile?> localizedSubmenu(
                        name: String,
                        defer: DeferMode,
                        useComponentsV2: Boolean?,
                        localization: L,
                        detach: Boolean,
                        config: LocalizedMessageMenuConfigurator<M, L>
                    ) = run {
                        if (this@submenu.menu.name.menuName() == name) throw InternalTermination(config)
                        else getNextSubmenu<MessageMenu<M, L>>("${this@MessageMenuConfigImpl.menu.name}.$name")
                    }

                    override fun <L : LocalizationFile?> localizedModal(
                        name: String,
                        defer: DeferMode,
                        localization: L,
                        detach: Boolean,
                        config: LocalizedModalConfigurator<M, L>
                    ) = getNextSubmenu<ModalMenu<M, L>>("${this@MessageMenuConfigImpl.menu.name}.$name")
                }

                try {
                    this@MessageMenuConfigImpl.menu.config(context, this@MessageMenuConfigImpl.menu.localization)
                    error("Unable to match parent render to child entrypoint. Are you conditionally registering submenus?")
                } catch(e: InternalTermination) {
                    (e.config as LocalizedMessageMenuConfigurator<M, CL>).invoke(this@submenu, localization)
                }
            }

        menu.manager.registerLocalizedMenu("${menu.name}.$name", defer, useComponentsV2, localization ?: menu.localization as CL, config = config, handler = this@MessageMenuConfigImpl.menu.handler)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <CL : LocalizationFile?> localizedModal(name: String, defer: DeferMode, localization: CL, detach: Boolean, config: LocalizedModalConfigurator<M, CL>) = setup {
        val config: LocalizedModalConfigurator<M, CL> =
            if (detach) config
            else submenu@{ localization ->
                val context = object : MessageMenuConfig<M, L> {
                    override val configState = this@submenu.configState

                    override val context = this@submenu.context
                    override val phase = this@submenu.phase
                    override val menu = this@MessageMenuConfigImpl.menu

                    override fun <C : MessageComponent<*>> register(component: C): C = component
                    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() {}

                    override fun message(data: MessageEditBuilder) {}

                    override fun <L : LocalizationFile?> localizedSubmenu(
                        name: String,
                        defer: DeferMode,
                        useComponentsV2: Boolean?,
                        localization: L,
                        detach: Boolean,
                        config: LocalizedMessageMenuConfigurator<M, L>
                    ) = getNextSubmenu<MessageMenu<M, L>>("${this@MessageMenuConfigImpl.menu.name}.$name")

                    override fun <L : LocalizationFile?> localizedModal(
                        name: String,
                        defer: DeferMode,
                        localization: L,
                        detach: Boolean,
                        config: LocalizedModalConfigurator<M, L>
                    ) =
                        if (this@submenu.menu.name.menuName() == name) throw InternalTermination(config)
                        else getNextSubmenu<ModalMenu<M, L>>("${this@MessageMenuConfigImpl.menu.name}.$name")
                }

                try {
                    this@MessageMenuConfigImpl.menu.config(context, this@MessageMenuConfigImpl.menu.localization)
                    error("Unable to match parent render to child entrypoint. Are you conditionally registering submenus?")
                } catch(e: InternalTermination) {
                    (e.config as LocalizedModalConfigurator<M, CL>).invoke(this@submenu, localization)
                }
            }

        menu.manager.registerLocalizedModal("${menu.name}.$name", defer, localization ?: menu.localization as CL, config = config)
    }
}

class MessageMenuBuilder<M, L : LocalizationFile?>(menu: MessageMenu<M, L>) : MessageMenuConfigImpl<M, L>(menu, MenuCallbackPhase.BUILD, BuildMenuContext()) {
    internal val components = mutableListOf<MessageComponent<out MessageTopLevelComponent>>()

    override fun <C : MessageComponent<*>> register(component: C) = component
    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() {
        components += this
    }
    override fun message(data: MessageEditBuilder) {}
}

class MessageMenuRenderer<M, L : LocalizationFile?>(
    menu: MessageMenu<M, L>,
    context: MenuContext<M>
) : MessageMenuConfigImpl<M, L>(menu, MenuCallbackPhase.RENDER, context) {
    internal var message: MessageEditBuilder? = null
        private set

    internal val components = mutableListOf<MessageComponent<out MessageTopLevelComponent>>()

    override fun <C : MessageComponent<*>> register(component: C) = component
    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() {
        components += this
    }

    override fun message(data: MessageEditBuilder) {
        message = data
    }
}

class MessageMenuComponentFinder<M, L : LocalizationFile?>(
    val component: String,
    menu: MessageMenu<M, L>,
    context: MenuContext<M>
) : MessageMenuConfigImpl<M, L>(menu, MenuCallbackPhase.HANDLE, context) {
    private var handler: ComponentHandler<*, *>? = null

    private fun findHandler(component: MessageComponent<*>) = component.elements().forEach {
        if (it.name == this.component) {
            @Suppress("UNCHECKED_CAST")
            this@MessageMenuComponentFinder.handler = it.handler as ComponentHandler<*, *>
            throw ComponentFinderResult
        }
    }

    override fun <C : MessageComponent<*>> register(component: C) = component.also { findHandler(component) }
    override fun MessageComponent<out MessageTopLevelComponent>.unaryPlus() = findHandler(this)

    override fun message(data: MessageEditBuilder) {}

    suspend fun execute(context: ComponentContext<*, *>) {
        handler!!(context)
    }
}

object ComponentFinderResult : RuntimeException() {
    private fun readResolve(): Any = ComponentFinderResult
}

private fun <T: Menu<*, *, *>> MenuConfig<*, *>.getNextSubmenu(name: String): T = setup<T> {
    error("Tried to register a submenu while executing the parent menu config. This should never happen, please report this to the developers")
}
package de.mineking.discord.commands

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.DEFAULT_DEFER_MODE
import de.mineking.discord.ui.DeferMode
import de.mineking.discord.ui.MenuConfig
import de.mineking.discord.ui.MutableState
import de.mineking.discord.ui.StateUpdateHandler
import de.mineking.discord.ui.UIManager
import de.mineking.discord.ui.initialize
import de.mineking.discord.ui.message.MessageMenu
import de.mineking.discord.ui.message.MessageMenuBuilder
import de.mineking.discord.ui.message.MessageMenuConfig
import de.mineking.discord.ui.message.replyMenu
import de.mineking.discord.ui.message.sendMenu
import de.mineking.discord.ui.parameter
import de.mineking.discord.ui.state
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.requests.RestAction

typealias MenuCommandConfigurator = suspend MenuCommandConfig<*>.(localization: LocalizationFile?) -> Unit
typealias LocalizedMenuCommandConfigurator<L> = suspend MenuCommandConfig<L>.(localization: L) -> Unit

@CommandMarker
interface MenuCommandConfig<L : LocalizationFile?> : MessageMenuConfig<SlashCommandContext, L>, OptionConfig, CommandConfig<SlashCommandContext> {
    fun ignoreParentConditions()
}

context(config: MenuCommandConfig<*>)
suspend fun <T> Option<T>.createUninitializedState(handler: StateUpdateHandler<T?>? = null) = createState(null, handler)

context(config: MenuCommandConfig<*>)
suspend fun <T> Option<T>.createState(
    default: T =
        @Suppress("UNCHECKED_CAST")
        if (this.default != null || this.type.isMarkedNullable) this.default as T
        else error("You need to provide a default value or use createUninitializedState"),
    handler: StateUpdateHandler<T>? = null
): MutableState<T> {
    val (_, setValue, state) = config.state(type, default, handler)
    config.initialize {
        it.run {
            setValue(this@createState())
        }
    }

    return state
}

class MenuCommandConfigImpl<L : LocalizationFile?>(override val manager: CommandManager, val parent: MessageMenuConfig<SlashCommandContext, L>) : MessageMenuConfig<SlashCommandContext, L> by parent, MenuCommandConfig<L> {
    internal val options = mutableListOf<OptionInfo>()

    internal val before = mutableListOf<BeforeHandler<in SlashCommandContext>>()
    internal var inheritConditions = true

    internal var defaultMemberPermission: DefaultMemberPermissions? = null
    internal val contexts = manager.defaultInteractionContextTypes.toMutableSet()
    internal val types = manager.defaultIntegrationTypes.toMutableSet()

    override fun <T> option(data: OptionInfo): Option<OptionalOption<T>> {
        options += data
        return object : Option<OptionalOption<T>> {
            override val data: OptionInfo = data

            context(context: SlashCommandContext)
            override suspend fun invoke() = OptionalOption<T>(context.parseOption(data.name), context.hasOption(data.name))
        }
    }

    override fun defaultMemberPermission(permission: DefaultMemberPermissions) {
        defaultMemberPermission = permission
    }

    override fun interactionContextTypes(types: Collection<InteractionContextType>) {
        this.contexts += types
    }

    override fun integrationTypes(types: Collection<IntegrationType>) {
        this.types += types
    }

    override fun ignoreParentConditions() {
        inheritConditions = false
    }

    override fun before(handler: BeforeHandler<in SlashCommandContext>) {
        before += handler
    }
}

enum class MenuCommandResponseType(val action: suspend SlashCommandContext.(menu: MessageMenu<SlashCommandContext, *>) -> RestAction<*>) {
    REPLY({ menu -> replyMenu(menu, this, false) }),
    EPHEMERAL_REPLY({ menu -> replyMenu(menu, this, true) }),
    CHANNEL({ menu -> channel.sendMenu(menu, this) })
}

fun menuCommand(
    name: String,
    menu: String? = null,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    localization: LocalizationFile? = null,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean? = null,
    response: MenuCommandResponseType = MenuCommandResponseType.EPHEMERAL_REPLY,
    config: MenuCommandConfigurator
) = localizedMenuCommand(name, menu, description, localization, defer, useComponentsV2, response, config)

inline fun <reified L : LocalizationFile> localizedMenuCommand(
    name: String,
    menu: String? = null,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean? = null,
    response: MenuCommandResponseType = MenuCommandResponseType.EPHEMERAL_REPLY,
    crossinline config: LocalizedMenuCommandConfigurator<L>
): SlashCommand = {
    val file = manager.localizationManager.read<L>()
    localizedMenuCommand(name, menu, description, file, defer, useComponentsV2, response) { config(file) }(it)
}

fun <L : LocalizationFile?> localizedMenuCommand(
    name: String,
    menu: String? = null,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    localization: L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean? = null,
    response: MenuCommandResponseType = MenuCommandResponseType.EPHEMERAL_REPLY,
    config: LocalizedMenuCommandConfigurator<L>
): SlashCommand = { parent ->
    val manager = this

    val menuName = menu ?: "${parent?.path?.joinToString(".")?.let { "$it." } ?: ""}$name"

    val ui = manager.manager.get<UIManager>()
    lateinit var builder: MenuCommandConfigImpl<L>

    @Suppress("UNCHECKED_CAST")
    val menu = ui.registerLocalizedMenu(menuName, defer, useComponentsV2, localization, {
        val parent = MessageMenuBuilder(this)
        MenuCommandConfigImpl(manager, parent).also { builder = it }.config(localization)
    }) { MenuCommandConfigImpl(manager, this).config(localization) }

    slashCommand(name, description, localization) {
        builder.defaultMemberPermission?.let(this::defaultMemberPermission)

        if (!builder.inheritConditions) ignoreParentConditions()

        builder.options.forEach { option<Any?>(it) }

        builder.before.forEach { before(it) }
        execute { response.action(this, menu).queue() }
    }(parent)
}

fun MenuConfig<SlashCommandContext, *>.channel() = parameter({ null }, { it.event.messageChannel }, { event.messageChannel })
fun MenuConfig<SlashCommandContext, *>.event() = parameter({ null }, { it.event }, { event })
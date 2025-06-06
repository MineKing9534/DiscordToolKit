package de.mineking.discord.commands

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.*
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.requests.RestAction
import kotlin.reflect.KType

typealias MenuCommandConfigurator = suspend MenuCommandConfig<*>.(localization: LocalizationFile?) -> Unit
typealias LocalizedMenuCommandConfigurator<L> = suspend MenuCommandConfig<L>.(localization: L) -> Unit

@CommandMarker
interface MenuCommandConfig<L : LocalizationFile?> : MessageMenuConfig<SlashCommandContext, L>, OptionConfig, CommandConfig<SlashCommandContext> {
    suspend fun <T> Option<T>.createUninitializedState(type: KType, handler: StateHandler<T?>? = null) = createState(type, null, handler)
    suspend fun <T> Option<T>.createState(
        type: KType = if (this is RichOption) this.type else error("You need to provide a type"),
        default: T = if (this is RichOption && this.default != null) this.default!! else error("You need to provide a default value or use createUninitializedState"),
        handler: StateHandler<T>? = null
    ): State<T> {
        val (_, setValue, state) = state(type, default, handler)
        initialize { setValue(this@createState(it)) }

        return state
    }

    fun ignoreParentConditions()
}

class MenuCommandConfigImpl<L : LocalizationFile?>(override val manager: CommandManager, val parent: MessageMenuConfig<SlashCommandContext, L>) : MessageMenuConfig<SlashCommandContext, L> by parent, MenuCommandConfig<L>, MenuConfigData {
    internal val options = mutableListOf<OptionInfo>()

    internal val before = mutableListOf<BeforeHandler<in SlashCommandContext>>()
    internal var inheritConditions = true

    internal var defaultMemberPermission: DefaultMemberPermissions? = null
    internal val contexts = manager.defaultInteractionContextTypes.toMutableSet()
    internal val types = manager.defaultIntegrationTypes.toMutableSet()

    override val setup get() = (parent as MessageMenuConfigImpl).setup
    override val states get() = (parent as MessageMenuConfigImpl).states

    override fun <T> option(data: OptionInfo): Option<OptionalOption<T>> {
        options += data
        return object : RichOption<OptionalOption<T>> {
            override val data: OptionInfo = data
            override suspend fun invoke(context: SlashCommandContext) = OptionalOption<T>(context.parseOption(data.name), context.hasOption(data.name))
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
    val builder = MenuCommandConfigImpl(this, MessageMenuConfigImpl(MenuConfigPhase.BUILD, null, MenuInfo.create(menuName, ui), localization) { MenuCommandConfigImpl(manager, this).config(localization) })
    runBlocking { builder.config(localization) }

    @Suppress("UNCHECKED_CAST")
    val menu = ui.registerLocalizedMenu(menuName, defer, useComponentsV2, localization, builder) { MenuCommandConfigImpl(manager, this).config(localization) }

    slashCommand(name, description, localization) {
        builder.defaultMemberPermission?.let(this::defaultMemberPermission)

        if (!builder.inheritConditions) ignoreParentConditions()

        builder.options.forEach { option<Any?>(it) }

        builder.before.forEach { before(it) }
        execute { response.action(this, menu).queue() }
    }(parent)
}

fun MenuConfig<SlashCommandContext, *>.channel(): Parameter<MessageChannel> = parameter({ it.event.messageChannel }, { event.messageChannel })
fun MenuConfig<SlashCommandContext, *>.event(): Parameter<GenericInteractionCreateEvent> = parameter({ it.event }, { event })
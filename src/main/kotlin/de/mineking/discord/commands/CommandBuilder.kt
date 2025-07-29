package de.mineking.discord.commands

import de.mineking.discord.DiscordToolKit
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions

typealias CommandHandler<C> = suspend C.() -> Unit

interface BeforeHandler<C> : CommandHandler<C> {
    val inherit: Boolean get() = true
}

const val DEFAULT_COMMAND_DESCRIPTION = "-"
const val DEFAULT_OPTION_DESCRIPTION = "-"

typealias ContextCommandConfig<C> = GenericCommandBuilder<C>.() -> Unit
typealias SlashCommandConfig = SlashCommandBuilder.() -> Unit
//TODO typealias EntrypointConfig = GenericCommandBuilder<EntrypointCommandContext>.() -> Unit

typealias LocalizedContextCommandConfig<C, L> = GenericCommandBuilder<C>.(localization: L) -> Unit
typealias LocalizedSlashCommandConfig<L> = SlashCommandBuilder.(localization: L) -> Unit
//TODO typealias LocalizedEntrypointConfig<L> = GenericCommandBuilder<EntrypointCommandContext>.(localization: L) -> Unit

interface CommandConfig<C> {
    fun defaultMemberPermission(permission: DefaultMemberPermissions)

    fun defaultEnabledFor(vararg permissions: Permission) = defaultMemberPermission(DefaultMemberPermissions.enabledFor(*permissions))
    fun defaultDisabled() = defaultMemberPermission(DefaultMemberPermissions.DISABLED)

    fun interactionContextTypes(types: Collection<InteractionContextType>)
    fun integrationTypes(types: Collection<IntegrationType>)

    fun interactionContextTypes(vararg types: InteractionContextType) = interactionContextTypes(types.asList())
    fun integrationTypes(vararg types: IntegrationType) = integrationTypes(types.asList())

    fun before(handler: BeforeHandler<in C>)
    fun before(inherit: Boolean = false, handler: CommandHandler<C>) = before(object : BeforeHandler<C> {
        override val inherit: Boolean get() = inherit
        override suspend fun invoke(context: C) = context.handler()
    })
}

@CommandMarker
open class GenericCommandBuilder<C : ICommandContext<*>>(val manager: CommandManager, val command: CommandImpl<C, *>?) : CommandConfig<C> {
    internal val before = mutableListOf<BeforeHandler<in C>>()

    internal val handlers = mutableListOf<CommandHandler<C>>()
    internal val setup = mutableListOf<Any?>()

    internal var defaultMemberPermission: DefaultMemberPermissions? = null
    internal val contexts = manager.defaultInteractionContextTypes.toMutableSet()
    internal val types = manager.defaultIntegrationTypes.toMutableSet()

    private var currentSetup = 0

    override fun defaultMemberPermission(permission: DefaultMemberPermissions) {
        this.defaultMemberPermission = permission
    }

    override fun interactionContextTypes(types: Collection<InteractionContextType>) {
        this.contexts += types
    }

    override fun integrationTypes(types: Collection<IntegrationType>) {
        this.types += types
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> setup(value: DiscordToolKit<*>.() -> T): T {
        return if (command == null) {
            val temp = manager.manager.value()
            setup += temp

            temp
        } else command.setup[currentSetup++] as T
    }

    fun execute(handler: CommandHandler<C>) {
        handlers += handler
    }

    override fun before(handler: BeforeHandler<in C>) {
        before += handler
    }
}

class SlashCommandBuilder(manager: CommandManager, command: SlashCommandImpl?) : GenericCommandBuilder<SlashCommandContext>(manager, command), OptionConfig {
    internal var inheritConditions = true

    internal val options = mutableListOf<OptionInfo>()
    internal val subcommands = mutableListOf<SlashCommand>()

    fun ignoreParentConditions() {
        inheritConditions = false
    }

    override fun <T> option(data: OptionInfo): Option<OptionalOption<T>> {
        if (command == null) options += data
        return object : RichOption<OptionalOption<T>> {
            override val data: OptionInfo = data
            override suspend fun invoke(context: SlashCommandContext) = OptionalOption<T>(context.parseOption(data.name), context.hasOption(data.name))
        }
    }

    operator fun SlashCommand.unaryPlus() {
        if (command != null) return
        this@SlashCommandBuilder.subcommands += this@unaryPlus
    }
}

fun slashCommand(
    name: String,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    localization: LocalizationFile? = null,
    config: SlashCommandConfig
): SlashCommand = { parent ->
    val manager = this

    val builder = SlashCommandBuilder(manager, null)
    builder.config()

    val options = mutableMapOf<String, OptionInfo>()
    val subcommands = mutableMapOf<String, SlashCommandImpl>()

    lateinit var command: SlashCommandImpl
    command = object : SlashCommandImpl(name, description, parent, localization, options, subcommands, builder.setup, builder.before, builder.inheritConditions, builder.handlers.isNotEmpty(), builder.defaultMemberPermission, builder.contexts, builder.types) {
        override suspend fun handle(context: SlashCommandContext) {
            val executor = SlashCommandBuilder(manager, this)
            executor.config()

            command.effectiveConditions().forEach { handler -> handler(context) }
            executor.handlers.forEach { handler -> context.handler() }
        }
    }

    options += builder.options.associateBy { it.name }
    subcommands += builder.subcommands.map { it(command) }.associateBy { it.name }

    command
}

fun <C : ContextCommandContext<*, *>> contextCommand(
    name: String,
    type: Command.Type,
    localization: LocalizationFile? = null,
    config: ContextCommandConfig<C>
): ContextCommand<C> = {
    val manager = this

    val builder = GenericCommandBuilder<C>(manager, null)
    builder.config()

    lateinit var command: ContextCommandImpl<C>
    command = object : ContextCommandImpl<C>(type, name, localization, builder.setup, builder.before, builder.defaultMemberPermission, builder.contexts, builder.types) {
        override suspend fun handle(context: C) {
            val executor = GenericCommandBuilder(manager, this)
            executor.config()

            command.effectiveConditions().forEach { handler -> handler(context) }
            executor.handlers.forEach { handler -> context.handler() }
        }
    }
    command
}

fun messageCommand(
    name: String,
    localization: LocalizationFile? = null,
    config: ContextCommandConfig<MessageCommandContext>
) = contextCommand(name, Command.Type.MESSAGE, localization, config)

fun userCommand(
    name: String,
    localization: LocalizationFile? = null,
    config: ContextCommandConfig<UserCommandContext>
) = contextCommand(name, Command.Type.USER, localization, config)

/* TODO
fun entrypointCommand(
    name: String,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    permission: DefaultMemberPermissions? = null,
    contexts: Set<InteractionContextType>? = null,
    types: Set<IntegrationType>? = null,
    localization: LocalizationFile? = null
): EntrypointCommand = {
    object : EntryPointCommandImpl(name, description, localization, emptyList(), emptyList(), permission, contexts ?: defaultInteractionContextTypes.toMutableSet(), types ?: defaultIntegrationTypes.toMutableSet(), PrimaryEntryPointCommandData.Handler.DISCORD_LAUNCH_ACTIVITY) {
        override suspend fun handle(context: EntrypointCommandContext) {}
    }
}*/

/* TODO
fun entrypointCommand(
    name: String,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    localization: LocalizationFile? = null,
    config: EntrypointConfig
): EntrypointCommand = {
    val manager = this

    val builder = GenericCommandBuilder<EntrypointCommandContext>(manager, null)
    builder.config()

    lateinit var command: EntryPointCommandImpl
    command = object : EntryPointCommandImpl(name, description, localization, builder.setup, builder.before, builder.defaultMemberPermission, builder.contexts, builder.types, PrimaryEntryPointCommandData.Handler.APP_HANDLER) {
        override suspend fun handle(context: EntrypointCommandContext) {
            val executor = GenericCommandBuilder(manager, this)
            executor.config()

            command.effectiveConditions().forEach { handler -> handler(context) }
            executor.handlers.forEach { handler -> context.handler() }
        }
    }
    command
}
 */

inline fun <reified L : LocalizationFile> localizedSlashCommand(
    name: String,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    crossinline config: LocalizedSlashCommandConfig<L>
): SlashCommand = {
    val file = manager.localizationManager.read<L>()
    slashCommand(name, description, file) { config(file) }(it)
}

inline fun <reified L : LocalizationFile> localizedMessageCommand(
    name: String,
    crossinline config: LocalizedContextCommandConfig<MessageCommandContext, L>
): ContextCommand<MessageCommandContext> = {
    val file = manager.localizationManager.read<L>()
    messageCommand(name, file) { config(file) }(it)
}

inline fun <reified L : LocalizationFile> localizedUserCommand(
    name: String,
    crossinline config: LocalizedContextCommandConfig<UserCommandContext, L>
): ContextCommand<UserCommandContext> = {
    val file = manager.localizationManager.read<L>()
    userCommand(name, file) { config(file) }(it)
}

/* TODO
inline fun <reified L : LocalizationFile> localizedEntrypointCommand(
    name: String,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    permission: DefaultMemberPermissions? = null,
    contexts: Set<InteractionContextType>? = null,
    types: Set<IntegrationType>? = null
): EntrypointCommand = {
    val file = manager.localizationManager.read<L>()
    entrypointCommand(name, description, permission, contexts, types, file)(it)
}

inline fun <reified L : LocalizationFile> localizedEntrypointCommand(
    name: String,
    description: String = DEFAULT_COMMAND_DESCRIPTION,
    crossinline config: LocalizedEntrypointConfig<L>
): EntrypointCommand = {
    val file = manager.localizationManager.read<L>()
    entrypointCommand(name, description, file) { config(file) }(it)
}
 */
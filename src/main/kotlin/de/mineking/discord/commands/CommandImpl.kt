package de.mineking.discord.commands

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command.Type
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.*
import net.dv8tion.jda.api.interactions.commands.build.attributes.IRestrictedCommandData
import net.dv8tion.jda.api.interactions.commands.build.attributes.IScopedCommandData

class CommandTermination : RuntimeException()

fun ICommandContext<*>.terminateCommand(): Nothing = throw CommandTermination()

typealias Command<C> = CommandManager.(parent: SlashCommandImpl?) -> C

typealias ContextCommand<C> = Command<ContextCommandImpl<C>>
typealias EntrypointCommand = Command<EntryPointCommandImpl>
typealias SlashCommand = Command<SlashCommandImpl>

sealed class CommandImpl<C : ICommandContext<*>, D>(
    val name: String,
    val parent: CommandImpl<C, D>? = null,
    val localization: LocalizationFile? = null,
    internal val setup: List<Any?>,
    internal val conditions: List<BeforeHandler<in C>>,
    val defaultMemberPermissions: DefaultMemberPermissions?,
    val contexts: Set<InteractionContextType>,
    val types: Set<IntegrationType>
) : IMentionable where D : IScopedCommandData, D : IRestrictedCommandData {
    internal var idLong: Long? = null

    val root: CommandImpl<*, *> = parent?.root ?: this
    val path: List<String> = (parent?.path ?: emptyList()) + name

    fun effectiveLocalization(): LocalizationFile? = localization ?: parent?.effectiveLocalization()
    internal open fun effectiveConditions(): List<BeforeHandler<in C>> = conditions

    fun getDiscordPath(skip: Int = 0): String {
        val result = StringBuilder()

        for (index in skip until path.size) {
            result.append(path[index])
            if (index != path.lastIndex) result.append(if (index < 2) " " else "_")
        }

        return result.toString()
    }

    override fun getIdLong() = root.idLong ?: error("Command has no id yet. Register it first and trigger a command update")
    override fun getAsMention() = "</${getDiscordPath()}:$id>"

    abstract fun handle(context: C)
    abstract fun build(manager: CommandManager): D

    protected fun finalize(data: D) {
        data.setContexts(contexts)
        data.setIntegrationTypes(types)

        if (defaultMemberPermissions != null) data.defaultPermissions = defaultMemberPermissions
    }
}

abstract class EntryPointCommandImpl(
    name: String,
    val description: String,
    localization: LocalizationFile? = null,
    setup: List<Any?>,
    conditions: List<BeforeHandler<in EntrypointCommandContext>>,
    defaultMemberPermissions: DefaultMemberPermissions?,
    contexts: Set<InteractionContextType>,
    types: Set<IntegrationType>,
    val handler: EntryPointCommandData.Handler
) : CommandImpl<EntrypointCommandContext, EntryPointCommandData>(name, null, localization, setup, conditions, defaultMemberPermissions, contexts, types) {
    override fun build(manager: CommandManager): EntryPointCommandData {
        val localization = manager.localization?.getCommandDescription(effectiveLocalization(), this)

        val result = Commands.entryPoint(name, localization?.default ?: description)
        if (localization != null) result.setDescriptionLocalizations(localization.localization)

        result.setHandler(handler)

        finalize(result)
        return result
    }
}

abstract class ContextCommandImpl<C : ContextCommandContext<*, *>>(
    val type: Type,
    name: String,
    localization: LocalizationFile? = null,
    setup: List<Any?>,
    conditions: List<BeforeHandler<in C>>,
    defaultMemberPermissions: DefaultMemberPermissions?,
    contexts: Set<InteractionContextType>,
    types: Set<IntegrationType>
) : CommandImpl<C, CommandData>(name, null, localization, setup, conditions, defaultMemberPermissions, contexts, types) {
    override fun build(manager: CommandManager): CommandData {
        val localization = manager.localization?.getCommandDescription(effectiveLocalization(), this)
        val result = Commands.context(type, localization?.default ?: name)

        if (localization != null) result.setNameLocalizations(localization.localization)
        finalize(result)

        return result
    }

    override fun toString() = "ContextCommand[$type, name = $name]"
}

abstract class SlashCommandImpl(
    name: String,
    val description: String,
    parent: SlashCommandImpl?,
    localization: LocalizationFile? = null,
    val options: Map<String, OptionInfo>,
    val subcommands: Map<String, SlashCommandImpl>,
    setup: List<Any?>,
    conditions: List<BeforeHandler<in SlashCommandContext>>,
    internal val inheritConditions: Boolean,
    val hasExecutor: Boolean,
    defaultMemberPermissions: DefaultMemberPermissions?,
    contexts: Set<InteractionContextType>,
    types: Set<IntegrationType>
) : CommandImpl<SlashCommandContext, SlashCommandData>(name, parent, localization, setup, conditions, defaultMemberPermissions, contexts, types) {
    override fun effectiveConditions() = (if (inheritConditions && parent != null) parent.effectiveConditions().filter { it.inherit } else emptyList()) + super.effectiveConditions()

    fun subcommand(vararg path: String): SlashCommandImpl? =
        if (path.isEmpty()) this
        else subcommands[path[0]]?.subcommand(*path.copyOfRange(1, path.size))

    private fun getSubcommands(flatten: Boolean): List<SlashCommandImpl> = subcommands.values
        .flatMap {
            if (flatten) {
                val temp = it.getSubcommands(true)
                temp + if (it.hasExecutor || temp.isEmpty()) listOf(it) else emptyList()
            } else listOf(it)
        }

    private fun buildOptions(manager: CommandManager): List<OptionData> = options.values.map { it.build(manager, this) }

    private fun buildAsSubcommand(manager: CommandManager, skip: Int): SubcommandData {
        val localization = manager.localization?.getCommandDescription(effectiveLocalization(), this)

        val result = SubcommandData(getDiscordPath(skip), localization?.default ?: description)
        if (localization != null) result.setDescriptionLocalizations(localization.localization)

        result.addOptions(buildOptions(manager))

        return result
    }

    private fun buildAsSubcommandGroup(manager: CommandManager): SubcommandGroupData {
        val localization = manager.localization?.getCommandDescription(effectiveLocalization(), this)

        val result = SubcommandGroupData(name, localization?.default ?: description)
        if (localization != null) result.setDescriptionLocalizations(localization.localization)

        result.addSubcommands(getSubcommands(true).map { it.buildAsSubcommand(manager, 2) })
        if (options.isNotEmpty()) error("Cannot add options to a command group")

        return result
    }

    override fun build(manager: CommandManager): SlashCommandData {
        val localization = manager.localization?.getCommandDescription(effectiveLocalization(), this)

        val result = Commands.slash(name, localization?.default ?: description)
        if (localization != null) result.setDescriptionLocalizations(localization.localization)

        result.addOptions(buildOptions(manager))
        getSubcommands(false).forEach {
            if (it.getSubcommands(false).isEmpty()) result.addSubcommands(it.buildAsSubcommand(manager, 1))
            else result.addSubcommandGroups(it.buildAsSubcommandGroup(manager))
        }

        finalize(result)
        return result
    }

    override fun toString() = "SlashCommand[name = $name]"
}


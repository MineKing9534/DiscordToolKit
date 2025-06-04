package de.mineking.discord.commands

import de.mineking.discord.DiscordToolKit
import de.mineking.discord.Manager
import net.dv8tion.jda.api.events.interaction.command.*
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import kotlin.reflect.KType
import kotlin.reflect.jvm.kotlinProperty
import kotlin.reflect.typeOf

@DslMarker
annotation class CommandMarker

interface CommandExecutor {
    suspend fun <C : ICommandContext<*>> CommandImpl<C, *>.execute(context: C)

    companion object {
        val DEFAULT = object : CommandExecutor {
            override suspend fun <C : ICommandContext<*>> CommandImpl<C, *>.execute(context: C) {
                handle(context)
            }
        }
    }
}

class CommandManager internal constructor(manager: DiscordToolKit<*>) : Manager(manager) {
    val commands: MutableMap<String, CommandImpl<*, out CommandData>> = hashMapOf()
    var entryPoint: EntryPointCommandImpl? = null
        private set

    val optionMappers = DefaultOptionMappers.javaClass.declaredFields.filter { it.kotlinProperty != null }.onEach { it.trySetAccessible() }.map { it.get(DefaultOptionMappers) as OptionMapper<*> }.toMutableList()

    val defaultInteractionContextTypes = mutableSetOf(InteractionContextType.GUILD, InteractionContextType.BOT_DM)
    val defaultIntegrationTypes = mutableSetOf(IntegrationType.GUILD_INSTALL)

    var localization: CommandLocalizationHandler? = null
        private set

    var executor: CommandExecutor = CommandExecutor.DEFAULT

    init {
        manager.listen<GenericCommandInteractionEvent>(this::handleCommand)
        manager.listen<CommandAutoCompleteInteractionEvent>(this::handleAutocomplete)
    }

    fun getOptionMapper(type: KType): OptionMapper<*>? = optionMappers.findLast { it.accepts(this, type) }
    inline fun <reified T> getOptionMapper() = getOptionMapper(typeOf<T>())

    fun localize(localization: CommandLocalizationHandler = DefaultCommandLocalizationHandler("command")) {
        this.localization = localization
    }

    private suspend fun handleCommand(event: GenericCommandInteractionEvent) {
        val command = getCommand(event.fullCommandName) ?: error("Got command interaction event for unknown command ${event.fullCommandName}")

        try {
            val context = when (event) {
                is SlashCommandInteractionEvent -> {
                    require(command is SlashCommandImpl)

                    val optionMap = hashMapOf<String, Any?>()
                    val options = CommandOptions(optionMap)

                    val context = SlashCommandContext(this, event, options)

                    event.options.forEach {
                        val option = command.options[it.name]!!
                        optionMap += option.name to getOptionMapper(option.type)!!.read(this, option.type, context, option.name)
                    }

                    context
                }

                is MessageContextInteractionEvent -> MessageCommandContext(this, event)
                is UserContextInteractionEvent -> UserCommandContext(this, event)
                is PrimaryEntryPointInteractionEvent -> EntrypointCommandContext(this, event)
                else -> error("Unknown command event")
            }

            suspend fun <C : ICommandContext<*>> execute(context: C) {
                with(executor) {
                    @Suppress("UNCHECKED_CAST")
                    (command as CommandImpl<C, *>).handle(context)
                }
            }

            execute(context)
        } catch (_: CommandTermination) {}
    }

    private suspend fun handleAutocomplete(event: CommandAutoCompleteInteractionEvent) {
        val command = getCommand(event.fullCommandName) ?: error("Got autocomplete interaction event for unknown command ${event.fullCommandName}")
        require(command is SlashCommandImpl) //Should always be true

        val option = command.options[event.focusedOption.name] ?: error("Got autocomplete interaction event for unknown option ${event.focusedOption.name}")


        val optionMap = hashMapOf<String, Any?>()
        val options = CommandOptions(optionMap)

        val context = AutocompleteContext<Any?>(this, event, null, options, command, option)

        try {
            event.options.forEach {
                val option = command.options[it.name]!!
                optionMap += option.name to getOptionMapper(option.type)!!.read(this, option.type, context, option.name)
            }

            context.currentValue = options.parseOption(option.name)

            option.autocomplete?.invoke(context)
        } catch (_: CommandTermination) {}
    }

    fun getCommand(name: String): CommandImpl<*, *>? = if (name == entryPoint?.name) entryPoint else commands[name]

    fun <C : CommandImpl<*, out CommandData>> registerCommand(command: Command<C>) = command(null).apply(this::registerCommand)
    fun registerCommand(command: CommandImpl<*, out CommandData>) {
        commands[command.getDiscordPath()] = command
        if (command is SlashCommandImpl) command.subcommands.values.forEach { registerCommand(it) }
    }

    fun primaryEntrypoint(command: EntrypointCommand) = command(null).apply(this::primaryEntrypoint)
    fun primaryEntrypoint(command: EntryPointCommandImpl) {
        require(entryPoint == null) { "Cannot have more than one entrypoint command" }
        entryPoint = command
    }

    operator fun <C: CommandImpl<*, out CommandData>> plusAssign(command: Command<C>) { registerCommand(command) }
    operator fun plusAssign(command: CommandImpl<*, out CommandData>) = registerCommand(command)

    operator fun <C : CommandImpl<*, out CommandData>> Command<C>.unaryPlus() = registerCommand(this)
    operator fun CommandImpl<*, out CommandData>.unaryPlus() = registerCommand(this)

    fun updateCommands() = manager.jda.updateCommands()
        .addCommands(commands.values
             .filter { it.parent == null }
             .map { it.build(this) }
        ).apply { entryPoint?.build(this@CommandManager)?.let { setPrimaryEntryPointCommand(it) } }
        .onSuccess { result -> result.forEach {
            getCommand(it.fullCommandName)!!.idLong = it.idLong
        } }
}
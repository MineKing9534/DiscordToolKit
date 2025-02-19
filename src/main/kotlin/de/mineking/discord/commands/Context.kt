package de.mineking.discord.commands

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.command.*
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import net.dv8tion.jda.api.interactions.commands.PrimaryEntryPointInteraction
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction

@CommandMarker
sealed interface IContext<E : GenericInteractionCreateEvent> {
    val manager: CommandManager
    val event: E
}

sealed interface ICommandContext<E : GenericCommandInteractionEvent> : IContext<E>, CommandInteractionPayload

sealed interface ContextCommandContext<T : Any, E : GenericContextInteractionEvent<T>> : ICommandContext<E>

class MessageCommandContext(
    override val manager: CommandManager,
    override val event: MessageContextInteractionEvent
) : ContextCommandContext<Message, MessageContextInteractionEvent>, MessageContextInteraction by event

class UserCommandContext(
    override val manager: CommandManager,
    override val event: UserContextInteractionEvent
) : ContextCommandContext<User, UserContextInteractionEvent>, UserContextInteraction by event

class EntrypointCommandContext(
    override val manager: CommandManager,
    override val event: PrimaryEntryPointInteractionEvent
) : ICommandContext<PrimaryEntryPointInteractionEvent>, PrimaryEntryPointInteraction by event

interface IOptionContext<E> : IContext<E>, CommandInteractionPayload, OptionContext where E : GenericInteractionCreateEvent, E : CommandInteractionPayload

class SlashCommandContext(
    override val manager: CommandManager,
    override val event: SlashCommandInteractionEvent,
    val options: CommandOptions
) : ICommandContext<SlashCommandInteractionEvent>, IOptionContext<SlashCommandInteractionEvent>, OptionContext by options, SlashCommandInteraction by event

class AutocompleteContext<out T>(
    override val manager: CommandManager,
    override val event: CommandAutoCompleteInteractionEvent,
    currentValue: T?,
    val options: CommandOptions,
    val command: SlashCommandImpl,
    val option: OptionInfo
) : IOptionContext<CommandAutoCompleteInteractionEvent>, OptionContext by options, CommandInteractionPayload by event {
    var currentValue: @UnsafeVariance T? = currentValue
        internal set

    fun replyChoices(choices: List<Choice>) = event.replyChoices(choices.map { it.build(manager, command, option, event.focusedOption.type) }).queue()
    fun replyChoices(vararg choices: Choice) = replyChoices(choices.toList())
}

typealias AutocompleteHandler<T> = AutocompleteContext<T>.() -> Unit

@Suppress("UNCHECKED_CAST")
fun <T, U> AutocompleteHandler<T>.map(mapper: (value: U?) -> T?): AutocompleteHandler<U> = { this@map(AutocompleteContext(manager, event, mapper(currentValue), options, command, option)) }
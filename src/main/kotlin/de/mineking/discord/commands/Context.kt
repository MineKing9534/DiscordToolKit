package de.mineking.discord.commands

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion
import net.dv8tion.jda.api.components.tree.ComponentTree
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.command.*
import net.dv8tion.jda.api.interactions.commands.*
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessagePollData
import java.util.function.Function
import java.util.function.Supplier

@CommandMarker
sealed interface IContext<E : GenericInteractionCreateEvent> {
    val manager: CommandManager
    val event: E
}

sealed interface ICommandContext<E : GenericCommandInteractionEvent> : IContext<E>, CommandInteraction

sealed interface ContextCommandContext<T : Any, E : GenericContextInteractionEvent<T>> : ICommandContext<E>

class MessageCommandContext(
    override val manager: CommandManager,
    override val event: MessageContextInteractionEvent
) : ContextCommandContext<Message, MessageContextInteractionEvent>, MessageContextInteraction by event {
    override fun getTargetType() = event.targetType

    override fun deferReply(ephemeral: Boolean) = event.deferReply(ephemeral)
    override fun reply(message: MessageCreateData) = event.reply(message)
    override fun reply(content: String) = event.reply(content)
    override fun replyPoll(poll: MessagePollData) = event.replyPoll(poll)
    override fun replyEmbeds(embeds: Collection<MessageEmbed?>) = event.replyEmbeds(embeds)
    override fun replyEmbeds(embed: MessageEmbed, vararg embeds: MessageEmbed?) = event.replyEmbeds(embed, *embeds)
    override fun replyComponents(components: Collection<MessageTopLevelComponent?>) = event.replyComponents(components)
    override fun replyComponents(component: MessageTopLevelComponent, vararg other: MessageTopLevelComponent?) = event.replyComponents(component, *other)
    override fun replyComponents(tree: ComponentTree<MessageTopLevelComponentUnion?>) = event.replyComponents(tree)
    override fun replyFormat(format: String, vararg args: Any?) = event.replyFormat(format, *args)
    override fun replyFiles(files: Collection<FileUpload?>) = event.replyFiles(files)
    override fun replyFiles(vararg files: FileUpload?) = event.replyFiles(*files)

    override fun getType() = event.type
    override fun isFromAttachedGuild() = event.isFromAttachedGuild()
    override fun isFromGuild() = event.isFromGuild

    override fun getChannelType() = event.getChannelType()
    override fun getChannelId() = event.getChannelId()
    override fun getMessageChannel() = event.messageChannel
    override fun getGuildLocale() = event.guildLocale

    override fun getId() = event.id
    override fun getTimeCreated() = event.timeCreated

    override fun getFullCommandName() = event.fullCommandName
    override fun getCommandString() = event.commandString
    override fun getCommandId() = event.commandId
    override fun isGlobalCommand() = event.isGlobalCommand

    override fun getOptionsByName(name: String): List<OptionMapping?> = event.getOptionsByName(name)
    override fun getOptionsByType(type: OptionType): List<OptionMapping?> = event.getOptionsByType(type)
    override fun getOption(name: String): OptionMapping? = event.getOption(name)

    override fun <T : Any?> getOption(name: String, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, resolver)
    override fun <T : Any?> getOption(name: String, fallback: T?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
    override fun <T : Any?> getOption(name: String, fallback: Supplier<out T?>?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
}

class UserCommandContext(
    override val manager: CommandManager,
    override val event: UserContextInteractionEvent
) : ContextCommandContext<User, UserContextInteractionEvent>, UserContextInteraction by event {
    override fun getTargetType() = event.targetType

    override fun deferReply(ephemeral: Boolean) = event.deferReply(ephemeral)
    override fun reply(message: MessageCreateData) = event.reply(message)
    override fun reply(content: String) = event.reply(content)
    override fun replyPoll(poll: MessagePollData) = event.replyPoll(poll)
    override fun replyEmbeds(embeds: Collection<MessageEmbed?>) = event.replyEmbeds(embeds)
    override fun replyEmbeds(embed: MessageEmbed, vararg embeds: MessageEmbed?) = event.replyEmbeds(embed, *embeds)
    override fun replyComponents(components: Collection<MessageTopLevelComponent?>) = event.replyComponents(components)
    override fun replyComponents(component: MessageTopLevelComponent, vararg other: MessageTopLevelComponent?) = event.replyComponents(component, *other)
    override fun replyComponents(tree: ComponentTree<MessageTopLevelComponentUnion?>) = event.replyComponents(tree)
    override fun replyFormat(format: String, vararg args: Any?) = event.replyFormat(format, *args)
    override fun replyFiles(files: Collection<FileUpload?>) = event.replyFiles(files)
    override fun replyFiles(vararg files: FileUpload?) = event.replyFiles(*files)

    override fun getType() = event.type
    override fun isFromAttachedGuild() = event.isFromAttachedGuild()
    override fun isFromGuild() = event.isFromGuild

    override fun getChannelType() = event.getChannelType()
    override fun getChannelId() = event.getChannelId()
    override fun getGuildChannel() = event.guildChannel
    override fun getMessageChannel() = event.messageChannel

    override fun getGuildLocale() = event.guildLocale

    override fun getId() = event.id
    override fun getTimeCreated() = event.timeCreated

    override fun getFullCommandName() = event.fullCommandName
    override fun getCommandString() = event.commandString
    override fun getCommandId() = event.commandId
    override fun isGlobalCommand() = event.isGlobalCommand

    override fun getOptionsByName(name: String): List<OptionMapping?> = event.getOptionsByName(name)
    override fun getOptionsByType(type: OptionType): List<OptionMapping?> = event.getOptionsByType(type)
    override fun getOption(name: String): OptionMapping? = event.getOption(name)

    override fun <T : Any?> getOption(name: String, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, resolver)
    override fun <T : Any?> getOption(name: String, fallback: T?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
    override fun <T : Any?> getOption(name: String, fallback: Supplier<out T?>?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
}

class EntrypointCommandContext(
    override val manager: CommandManager,
    override val event: PrimaryEntryPointInteractionEvent
) : ICommandContext<PrimaryEntryPointInteractionEvent>, PrimaryEntryPointInteraction by event {
    override fun deferReply(ephemeral: Boolean) = event.deferReply(ephemeral)
    override fun reply(message: MessageCreateData) = event.reply(message)
    override fun reply(content: String) = event.reply(content)
    override fun replyPoll(poll: MessagePollData) = event.replyPoll(poll)
    override fun replyEmbeds(embeds: Collection<MessageEmbed?>) = event.replyEmbeds(embeds)
    override fun replyEmbeds(embed: MessageEmbed, vararg embeds: MessageEmbed?) = event.replyEmbeds(embed, *embeds)
    override fun replyComponents(components: Collection<MessageTopLevelComponent?>) = event.replyComponents(components)
    override fun replyComponents(component: MessageTopLevelComponent, vararg other: MessageTopLevelComponent?) = event.replyComponents(component, *other)
    override fun replyComponents(tree: ComponentTree<MessageTopLevelComponentUnion?>) = event.replyComponents(tree)
    override fun replyFormat(format: String, vararg args: Any?) = event.replyFormat(format, *args)
    override fun replyFiles(files: Collection<FileUpload?>) = event.replyFiles(files)
    override fun replyFiles(vararg files: FileUpload?) = event.replyFiles(*files)

    override fun getType() = event.type
    override fun isFromAttachedGuild() = event.isFromAttachedGuild()
    override fun isFromGuild() = event.isFromGuild

    override fun getChannelType() = event.getChannelType()
    override fun getChannelId() = event.getChannelId()
    override fun getMessageChannel() = event.messageChannel
    override fun getGuildLocale() = event.guildLocale

    override fun getId() = event.id
    override fun getTimeCreated() = event.timeCreated

    override fun getFullCommandName() = event.fullCommandName
    override fun getCommandString() = event.commandString
    override fun getCommandId() = event.commandId
    override fun isGlobalCommand() = event.isGlobalCommand

    override fun getOptionsByName(name: String): List<OptionMapping?> = event.getOptionsByName(name)
    override fun getOptionsByType(type: OptionType): List<OptionMapping?> = event.getOptionsByType(type)
    override fun getOption(name: String): OptionMapping? = event.getOption(name)

    override fun <T : Any?> getOption(name: String, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, resolver)
    override fun <T : Any?> getOption(name: String, fallback: T?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
    override fun <T : Any?> getOption(name: String, fallback: Supplier<out T?>?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
}

interface IOptionContext<E> : IContext<E>, CommandInteractionPayload, OptionContext where E : GenericInteractionCreateEvent, E : CommandInteractionPayload

class SlashCommandContext(
    override val manager: CommandManager,
    override val event: SlashCommandInteractionEvent,
    val options: CommandOptions
) : ICommandContext<SlashCommandInteractionEvent>, IOptionContext<SlashCommandInteractionEvent>, OptionContext by options, SlashCommandInteraction by event {
    override fun <T> parseOptionOrElse(name: String, other: T) = super<OptionContext>.parseOptionOrElse(name, other)

    override fun deferReply(ephemeral: Boolean) = event.deferReply(ephemeral)
    override fun reply(message: MessageCreateData) = event.reply(message)
    override fun reply(content: String) = event.reply(content)
    override fun replyPoll(poll: MessagePollData) = event.replyPoll(poll)
    override fun replyEmbeds(embeds: Collection<MessageEmbed?>) = event.replyEmbeds(embeds)
    override fun replyEmbeds(embed: MessageEmbed, vararg embeds: MessageEmbed?) = event.replyEmbeds(embed, *embeds)
    override fun replyComponents(components: Collection<MessageTopLevelComponent?>) = event.replyComponents(components)
    override fun replyComponents(component: MessageTopLevelComponent, vararg other: MessageTopLevelComponent?) = event.replyComponents(component, *other)
    override fun replyComponents(tree: ComponentTree<MessageTopLevelComponentUnion?>) = event.replyComponents(tree)
    override fun replyFormat(format: String, vararg args: Any?) = event.replyFormat(format, *args)
    override fun replyFiles(files: Collection<FileUpload?>) = event.replyFiles(files)
    override fun replyFiles(vararg files: FileUpload?) = event.replyFiles(*files)

    override fun getType() = event.type
    override fun isFromAttachedGuild() = event.isFromAttachedGuild()
    override fun isFromGuild() = event.isFromGuild

    override fun getChannelType() = event.getChannelType()
    override fun getChannelId() = event.getChannelId()
    override fun getMessageChannel() = event.messageChannel
    override fun getGuildLocale() = event.guildLocale

    override fun getId() = event.id
    override fun getTimeCreated() = event.timeCreated

    override fun getFullCommandName() = event.fullCommandName
    override fun getCommandString() = event.commandString
    override fun getCommandId() = event.commandId
    override fun isGlobalCommand() = event.isGlobalCommand

    override fun getOptionsByName(name: String): List<OptionMapping?> = event.getOptionsByName(name)
    override fun getOptionsByType(type: OptionType): List<OptionMapping?> = event.getOptionsByType(type)
    override fun getOption(name: String): OptionMapping? = event.getOption(name)

    override fun <T : Any?> getOption(name: String, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, resolver)
    override fun <T : Any?> getOption(name: String, fallback: T?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
    override fun <T : Any?> getOption(name: String, fallback: Supplier<out T?>?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
}

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

    override fun getType() = event.type
    override fun isFromAttachedGuild() = event.isFromAttachedGuild()
    override fun isFromGuild() = event.isFromGuild

    override fun getChannelType() = event.getChannelType()
    override fun getChannelId() = event.getChannelId()
    override fun getGuildChannel() = event.guildChannel
    override fun getMessageChannel() = event.messageChannel
    override fun getGuildLocale() = event.guildLocale

    override fun getId() = event.id
    override fun getTimeCreated() = event.timeCreated

    override fun getFullCommandName() = event.fullCommandName
    override fun getCommandString() = event.commandString
    override fun getCommandId() = event.commandId
    override fun isGlobalCommand() = event.isGlobalCommand

    override fun getOptionsByName(name: String): List<OptionMapping?> = event.getOptionsByName(name)
    override fun getOptionsByType(type: OptionType): List<OptionMapping?> = event.getOptionsByType(type)
    override fun getOption(name: String): OptionMapping? = event.getOption(name)

    override fun <T : Any?> getOption(name: String, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, resolver)
    override fun <T : Any?> getOption(name: String, fallback: T?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
    override fun <T : Any?> getOption(name: String, fallback: Supplier<out T?>?, resolver: Function<in OptionMapping, out T?>): T? = event.getOption(name, fallback, resolver)
}

typealias AutocompleteHandler<T> = suspend AutocompleteContext<T>.() -> Unit

@Suppress("UNCHECKED_CAST")
fun <T, U> AutocompleteHandler<T>.map(mapper: (value: U?) -> T?): AutocompleteHandler<U> = { this@map(AutocompleteContext(manager, event, mapper(currentValue), options, command, option)) }
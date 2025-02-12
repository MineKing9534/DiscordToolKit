package de.mineking.discord.commands

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel

fun OptionConfig.intOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    min: Int? = null,
    max: Int? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Int>? = null
) = option<Int>(name, description, required, localization, choices, {
    if (min != null) setMinValue(min.toLong())
    if (max != null) setMaxValue(max.toLong())
    configurator()
}, autocomplete)

fun OptionConfig.nullableIntOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    min: Int? = null,
    max: Int? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Int>? = null
) = intOption(name, description, false, min, max, localization, choices, configurator, autocomplete).orNull()

fun OptionConfig.requiredIntOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    min: Int? = null,
    max: Int? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Int>? = null
) = intOption(name, description, true, min, max, localization, choices, configurator, autocomplete).get()

fun OptionConfig.longOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    min: Long? = null,
    max: Long? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Long>? = null
) = option<Long>(name, description, required, localization, choices, {
    if (min != null) setMinValue(min)
    if (max != null) setMaxValue(max)
    configurator()
}, autocomplete)

fun OptionConfig.nullableLongOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    min: Long? = null,
    max: Long? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Long>? = null
) = longOption(name, description, false, min, max, localization, choices, configurator, autocomplete).orNull()

fun OptionConfig.requiredLongOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    min: Long? = null,
    max: Long? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Long>? = null
) = longOption(name, description, true, min, max, localization, choices, configurator, autocomplete).get()

fun OptionConfig.doubleOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    min: Double? = null,
    max: Double? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Double>? = null
) = option<Double>(name, description, required, localization, choices, {
    if (min != null) setMinValue(min)
    if (max != null) setMaxValue(max)
    configurator()
}, autocomplete)

fun OptionConfig.nullableDoubleOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    min: Double? = null,
    max: Double? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Double>? = null
) = doubleOption(name, description, false, min, max, localization, choices, configurator, autocomplete).orNull()

fun OptionConfig.requiredDoubleOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    min: Double? = null,
    max: Double? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<Double>? = null
) = doubleOption(name, description, true, min, max, localization, choices, configurator, autocomplete).get()

fun OptionConfig.stringOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    minLength: Int? = null,
    maxLength: Int? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<String>? = null
) = option<String>(name, description, required, localization, choices, {
    if (minLength != null) setMinLength(minLength)
    if (maxLength != null) setMaxLength(maxLength)
    configurator()
}, autocomplete)

fun OptionConfig.nullableStringOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    minLength: Int? = null,
    maxLength: Int? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<String>? = null
) = stringOption(name, description, false, minLength, maxLength, localization, choices, configurator, autocomplete).orNull()

fun OptionConfig.requiredStringOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    minLength: Int? = null,
    maxLength: Int? = null,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    configurator: OptionConfigurator = {},
    autocomplete: AutocompleteHandler<String>? = null
) = stringOption(name, description, true, minLength, maxLength, localization, choices, configurator, autocomplete).get()

inline fun <reified T : Channel> OptionConfig.channelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    localization: LocalizationFile? = null,
    channelTypes: List<ChannelType> = emptyList(),
    noinline configurator: OptionConfigurator = {}
) = option<T>(name, description, required, localization, emptyList(), {
    setChannelTypes(channelTypes)
    configurator()
})

inline fun <reified T : Channel> OptionConfig.nullableChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    channelTypes: List<ChannelType> = emptyList(),
    noinline configurator: OptionConfigurator = {}
) = channelOption<T>(name, description, false, localization, channelTypes, configurator).orNull()

inline fun <reified T : Channel> OptionConfig.requiredChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    channelTypes: List<ChannelType> = emptyList(),
    noinline configurator: OptionConfigurator = {},
) = channelOption<T>(name, description, true, localization, channelTypes, configurator).get()

fun OptionConfig.messageChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    localization: LocalizationFile? = null,
    configurator: OptionConfigurator = {}
) = channelOption<MessageChannel>(name, description, required, localization, ChannelType.entries.filter { it.isMessage }, configurator)

fun OptionConfig.nullableMessageChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    configurator: OptionConfigurator = {}
) = messageChannelOption(name, description, false, localization, configurator).orNull()

fun OptionConfig.requiredMessageChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    configurator: OptionConfigurator = {},
) = messageChannelOption(name, description, true, localization, configurator).get()

fun OptionConfig.audioChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    localization: LocalizationFile? = null,
    configurator: OptionConfigurator = {}
) = channelOption<AudioChannel>(name, description, required, localization, ChannelType.entries.filter { it.isAudio }, configurator)

fun OptionConfig.nullableVoiceChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    configurator: OptionConfigurator = {}
) = audioChannelOption(name, description, false, localization, configurator).orNull()

fun OptionConfig.requiredVoiceChannelOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    configurator: OptionConfigurator = {},
) = audioChannelOption(name, description, true, localization, configurator).get()
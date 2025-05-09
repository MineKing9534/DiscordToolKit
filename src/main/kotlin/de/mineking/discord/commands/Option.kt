package de.mineking.discord.commands

import de.mineking.discord.localization.LocalizationFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability
import kotlin.reflect.typeOf

typealias JDAChoice = net.dv8tion.jda.api.interactions.commands.Command.Choice

data class Choice(
    val label: String,
    val value: Any,
    val localize: Boolean = true
) {
    fun build(manager: CommandManager, command: SlashCommandImpl, option: OptionInfo, type: OptionType): JDAChoice {
        val localization = manager.localization?.getChoiceLabel(if (localize) option.effectiveLocalization(command) else null, command, option, this)
        val result = when (type) {
            OptionType.INTEGER -> JDAChoice(localization?.default ?: label, (value as Number).toLong())
            OptionType.NUMBER -> JDAChoice(localization?.default ?: label, value as Double)
            else -> JDAChoice(localization?.default ?: label, value.toString())
        }

        if (localization != null) result.setNameLocalizations(localization.localization)

        return result
    }
}

fun choice(value: Any, label: String = value.toString(), localize: Boolean = true) = Choice(label, value, localize)
fun autocompleteChoice(value: Any, label: String = value.toString(), localize: Boolean = false) = choice(value, label, localize)

typealias OptionConfigurator = OptionData.() -> Unit

data class OptionInfo(
    val name: String,
    val description: String,
    val required: Boolean,
    val type: KType,
    val choices: List<Choice>,
    val autocomplete: AutocompleteHandler<*>?,
    val localization: LocalizationFile?,
    val config: OptionConfigurator
) {
    fun effectiveLocalization(command: SlashCommandImpl) = localization ?: command.effectiveLocalization()

    fun build(manager: CommandManager, command: SlashCommandImpl): OptionData {
        val mapper = manager.getOptionMapper(type)!!
        val localization = manager.localization?.getOptionDescription(effectiveLocalization(command), command, this)

        val optionType = mapper.getType(manager, type)

        val option = OptionData(optionType, name, localization?.default ?: description, required, autocomplete != null)
            .addChoices(choices.map { it.build(manager, command, this, optionType) })

        if (localization != null) option.setDescriptionLocalizations(localization.localization)

        mapper.configure(manager, command, this, type, option)
        option.config()

        return option
    }
}

interface OptionContext {
    fun hasOption(name: String): Boolean
    fun <T> parseOption(name: String): T

    fun <T> parseOptionOrElse(name: String, other: T) = if (hasOption(name)) parseOption(name) else other
}

data class CommandOptions(val data: Map<String, Any?>) : OptionContext {
    override fun hasOption(name: String): Boolean = name in data

    @Suppress("UNCHECKED_CAST")
    override fun <T> parseOption(name: String) = data[name] as T
}

typealias Option<T> = suspend SlashCommandContext.() -> T

interface RichOption<T> : Option<T> {
    val data: OptionInfo
    val type: KType get() = data.type

    val default: T? get() = null
}

class OptionalOptionSerializer<T>(val dataSerializer: KSerializer<T>) : KSerializer<OptionalOption<T>> {
    override val descriptor = buildClassSerialDescriptor("de.mineking.discord.OptionalOption") {
        element<Boolean>("present")
        element("value", dataSerializer.descriptor)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: OptionalOption<T>) = encoder.encodeStructure(descriptor) {
        encodeBooleanElement(descriptor, 0, value.isPresent())
        encodeNullableSerializableElement(descriptor, 1, dataSerializer, value.get())
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        val present = decodeBooleanElement(descriptor, 0)
        val value = decodeNullableSerializableElement(descriptor, 1, dataSerializer)

        OptionalOption(value, present)
    }
}

@Suppress("UNCHECKED_CAST")
@Serializable(with = OptionalOptionSerializer::class)
class OptionalOption<out T>(private val value: T?, private val present: Boolean) {
    companion object {
        val EMPTY = OptionalOption(null, false)
    }

    fun isPresent() = present
    fun isEmpty() = !present

    fun orNull(): T? = if (present) value else null

    fun get(): T = if (present) value as T else error("")

    inline fun <U> map(mapper: (value: T) -> U): OptionalOption<U> = if (isPresent()) OptionalOption(mapper(get()), true) else EMPTY
    inline fun filter(filter: (value: T) -> Boolean): OptionalOption<T> = if (isPresent() && filter(get())) OptionalOption(get(), true) else EMPTY
}

fun <T> OptionalOption<T>.orElse(other: T) = if (isPresent()) get() else other
fun <T> OptionalOption<T>.or(other: OptionalOption<T>) = if (isPresent()) this else other

inline fun <T, reified U> Option<T>.map(noinline mapper: suspend SlashCommandContext.(value: T) -> U): Option<U> = map(typeOf<U>(), mapper)
fun <T, U> Option<T>.map(type: KType?, mapper: suspend SlashCommandContext.(value: T) -> U): Option<U> = if (this is RichOption<*>) object : RichOption<U> {
    override val data: OptionInfo = this@map.data
    override val type: KType = type!!

    @Suppress("UNCHECKED_CAST")
    override val default: U? = if (type == this@map.type) this@map.default as U? else null

    override suspend fun invoke(context: SlashCommandContext): U = context.mapper(this@map(context))
} else { { mapper(this@map()) } }

fun <T, U> Option<OptionalOption<T>>.mapValue(mapper: suspend SlashCommandContext.(value: T) -> U): Option<OptionalOption<U>> = map { it.map { mapper(it) } }
fun <T> Option<OptionalOption<T>>.filterValue(filter: suspend SlashCommandContext.(value: T) -> Boolean): Option<OptionalOption<T>> = map { it.filter { filter(it) } }

fun <T> Option<OptionalOption<T>>.get(): Option<T> = map(if (this is RichOption) type else null) { it.get() }
fun <T> Option<OptionalOption<T>>.orNull(): Option<T?> = map(if (this is RichOption) type.withNullability(true) else null) { it.orNull() }

fun <T> Option<OptionalOption<T>>.or(other: Option<OptionalOption<T>>) = map { if (it.isPresent()) it else other() }
inline fun <reified T> Option<OptionalOption<T>>.orValue(crossinline other: Option<T>) = map { if (it.isPresent()) it.get() else other() }

inline fun <reified T> Option<OptionalOption<T>>.orElse(noinline value: SlashCommandContext.() -> T): Option<T> = orElse(typeOf<T>(), value)
fun <T> Option<OptionalOption<T>>.orElse(type: KType?, value: SlashCommandContext.() -> T): Option<T> = map(type) { it.orElse(value()) }
inline fun <reified T> Option<OptionalOption<T>>.orElse(value: T): Option<T> = orElse(typeOf<T>(), value)
fun <T> Option<OptionalOption<T>>.orElse(type: KType?, value: T): Option<T> = if (this is RichOption<*>) object : RichOption<T> {
    override val data: OptionInfo = this@orElse.data
    override val type: KType = type!!

    override val default: T = value

    override suspend fun invoke(context: SlashCommandContext): T = this@orElse(context).orElse(value)
} else { { this@orElse().orElse(value) } }

@Suppress("UNCHECKED_CAST")
interface OptionConfig {
    val manager: CommandManager

    fun <T> option(data: OptionInfo): Option<OptionalOption<T>>
    fun <T> nullableOption(data: OptionInfo) = option<T>(data).orNull()
    fun <T> requiredOption(data: OptionInfo) = option<T>(data).get()

    fun <T> option(
        type: KType,
        name: String,
        description: String = DEFAULT_OPTION_DESCRIPTION,
        required: Boolean = false,
        localization: LocalizationFile? = null,
        choices: List<Choice> = emptyList(),
        configurator: OptionConfigurator = {},
        autocomplete: AutocompleteHandler<T>? = null
    ): Option<OptionalOption<T>> = option(OptionInfo(name, description, required, type, choices, autocomplete as AutocompleteHandler<*>?, localization, configurator))

    fun <T> nullableOption(
        type: KType,
        name: String,
        description: String = DEFAULT_OPTION_DESCRIPTION,
        localization: LocalizationFile? = null,
        choices: List<Choice> = emptyList(),
        configurator: OptionConfigurator = {},
        autocomplete: AutocompleteHandler<T>? = null
    ) = option<T?>(type, name, description, false, localization, choices, configurator, autocomplete as AutocompleteHandler<*>?).orNull()

    fun <T> requiredOption(
        type: KType,
        name: String,
        description: String = DEFAULT_OPTION_DESCRIPTION,
        localization: LocalizationFile? = null,
        choices: List<Choice> = emptyList(),
        configurator: OptionConfigurator = {},
        autocomplete: AutocompleteHandler<T>? = null
    ) = option(type, name, description, true, localization, choices, configurator, autocomplete).get()
}

inline fun <reified T> OptionConfig.option(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    noinline configurator: OptionConfigurator = {},
    noinline autocomplete: AutocompleteHandler<T>? = null
) = option(typeOf<T>(), name, description, required, localization, choices, configurator, autocomplete)

inline fun <reified T> OptionConfig.nullableOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    noinline configurator: OptionConfigurator = {},
    noinline autocomplete: AutocompleteHandler<T>? = null
) = nullableOption(typeOf<T>(), name, description, localization, choices, configurator, autocomplete)

inline fun <reified T> OptionConfig.requiredOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    choices: List<Choice> = emptyList(),
    noinline configurator: OptionConfigurator = {},
    noinline autocomplete: AutocompleteHandler<T>? = null
) = requiredOption(typeOf<T>(), name, description, localization, choices, configurator, autocomplete)

inline fun <reified E : Enum<E>> OptionConfig.enumOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    required: Boolean = false,
    localization: LocalizationFile? = null,
    noinline configurator: OptionConfigurator = {},
    label: (value: E) -> String? = { it.name },
    noinline autocomplete: AutocompleteHandler<E>? = null
): Option<OptionalOption<E>> = option<String>(
    name, description, required, localization,
    choices = E::class.java.enumConstants
        .map { it.name to label(it) }
        .filter { it.second != null }
        .map { Choice(it.first, it.second!!) },
    configurator, autocomplete?.map { value -> E::class.java.enumConstants.first { it.name == value } }
).mapValue { value -> E::class.java.enumConstants.first { it.name == value } }

inline fun <reified E : Enum<E>> OptionConfig.nullableEnumOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    noinline configurator: OptionConfigurator = {},
    label: (value: E) -> String? = { it.name },
    noinline autocomplete: AutocompleteHandler<E>? = null
) = enumOption<E>(name, description, false, localization, configurator, label, autocomplete).orNull()

inline fun <reified E : Enum<E>> OptionConfig.requiredEnumOption(
    name: String,
    description: String = DEFAULT_OPTION_DESCRIPTION,
    localization: LocalizationFile? = null,
    noinline configurator: OptionConfigurator = {},
    label: (value: E) -> String? = { it.name },
    noinline autocomplete: AutocompleteHandler<E>? = null
) = enumOption<E>(name, description, true, localization, configurator, label, autocomplete).get()
package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun <T> typedTextInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    value: T? = null,
    localization: LocalizationFile? = null,
    formatter: (value: T) -> String? = { it?.toString() },
    parser: ParseContext<*>.(value: String) -> T
) = createModalElement(name, {
    val temp = event.values.first { it.customId.split(":", limit = 2)[0] == name }.asString
    ParseContext(this).parser(temp)
}) { config, id ->
    TextInput.create(id, config.readLocalizedString(localization, name, label, "label", prefix = "inputs")?.takeIf { it.isNotEmpty() } ?: ZERO_WIDTH_SPACE, style)
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder", prefix = "inputs")?.takeIf { it.isNotBlank() })
        .setValue(value?.let(formatter)?.takeIf { it.isNotBlank() })
        .setMinLength(minLength)
        .setMaxLength(maxLength)
        .setRequired(required)
        .build()
}

@MenuMarker
class ParseContext<M>(context: StateContext<M>) : StateContext<M> by context {
    fun check(validator: () -> Boolean) {
        if (!validator()) throw RenderTermination
    }
}

typealias ResultHandler<T> = StateContext<*>.(value: T) -> Unit

fun textInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    value: String? = null,
    localization: LocalizationFile? = null,
    handler: ResultHandler<String> = {}
) = typedTextInput(name, label, placeholder, style, required, minLength, maxLength, value, localization) {
    handler.invoke(this, it)
    it
}

sealed interface Result<out T> {
    @JvmInline
    value class Success<out T>(val value: T): Result<T>
    object Error : Result<Nothing>
}

@OptIn(ExperimentalContracts::class)
fun <T> Result<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is Result.Success<T>)
    }
    return this is Result.Success<T>
}

fun <T> Result<T>.valueOrNull() = if (isSuccess()) value else null

fun <T> ModalComponent<Result<T>>.unbox() = map { it.valueOrNull() }

fun intInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int? = null,
    max: Int? = null,
    value: Int? = null,
    localization: LocalizationFile? = null,
    handler: ResultHandler<Result<Int?>> = {}
) = typedTextInput(name, label, placeholder, TextInputStyle.SHORT, required, value = value?.let { Result.Success(it) }, localization = localization, formatter = { it.valueOrNull()?.toString() }) {
    val result = try {
        val value = it.takeIf { it.isNotBlank() }?.toInt()
        if (value != null) check { (min == null || value >= min) && (max == null || value <= max) }

        Result.Success(value)
    } catch (_: NumberFormatException) {
        Result.Error
    }

    handler(this, result)
    result
}

fun statefulTextInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    localization: LocalizationFile? = null,
    ref: State<String>,
    handler: ResultHandler<String> = {}
): ModalComponent<String> {
    var value by ref
    return textInput(name, label, placeholder, style, required, minLength, maxLength, value, localization) {
        value = it
        handler(this, it)
    }
}

fun statefulIntInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int? = null,
    max: Int? = null,
    localization: LocalizationFile? = null,
    ref: State<Int?>,
    handler: ResultHandler<Result<Int?>> = {}
): ModalComponent<Result<Int?>> {
    var value by ref
    return intInput(name, label, placeholder, required, min, max, value, localization) {
        if (it.isSuccess()) value = it.value
        handler(this, it)
    }
}
package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle

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

data class IntParseResult(val value: Int?, val error: Boolean)
fun ModalComponent<IntParseResult>.unbox() = map { it.value }

fun intInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int? = null,
    max: Int? = null,
    value: Int? = null,
    localization: LocalizationFile? = null,
    handler: ResultHandler<IntParseResult> = {}
) = typedTextInput(name, label, placeholder, TextInputStyle.SHORT, required, value = value?.let { IntParseResult(it, false) }, localization = localization, formatter = { it.value?.toString() }) {
    val result = try {
        IntParseResult(it.takeIf { it.isNotBlank() }?.toInt(), false)
    } catch (_: NumberFormatException) {
        IntParseResult(null, true)
    }

    if (result.value != null) {
        check { (min == null || result.value >= min) && (max == null || result.value <= max) }
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
    ref: State<Int>,
    handler: ResultHandler<IntParseResult> = {}
): ModalComponent<IntParseResult> {
    var value by ref
    return intInput(name, label, placeholder, required, min, max, value, localization) {
        if (it.value != null) value = it.value
        handler(this, it)
    }
}
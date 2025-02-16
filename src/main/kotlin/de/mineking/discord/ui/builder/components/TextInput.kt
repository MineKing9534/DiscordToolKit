package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import sun.jvm.hotspot.oops.CellTypeState
import sun.jvm.hotspot.oops.CellTypeState.ref
import kotlin.invoke

fun <T> typedTextInput(
    name: String,
    label: String = DEFAULT_LABEL,
    placeholder: String? = null,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    value: T? = null,
    localization: LocalizationFile? = null,
    formatter: (value: T) -> String = { it.toString() },
    parser: ParseContext<*>.(value: String) -> T
) = modalElement(name, {
    TextInput.create(it, label, style)
        .setRequired(required)
        .setPlaceholder(placeholder)
        .setMinLength(minLength)
        .setMaxLength(maxLength)
        .setValue(value?.let(formatter)?.takeIf { it.isNotBlank() })
        .build()
}, localization) {
    val temp = event.values.first { it.id.split(":", limit = 2)[0] == name }.asString
    ParseContext(this).parser(temp)
}

@MenuMarker
class ParseContext<M>(context: StateContext<M>) : StateContext<M> by context {
    fun validate(validator: () -> Boolean) {
        if (!validator()) throw RenderTermination
    }
}

typealias ResultHandler<T> = StateContext<*>.(value: T) -> Unit

fun textInput(
    name: String,
    label: String = DEFAULT_LABEL,
    placeholder: String? = null,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    value: String? = null,
    localization: LocalizationFile? = null,
    handler: ResultHandler<String>? = null
) = typedTextInput(name, label, placeholder, style, required, minLength, maxLength, value, localization) {
    handler?.invoke(this, it)
    it
}

fun intInput(
    name: String,
    label: String = DEFAULT_LABEL,
    placeholder: String? = null,
    required: Boolean = true,
    min: Int = 0,
    max: Int = TextInput.MAX_VALUE_LENGTH,
    value: Int? = null,
    localization: LocalizationFile? = null,
    handler: ResultHandler<Int>? = null
) = typedTextInput(name, label, placeholder, TextInputStyle.SHORT, required, value = value, localization = localization) {
    val value = it.toIntOrNull()
    if (value != null) {
        validate { value >= min && value <= max }
        handler?.invoke(this, value)
    }

    value
}

fun statefulTextInput(
    name: String,
    label: String = DEFAULT_LABEL,
    placeholder: String? = null,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    localization: LocalizationFile? = null,
    ref: State<String>,
    handler: ResultHandler<String>? = null
): IModalComponent<String> {
    var value by ref
    return textInput(name, label, placeholder, style, required, minLength, maxLength, value, localization) {
        value = it
        handler?.invoke(this, it)
    }
}

fun StateContext<*>.intInput(
    name: String,
    label: String = DEFAULT_LABEL,
    placeholder: String? = null,
    required: Boolean = true,
    min: Int = 0,
    max: Int = TextInput.MAX_VALUE_LENGTH,
    localization: LocalizationFile? = null,
    ref: State<Int>,
    handler: ResultHandler<Int>? = null
): IModalComponent<Int?> {
    var value by ref
    return intInput(name, label, placeholder, required, min, max, value, localization) {
        value = it
        handler?.invoke(this, it)
    }
}
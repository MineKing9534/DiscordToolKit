package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.modal.ModalComponent
import de.mineking.discord.ui.modal.ModalContext
import de.mineking.discord.ui.modal.createModalElement
import de.mineking.discord.ui.modal.map
import de.mineking.discord.ui.readLocalizedString
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun textInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    localization: LocalizationFile? = null,
    value: String? = null,
    handler: ResultHandler<String>? = null
) = createModalElement(name, {
    val temp = event.values.first { it.customId.split(":", limit = 2)[0] == name }.asString
    temp.also { handler?.invoke(this, it) }
}) { config, id ->
    TextInput.create(id, config.readLocalizedString(localization, name, label, "label", prefix = "inputs")?.takeIf { it.isNotEmpty() } ?: ZERO_WIDTH_SPACE, style)
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder", prefix = "inputs")?.takeIf { it.isNotBlank() })
        .setValue(value?.takeIf { it.isNotBlank() })
        .setMinLength(minLength)
        .setMaxLength(maxLength)
        .setRequired(required)
        .build()
}

fun <T> typedTextInput(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    placeholder: CharSequence? = DEFAULT_LABEL,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    localization: LocalizationFile? = null,
    value: T? = null,
    formatter: (value: T) -> String? = { it?.toString() },
    parser: ModalContext<*>.(value: String) -> T
) = textInput(name, label, placeholder, style, required, minLength, maxLength, localization, value?.let(formatter))
    .map { parser(it) }

typealias ResultHandler<T> = ModalContext<*>.(value: T) -> Unit


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
    value: Int? = null,
    localization: LocalizationFile? = null,
    check: ((Int) -> Boolean)? = null,
    handler: ResultHandler<Result<Int?>>? = null
) = typedTextInput(name, label, placeholder, TextInputStyle.SHORT, required, value = value?.let { Result.Success(it) }, localization = localization, formatter = { it.valueOrNull()?.toString() }) {
    val result = try {
        val value = it.takeIf { it.isNotBlank() }?.toInt()

        if (value != null && check?.invoke(value) == false) Result.Error
        else Result.Success(value)
    } catch (_: NumberFormatException) {
        Result.Error
    }

    handler?.invoke(this, result)
    result
}
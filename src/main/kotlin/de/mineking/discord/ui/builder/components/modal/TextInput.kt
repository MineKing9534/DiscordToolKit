package de.mineking.discord.ui.builder.components.modal

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.localizationPrefix
import de.mineking.discord.ui.modal.*
import de.mineking.discord.ui.readLocalizedString
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.absoluteValue

fun textInput(
    name: String,
    placeholder: CharSequence? = DEFAULT_LABEL,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    localization: LocalizationFile? = null,
    value: String? = null,
    handler: ModalResultHandler<String>? = null
) = createModalElement(name, {
    val temp = event.getValueByUniqueId(name.hashCode().absoluteValue)!!.asString
    temp.also { handler?.invoke(this, it) }
}) { config, id ->
    TextInput.create(id, style)
        .setUniqueId(name.hashCode().absoluteValue)
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder", prefix = config.localizationPrefix())?.takeIf { it.isNotBlank() })
        .setValue(value?.takeIf { it.isNotBlank() })
        .setMinLength(minLength)
        .setMaxLength(maxLength)
        .setRequired(required)
        .build()
}

fun <T> typedTextInput(
    name: String,
    placeholder: CharSequence? = DEFAULT_LABEL,
    style: TextInputStyle = TextInputStyle.SHORT,
    required: Boolean = true,
    minLength: Int = 0,
    maxLength: Int = TextInput.MAX_VALUE_LENGTH,
    localization: LocalizationFile? = null,
    value: T? = null,
    formatter: (value: T) -> String? = { it?.toString() },
    parser: ModalContext<*>.(value: String) -> T
)= textInput(name, placeholder, style, required, minLength, maxLength, localization, value?.let(formatter))
    .map { parser(it) }


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

fun <C : Component, T> ModalComponent<C, Result<T>>.unbox() = map { it.valueOrNull() }
fun <C : Component, T> ModalElement<C, Result<T>>.unbox() = map { it.valueOrNull() }

fun intInput(
    name: String,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    value: Int? = null,
    localization: LocalizationFile? = null,
    check: ((Int) -> Boolean)? = null,
    handler: ModalResultHandler<Result<Int?>>? = null
) = typedTextInput(name, placeholder, TextInputStyle.SHORT, required, value = value?.let { Result.Success(it) }, localization = localization, formatter = { it.valueOrNull()?.toString() }) {
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
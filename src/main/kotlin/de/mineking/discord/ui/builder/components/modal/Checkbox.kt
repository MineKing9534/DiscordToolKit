package de.mineking.discord.ui.builder.components.modal

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.MenuConfig
import de.mineking.discord.ui.modal.ModalResultHandler
import de.mineking.discord.ui.modal.createModalElement
import de.mineking.discord.ui.modal.map
import de.mineking.discord.ui.readLocalizedString
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.checkbox.Checkbox
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup
import net.dv8tion.jda.api.components.radiogroup.RadioGroup
import kotlin.math.absoluteValue
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroupOption as JDACheckboxGroupOption
import net.dv8tion.jda.api.components.radiogroup.RadioGroupOption as JDARadioGroupOption

fun checkbox(
    name: String,
    default: Boolean = false,
    handler: ModalResultHandler<Boolean>? = null
) = createModalElement(name, {
    val temp = event.getValueByUniqueId(name.hashCode().absoluteValue)!!.asBoolean
    temp.also { handler?.invoke(this, it) }
}) { _, id ->
    Checkbox.of(id, default)
        .withUniqueId(name.hashCode().absoluteValue)
}

class CheckboxGroupOption(
    val value: String,
    val label: CharSequence,
    val description: CharSequence?,
    val default: Boolean,
    val localization: LocalizationFile?,
    val visible: Boolean,
) {
    fun buildCheckboxOption(name: String, localization: LocalizationFile?, config: MenuConfig<*, *>) =
        JDACheckboxGroupOption.of(config.readLocalizedString(this.localization ?: localization, name, label, "label", postfix = "options.$value") ?: ZERO_WIDTH_SPACE, value)
            .withDescription(config.readLocalizedString(this.localization ?: localization, name, description, "description", postfix = "options.$value"))
            .withDefault(default)
            .withValue(value)

    fun buildRadioOption(name: String, localization: LocalizationFile?, config: MenuConfig<*, *>) =
        JDARadioGroupOption.of(config.readLocalizedString(this.localization ?: localization, name, label, "label", postfix = "options.$value") ?: ZERO_WIDTH_SPACE, value)
            .withDescription(config.readLocalizedString(this.localization ?: localization, name, description, "description", postfix = "options.$value"))
            .withDefault(default)
            .withValue(value)
}

fun CheckboxGroupOption.visibleIf(visible: Boolean) = CheckboxGroupOption(value, label, description, default, localization, visible)
fun CheckboxGroupOption.hiddenIf(hide: Boolean) = visibleIf(!hide)

fun checkboxGroupOption(
    value: Any,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    localization: LocalizationFile? = null,
) = CheckboxGroupOption(value.toString(), label, description, default, localization, true)

fun checkboxGroup(
    name: String,
    options: List<CheckboxGroupOption>,
    required: Boolean = false,
    minValues: Int? = null,
    maxValues: Int? = null,
    localization: LocalizationFile? = null,
    handler: ModalResultHandler<List<String>>? = null,
) = createModalElement(name, {
    val temp = event.getValueByUniqueId(name.hashCode().absoluteValue)!!.asStringList
    temp.also { handler?.invoke(this, it) }
}) { config, id ->
    CheckboxGroup.create(id)
        .setUniqueId(name.hashCode().absoluteValue)
        .setRequired(required)
        .addOptions(options.filter { it.visible }.map { it.buildCheckboxOption(name, localization, config) })
        .apply {
            if (minValues != null) setMinValues(minValues)
            if (maxValues != null) setMaxValues(maxValues)
        }
        .build()
}

fun checkboxGroup(
    name: String,
    vararg options: CheckboxGroupOption,
    required: Boolean = false,
    minValues: Int? = null,
    maxValues: Int? = null,
    localization: LocalizationFile? = null,
    handler: ModalResultHandler<List<String>>? = null,
) = checkboxGroup(name, options.toList(), required, minValues, maxValues, localization, handler)

fun radioGroup(
    name: String,
    options: List<CheckboxGroupOption>,
    required: Boolean = false,
    localization: LocalizationFile? = null,
    handler: ModalResultHandler<String>? = null,
) = createModalElement(name, {
    val temp = event.getValueByUniqueId(name.hashCode().absoluteValue)!!.asString
    temp.also { handler?.invoke(this, it) }
}) { config, id ->
    RadioGroup.create(id)
        .setUniqueId(name.hashCode().absoluteValue)
        .setRequired(required)
        .addOptions(options.filter { it.visible }.map { it.buildRadioOption(name, localization, config) })
        .build()
}

fun radioGroup(
    name: String,
    vararg options: CheckboxGroupOption,
    required: Boolean = false,
    localization: LocalizationFile? = null,
    handler: ModalResultHandler<String>? = null,
) = radioGroup(name, options.toList(), required, localization, handler)

fun requiredCheckbox(
    name: String,
    default: Boolean = false,
) = checkboxGroup(
    name,
    checkboxGroupOption("value", default = default),
    required = true,
    minValues = 1,
).map { "value" in it }

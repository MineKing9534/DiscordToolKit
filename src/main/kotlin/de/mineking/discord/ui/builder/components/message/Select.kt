package de.mineking.discord.ui.builder.components.message

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import de.mineking.discord.ui.message.ComponentHandler
import de.mineking.discord.ui.message.createMessageElement
import de.mineking.discord.ui.modal.ModalResultHandler
import de.mineking.discord.ui.modal.map
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import kotlin.math.absoluteValue

typealias StringSelectHandler = ComponentHandler<*, StringSelectInteractionEvent>
typealias EntitySelectHandler = ComponentHandler<*, EntitySelectInteractionEvent>

typealias JDASelectOption = net.dv8tion.jda.api.components.selections.SelectOption

class SelectOption(
    val value: String,
    val label: CharSequence,
    val description: CharSequence?,
    val default: Boolean,
    val emoji: Emoji?,
    val localization: LocalizationFile?,
    val visible: Boolean,
    val handler: StringSelectHandler?
) {
    fun withDefault(default: Boolean) = SelectOption(value, label, description, default, emoji, localization, visible, handler)

    fun build(name: String, localization: LocalizationFile?, config: MenuConfig<*, *>) =
        JDASelectOption.of(config.readLocalizedString(this.localization ?: localization, name, label, "label", postfix = "options.$value") ?: ZERO_WIDTH_SPACE, value)
            .withDescription(config.readLocalizedString(this.localization ?: localization, name, description, "description", postfix = "options.$value"))
            .withEmoji(emoji)
            .withDefault(default)
            .withValue(value)
}

fun SelectOption.visibleIf(visible: Boolean) = SelectOption(value, label, description, default, emoji, localization, visible, handler)
fun SelectOption.hiddenIf(hide: Boolean) = visibleIf(!hide)

fun selectOption(
    value: Any,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    handler: StringSelectHandler? = null
) = SelectOption(value.toString(), label, description, default, emoji, localization, true, handler)

fun stringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<String>>? = null,
    handler: StringSelectHandler? = null
) = createSharedElement<StringSelectMenu, StringSelectInteractionEvent, List<String>>(name, {
    options.filter { it.value in event.values }.forEach { it.handler?.invoke(this) }
    handler?.invoke(this)
}, {
    val temp = event.getValueByUniqueId(name.hashCode().absoluteValue)!!.asStringList
    temp.also { modalHandler?.invoke(this, it) }
}) { config, id ->
    val select = StringSelectMenu.create(id)
        .setUniqueId(name.hashCode().absoluteValue)
        .addOptions(options.filter { it.visible }.map { it.build(name, localization, config) })
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder", prefix = config.localizationPrefix()))
        .setMinValues(min)
        .setMaxValues(max)
        .setRequired(required)

    if (options.isEmpty()) select.addOption("---", "---").isDisabled = true

    select.build()
}

fun stringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<String>>? = null,
    handler: StringSelectHandler? = null
) = stringSelect(name, options.toList(), placeholder, required, min, max, localization, modalHandler, handler)

fun statefulMultiStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<String>>? = null,
    ref: MutableState<List<String>>,
    handler: StringSelectHandler? = null
) = stringSelect(name, options.map { it.withDefault(it.value in ref.value) }, placeholder, required, min, max, localization, {
    ref.value = it
    modalHandler?.invoke(this, it)
}) {
    ref.value = event.values
    handler?.invoke(this)
}

fun statefulMultiStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<String>>? = null,
    ref: MutableState<List<String>>,
    handler: StringSelectHandler? = null
) = statefulMultiStringSelect(name, options.toList(), placeholder, required, min, max, localization, modalHandler, ref, handler)

internal fun String?.toList() = if (this == null) emptyList() else listOf(this)
fun statefulOptionalSingleStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<String?>? = null,
    ref: MutableState<String?>,
    handler: StringSelectHandler? = null
) = statefulMultiStringSelect(
    name, options, placeholder, required, min = 0, max = 1, localization,
    modalHandler?.map { it.firstOrNull() },
    ref = ref.map({ it.toList() }, { it.firstOrNull() }), handler = handler
)

fun statefulOptionalSingleStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<String?>? = null,
    ref: MutableState<String?>,
    handler: StringSelectHandler? = null
) = statefulOptionalSingleStringSelect(name, options.toList(), placeholder, required, localization, modalHandler, ref, handler)

fun statefulSingleStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<String>? = null,
    ref: MutableState<String>,
    handler: StringSelectHandler? = null
) = statefulMultiStringSelect(
    name, options, placeholder, required, min = 1, max = 1, localization,
    modalHandler?.map { it.first() },
    ref = ref.map({ it.toList() }, { it.first() }), handler = handler
)

fun statefulSingleStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<String?>? = null,
    ref: MutableState<String>,
    handler: StringSelectHandler? = null
) = statefulSingleStringSelect(name, options.toList(), placeholder, required, localization, modalHandler, ref, handler)

fun entitySelect(
    name: String,
    vararg targets: EntitySelectMenu.SelectTarget,
    channelTypes: Collection<ChannelType> = emptyList(),
    default: Collection<EntitySelectMenu.DefaultValue> = emptyList(),
    placeholder: CharSequence? = DEFAULT_LABEL,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    handler: EntitySelectHandler? = null
) = createMessageElement(name, handler) { config, id ->
    EntitySelectMenu.create(id, targets.toList())
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder", prefix = config.localizationPrefix()))
        .setMinValues(min)
        .setMaxValues(max)
        .setChannelTypes(channelTypes)
        .setDefaultValues(default)
        .build()
}
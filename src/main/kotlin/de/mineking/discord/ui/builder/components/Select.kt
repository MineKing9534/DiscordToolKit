package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import java.util.*

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

    fun show(visible: Boolean) = SelectOption(value, label, description, default, emoji, localization, visible, handler)
    fun hide(hide: Boolean) = show(!hide)

    suspend fun build(name: String, localization: LocalizationFile?, config: MenuConfig<*, *>) =
        JDASelectOption.of(config.readLocalizedString(this.localization ?: localization, name, label, "label", postfix = "options.$value") ?: ZERO_WIDTH_SPACE, value)
            .withDescription(config.readLocalizedString(this.localization ?: localization, name, description, "description", postfix = "options.$value"))
            .withEmoji(emoji)
            .withDefault(default)
            .withValue(value)
}

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
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    handler: StringSelectHandler = {}
) = createMessageElement<ActionRow, StringSelectInteractionEvent>(name, {
    options.filter { it.value in event.values }.forEach { it.handler?.invoke(this) }
    handler()
}) { config, id ->
    val select = StringSelectMenu.create(id)
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder"))
        .setMinValues(min)
        .setMaxValues(max)
        .addOptions(options.filter { it.visible }.map { it.build(name, localization, config) })

    if (options.isEmpty()) select.addOption("---", "---").isDisabled = true

    ActionRow.of(select.build())
}

fun stringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    handler: StringSelectHandler = {}
) = stringSelect(name, options.toList(), placeholder, min, max, localization, handler)

fun statefulMultiStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: CharSequence? = DEFAULT_LABEL,
    min: Int = 1,
    max: Int = 1,
    ref: State<List<String>>,
    handler: StringSelectHandler = {}
) = stringSelect(name, options.map { it.withDefault(it.value in ref.get(null)) }, placeholder, min, max) {
    ref.set(null, event.values)
    handler()
}

fun statefulMultiStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    min: Int = 1,
    max: Int = 1,
    ref: State<List<String>>,
    handler: StringSelectHandler = {}
) = statefulMultiStringSelect(name, options.toList(), placeholder, min, max, ref, handler)

internal fun String?.toList() = if (this == null) emptyList() else listOf(this)
fun statefulOptionalSingleStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: CharSequence? = DEFAULT_LABEL,
    ref: State<String?>,
    handler: StringSelectHandler = {}
) = statefulMultiStringSelect(name, options, placeholder, min = 0, max = 1, ref = ref.transform({ it.toList() }, { it.firstOrNull() }), handler = handler)

fun statefulOptionalSingleStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    ref: State<String?>,
    handler: StringSelectHandler = {}
) = statefulOptionalSingleStringSelect(name, options.toList(), placeholder, ref, handler)

fun statefulSingleStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: CharSequence? = DEFAULT_LABEL,
    ref: State<String>,
    handler: StringSelectHandler = {}
) = statefulMultiStringSelect(name, options, placeholder, min = 1, max = 1, ref = ref.transform({ it.toList() }, { it.first() }), handler = handler)

fun statefulSingleStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: CharSequence? = DEFAULT_LABEL,
    ref: State<String>,
    handler: StringSelectHandler = {}
) = statefulSingleStringSelect(name, options.toList(), placeholder, ref, handler)

inline fun <reified E : Enum<E>> enumSelect(
    name: String,
    placeholder: CharSequence? = DEFAULT_LABEL,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    noinline handler: StringSelectHandler = {}
) = stringSelect(name, E::class.java.enumConstants.map { label(it.name, it) }, placeholder, min, max, localization, handler)

inline fun <reified E : Enum<E>> statefulMultiEnumSelect(
    name: String,
    placeholder: CharSequence? = DEFAULT_LABEL,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    ref: State<EnumSet<E>>,
    noinline handler: StringSelectHandler = {}
) = enumSelect<E>(name, placeholder, min, max, localization, { id, e -> label(id, e).withDefault(id in ref.get(null).map { it.name }) }) {
    val temp = E::class.java.enumConstants.filter { it.name in event.values }
    ref.set(null, if (temp.isNotEmpty()) EnumSet.copyOf(temp) else EnumSet.noneOf(E::class.java))

    handler()
}

inline fun <reified E : Enum<E>> E?.toEnumSet(): EnumSet<E> = if (this == null) EnumSet.noneOf(E::class.java) else EnumSet.of(this)
inline fun <reified E : Enum<E>> statefulOptionalSingleEnumSelect(
    name: String,
    placeholder: CharSequence? = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    ref: State<E?>,
    noinline handler: StringSelectHandler = {}
) = statefulMultiEnumSelect(name, placeholder, min = 0, max = 1, localization, label, ref = ref.transform({ it.toEnumSet() }, { it.firstOrNull() }), handler = handler)

inline fun <reified E : Enum<E>> statefulSingleEnumSelect(
    name: String,
    placeholder: CharSequence? = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    ref: State<E>,
    noinline handler: StringSelectHandler = {}
) = statefulMultiEnumSelect(name, placeholder = placeholder, min = 1, max = 1, label = label, ref = ref.transform({ it.toEnumSet() }, { it.first() }), handler = handler)

fun entitySelect(
    name: String,
    vararg targets: EntitySelectMenu.SelectTarget,
    channelTypes: Collection<ChannelType> = emptyList(),
    default: Collection<EntitySelectMenu.DefaultValue> = emptyList(),
    placeholder: CharSequence? = DEFAULT_LABEL,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    handler: EntitySelectHandler = {}
) = createMessageElement(name, handler) { config, id ->
    val select = EntitySelectMenu.create(id, targets.toList())
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder"))
        .setMinValues(min)
        .setMaxValues(max)
        .setChannelTypes(channelTypes)
        .setDefaultValues(default)
        .build()

    ActionRow.of(select)
}
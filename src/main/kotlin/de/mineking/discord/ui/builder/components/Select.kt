package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.ComponentHandler
import de.mineking.discord.ui.MessageElement
import de.mineking.discord.ui.State
import de.mineking.discord.ui.element
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import java.util.*

typealias StringSelectHandler = ComponentHandler<*, StringSelectInteractionEvent>
typealias EntitySelectHandler = ComponentHandler<*, EntitySelectInteractionEvent>

typealias JDASelectOption = net.dv8tion.jda.api.interactions.components.selections.SelectOption
typealias StringSelectElement = MessageElement<StringSelectMenu, StringSelectInteractionEvent>

data class SelectOption(
    val label: String,
    val value: String,
    val description: String?,
    val default: Boolean,
    val emoji: Emoji?,
    val localization: LocalizationFile?,
    val visible: Boolean,
    val handler: StringSelectHandler?
) {
    fun build() = JDASelectOption.of(label, value)
        .withEmoji(emoji)
        .withDescription(description)
        .withDefault(default)

    fun show(visible: Boolean) = copy(visible = visible)
    fun hide(hide: Boolean) = show(!hide)
}

fun selectOption(
    label: String,
    value: Any = label,
    description: String? = null,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    handler: StringSelectHandler? = null
) = SelectOption(label, value.toString(), description, default, emoji, localization, true, handler)

fun stringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: String? = null,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    handler: StringSelectHandler = {}
) = element<StringSelectMenu, StringSelectInteractionEvent>(name, localization, { _, id ->
    StringSelectMenu.create(id)
        .setPlaceholder(placeholder)
        .setMinValues(min)
        .setMaxValues(max)
        .addOptions(options.map { it.build() })
        .build()
}, {
    options.filter { it.value in event.values }.forEach { it.handler?.invoke(this) }
    handler()
}) { this?.apply { if (isDisabled && options.isEmpty()) createCopy().addOption("---", "---").build() } }

fun stringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: String? = null,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    handler: StringSelectHandler = {}
) = stringSelect(name, options.toList(), placeholder, min, max, localization, handler)

fun statefulMultiStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: String? = null,
    min: Int = 1,
    max: Int = 1,
    ref: State<List<String>>,
    handler: StringSelectHandler = {}
) = stringSelect(name, options.map { it.copy(default = it.value in ref.get(null)) }, placeholder, min, max) {
    ref.set(null, event.values)
    handler()
}

fun statefulMultiStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: String? = null,
    min: Int = 1,
    max: Int = 1,
    ref: State<List<String>>,
    handler: StringSelectHandler = {}
) = statefulMultiStringSelect(name, options.toList(), placeholder, min, max, ref, handler)

internal fun String?.toList() = if (this == null) emptyList() else listOf(this)
fun statefulOptionalSingleStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: String? = null,
    ref: State<String?>,
    handler: StringSelectHandler = {}
) = statefulMultiStringSelect(name, options, placeholder, min = 0, max = 1, ref = ref.transform({ it.toList() }, { it.firstOrNull() }), handler = handler)

fun statefulOptionalSingleStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: String? = null,
    ref: State<String?>,
    handler: StringSelectHandler = {}
) = statefulOptionalSingleStringSelect(name, options.toList(), placeholder, ref, handler)

fun statefulSingleStringSelect(
    name: String,
    options: List<SelectOption>,
    placeholder: String? = null,
    ref: State<String>,
    handler: StringSelectHandler = {}
) = statefulMultiStringSelect(name, options, placeholder, min = 1, max = 1, ref = ref.transform({ it.toList() }, { it.first() }), handler = handler)

fun statefulSingleStringSelect(
    name: String,
    vararg options: SelectOption,
    placeholder: String? = null,
    ref: State<String>,
    handler: StringSelectHandler = {}
) = statefulSingleStringSelect(name, options.toList(), placeholder, ref, handler)

inline fun <reified E : Enum<E>> enumSelect(
    name: String,
    placeholder: String? = null,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    noinline handler: StringSelectHandler = {}
) = stringSelect(name, E::class.java.enumConstants.map { label(it.name, it) }, placeholder, min, max, localization, handler)

inline fun <reified E : Enum<E>> statefulMultiEnumSelect(
    name: String,
    placeholder: String? = null,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    ref: State<EnumSet<E>>,
    noinline handler: StringSelectHandler = {}
): StringSelectElement {
    return enumSelect<E>(name, placeholder, min, max, localization, { id, e -> label(id, e).copy(default = id in ref.get(null).map { it.name }) }) {
        val temp = E::class.java.enumConstants.filter { it.name in event.values }
        ref.set(null, if (temp.isNotEmpty()) EnumSet.copyOf(temp) else EnumSet.noneOf(E::class.java))

        handler()
    }
}

inline fun <reified E : Enum<E>> E?.toEnumSet(): EnumSet<E> = if (this == null) EnumSet.noneOf(E::class.java) else EnumSet.of(this)
inline fun <reified E : Enum<E>> statefulOptionalSingleEnumSelect(
    name: String,
    placeholder: String? = null,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    ref: State<E?>,
    noinline handler: StringSelectHandler = {}
) = statefulMultiEnumSelect(name, placeholder, min = 0, max = 1, localization, label, ref = ref.transform({ it.toEnumSet() }, { it.firstOrNull() }), handler = handler)

inline fun <reified E : Enum<E>> statefulSingleEnumSelect(
    name: String,
    placeholder: String? = null,
    localization: LocalizationFile? = null,
    label: (id: String, enum: E) -> SelectOption = { id, e -> selectOption(e.toString(), id, localization = localization) },
    ref: State<E>,
    noinline handler: StringSelectHandler = {}
) = statefulMultiEnumSelect(name, placeholder = placeholder, min = 1, max = 1, label = label, ref = ref.transform({ it.toEnumSet() }, { it.first() }), handler = handler)

fun entitySelect(
    name: String,
    vararg targets: SelectTarget,
    channelTypes: Collection<ChannelType> = emptyList(),
    placeholder: String? = null,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    handler: EntitySelectHandler = {}
) = element<EntitySelectMenu, EntitySelectInteractionEvent>(name, localization, { manager, id ->
    EntitySelectMenu.create(id, targets.toList())
        .setPlaceholder(placeholder)
        .setMinValues(min)
        .setMaxValues(max)
        .setChannelTypes(channelTypes)
        .build()
}, handler)
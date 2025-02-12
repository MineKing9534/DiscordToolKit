package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.internal.interactions.component.ButtonImpl

enum class ButtonColor(val style: ButtonStyle) {
    GRAY(ButtonStyle.SECONDARY),
    BLUE(ButtonStyle.PRIMARY),
    RED(ButtonStyle.DANGER),
    GREEN(ButtonStyle.SUCCESS)
}

val DEFAULT_BUTTON_COLOR = ButtonColor.GRAY

typealias ButtonHandler = ComponentHandler<*, ButtonInteractionEvent>
typealias ButtonElement = MessageElement<Button, ButtonInteractionEvent>

fun button(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: String = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    handler: ButtonHandler = {}
) = element<Button, _>(name, localization, { _, id ->
    ButtonImpl(id, label, color.style, null, null, false, emoji)
}, handler)

fun link(
    name: String,
    label: String = DEFAULT_LABEL,
    emoji: Emoji? = null,
    url: String,
    localization: LocalizationFile? = null,
    handler: ButtonHandler = {}
) = element<Button, _>(name, localization, { _, _ ->
    ButtonImpl(null, label, ButtonStyle.LINK, url, null, false, emoji)
}, handler)

fun toggleButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: String = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    ref: State<Boolean>,
    handler: ButtonHandler = {}
): ButtonElement {
    return button(name, color, label, emoji, localization) {
        ref.set(this, !ref.get(this))
        handler()
    }
}

inline fun <reified T : Enum<T>> enumToggleButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: String = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    ref: State<T>,
    noinline handler: ButtonHandler = {}
): ButtonElement {
    return button(name, color, label, emoji, localization) {
        ref.set(this, T::class.java.enumConstants[ref.get(this).ordinal + 1])
        handler()
    }
}
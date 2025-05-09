package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

enum class ButtonColor(val style: ButtonStyle) {
    GRAY(ButtonStyle.SECONDARY),
    BLUE(ButtonStyle.PRIMARY),
    RED(ButtonStyle.DANGER),
    GREEN(ButtonStyle.SUCCESS)
}

val DEFAULT_BUTTON_COLOR = ButtonColor.GRAY

typealias ButtonHandler = ComponentHandler<*, ButtonInteractionEvent>

fun button(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    handler: ButtonHandler = {}
) = createMessageElement<Button, _>(name, handler) { config, id ->
    Button.of(
        color.style,
        id,
        config.readLocalizedString(localization, name, label, "label") ?: if (emoji == null) ZERO_WIDTH_SPACE else null,
        emoji
    )
}

fun link(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    url: String,
    localization: LocalizationFile? = null
) = createMessageComponent { config, _ ->
    Button.of(
        ButtonStyle.LINK,
        url,
        config.readLocalizedString(localization, name, label, "label") ?: if (emoji == null) ZERO_WIDTH_SPACE else null,
        emoji
    )
}

fun toggleButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    ref: State<Boolean>,
    handler: ButtonHandler = {}
) = button(name, color, label, emoji, localization) {
    ref.set(this, !ref.get(this))
    handler()
}

inline fun <reified T : Enum<T>> enumToggleButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    ref: State<T>,
    noinline handler: ButtonHandler = {}
) = button(name, color, label, emoji, localization) {
    ref.set(this, T::class.java.enumConstants[ref.get(this).ordinal + 1])
    handler()
}
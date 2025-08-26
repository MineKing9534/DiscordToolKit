package de.mineking.discord.ui.builder.components.message

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.MutableState
import de.mineking.discord.ui.localizationPrefix
import de.mineking.discord.ui.message.ComponentHandler
import de.mineking.discord.ui.message.createMessageElement
import de.mineking.discord.ui.message.createMessageLayoutComponent
import de.mineking.discord.ui.readLocalizedString
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
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    handler: ButtonHandler? = null
) = createMessageElement(name, handler) { config, id ->
    Button.of(
        color.style,
        id,
        config.readLocalizedString(localization, name, label, "label", prefix = config.localizationPrefix()) ?: if (emoji == null) ZERO_WIDTH_SPACE else null,
        emoji
    )
}

fun link(
    name: String,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    url: String,
    localization: LocalizationFile? = null
) = createMessageLayoutComponent { config, _ ->
    Button.of(
        ButtonStyle.LINK,
        url,
        config.readLocalizedString(localization, name, label, "label", prefix = config.localizationPrefix()) ?: if (emoji == null) ZERO_WIDTH_SPACE else null,
        emoji
    )
}

fun toggleButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    ref: MutableState<Boolean>,
    handler: ButtonHandler? = null
) = button(name, color, label, emoji, localization) {
    ref.value = !ref.value
    handler?.invoke(this)
}

inline fun <reified T : Enum<T>> enumToggleButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    ref: MutableState<T>,
    noinline handler: ButtonHandler? = null
) = button(name, color, label, emoji, localization) {
    ref.value = T::class.java.enumConstants[ref.value.ordinal + 1]
    handler?.invoke(this)
}
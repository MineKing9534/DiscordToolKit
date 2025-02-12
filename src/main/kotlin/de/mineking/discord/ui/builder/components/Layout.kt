package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.Component

fun label(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: String = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null
) = button(name, color, label, emoji, localization).disabled()

fun blank(name: String, color: ButtonColor = DEFAULT_BUTTON_COLOR) = label(name, color = color, label = ZERO_WIDTH_SPACE)

fun endRow() = BREAKPOINT_ELEMENT

internal val BREAKPOINT = object : ActionComponent {
    override fun toData() = error("")
    override fun getType() = Component.Type.UNKNOWN
    override fun getId(): String = ""
    override fun isDisabled() = false
    override fun withDisabled(disabled: Boolean) = this
}

internal val BREAKPOINT_ELEMENT = object : MessageElement<ActionComponent, GenericComponentInteractionCreateEvent>("", null) {
    override fun handle(context: ComponentContext<*, GenericComponentInteractionCreateEvent>) {}
    override fun render(menu: MessageMenu<*, *>, generator: IdGenerator): List<Pair<ActionComponent?, MessageElement<*, *>>> = listOf(BREAKPOINT to this)
}
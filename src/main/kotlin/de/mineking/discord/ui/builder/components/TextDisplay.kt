package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.builder.TextElementBuilder
import de.mineking.discord.ui.builder.renderTextElement
import de.mineking.discord.ui.createSharedLayoutComponent
import de.mineking.discord.ui.localizationPrefix
import de.mineking.discord.ui.readLocalizedString
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.textdisplay.TextDisplay

fun textDisplay(content: String) = createSharedLayoutComponent { _, _ -> TextDisplay.of(content) }
inline fun buildTextDisplay(content: TextElementBuilder) = textDisplay(renderTextElement(content))

fun lazyTextDisplay(content: () -> String) = createSharedLayoutComponent { _, _ -> TextDisplay.of(content()) }
fun buildLazyTextDisplay(content: TextElementBuilder) = lazyTextDisplay { renderTextElement(content) }

fun localizedTextDisplay(name: String, path: CharSequence = DEFAULT_LABEL, localization: LocalizationFile? = null) = createSharedLayoutComponent { config, _ ->
    TextDisplay.of(config.readLocalizedString(localization, name, path, "content", prefix = config.localizationPrefix())?.takeIf { it.isNotEmpty() } ?: ZERO_WIDTH_SPACE)
}

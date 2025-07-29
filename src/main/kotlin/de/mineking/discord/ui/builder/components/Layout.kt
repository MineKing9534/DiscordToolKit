package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import de.mineking.discord.ui.builder.TextElement
import de.mineking.discord.ui.builder.TextElementBuilder
import de.mineking.discord.ui.builder.build
import de.mineking.discord.ui.builder.text
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.container.ContainerChildComponent
import net.dv8tion.jda.api.components.filedisplay.FileDisplay
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent
import net.dv8tion.jda.api.components.section.SectionContentComponent
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.thumbnail.Thumbnail
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.utils.FileUpload
import java.awt.Color

fun label(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null
) = button(name, color, label, emoji, localization).disabled()

fun actionRow(vararg components: MessageComponent<out ActionRowChildComponent>) = actionRow(components.toList())
fun actionRow(components: List<MessageComponent<out ActionRowChildComponent>>) = createLayoutComponent(components) { config, id ->
    listOf(
        ActionRow.of(components.flatMap { it.render(config, id) })
    )
}

private const val MAX_PER_ROW = 5

fun actionRows(vararg component: MessageComponent<out ActionRowChildComponent>) = actionRows(component.toList())
fun actionRows(components: List<MessageComponent<out ActionRowChildComponent>>) = createLayoutComponent(components) { config, id ->
    val temp = mutableListOf<ActionRowChildComponent>()
    var currentSize = 0

    val rows = mutableListOf<ActionRow>()

    for (component in components.flatMap { it.render(config, id) }) {
        val size = MAX_PER_ROW + 1 - ActionRow.getMaxAllowed(component.type)

        if (currentSize + size > MAX_PER_ROW && temp.isNotEmpty()) {
            rows += ActionRow.of(temp)

            temp.clear()
            currentSize = 0
        }

        temp += component
        currentSize += size
    }

    if (temp.isNotEmpty()) rows += ActionRow.of(temp)

    rows
}

fun separator(invisible: Boolean = false, spacing: Separator.Spacing = Separator.Spacing.SMALL) = createMessageComponent { _, _ -> Separator.create(!invisible, spacing) }

fun container(
    vararg components: MessageComponent<out ContainerChildComponent>,
    spoiler: Boolean = false,
    color: Int? = null
) = container(components.toList(), spoiler, color)

fun container(
    components: List<MessageComponent<out ContainerChildComponent>>,
    spoiler: Boolean = false,
    color: Int? = null
) = createLayoutComponent(components) { config, id ->
    listOf(
        Container.of(components.flatMap { it.render(config, id) })
            .withSpoiler(spoiler)
            .withAccentColor(color)
    )
}

fun section(
    accessory: MessageComponent<out SectionAccessoryComponent>,
    vararg components: MessageComponent<out SectionContentComponent>
) = section(accessory, components.toList())

fun section(
    accessory: MessageComponent<out SectionAccessoryComponent>,
    components: List<MessageComponent<out SectionContentComponent>>
) = createLayoutComponent(components + accessory) { config, id ->
    listOf(
        Section.of(
            accessory.render(config, id).single(),
            components.flatMap { it.render(config, id) }
        )
    )
}

fun thumbnail(file: suspend () -> FileUpload) = createMessageComponent { _, _ -> Thumbnail.fromFile(file()) }
fun thumbnail(file: FileUpload) = thumbnail { file }

fun thumbnail(url: String) = createMessageComponent { _, _ -> Thumbnail.fromUrl(url) }

fun textDisplay(content: suspend () -> String) = createMessageComponent { _, _ -> TextDisplay.of(content()) }
fun textDisplay(content: String) = textDisplay { content }
fun buildTextDisplay(content: suspend TextElement.() -> Unit) = textDisplay { build { content() } }

fun localizedTextDisplay(name: String, path: CharSequence = DEFAULT_LABEL, localization: LocalizationFile? = null) = createMessageComponent { config, _ ->
    TextDisplay.of(config.readLocalizedString(localization, name, path, "content")?.takeIf { it.isNotEmpty() } ?: ZERO_WIDTH_SPACE)
}

fun fileDisplay(file: suspend () -> FileUpload) = createMessageComponent { _, _ -> FileDisplay.fromFile(file()) }
fun fileDisplay(file: FileUpload) = fileDisplay { file }

fun mediaGallery(vararg media: MediaGalleryItem) = mediaGallery(media.toList())
fun mediaGallery(media: List<MediaGalleryItem>) = createMessageComponent { _, _ -> MediaGallery.of(media) }
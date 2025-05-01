package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
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
    ActionRow.of(components.flatMap { it.render(config, id) })
}

fun separator(invisible: Boolean = false, spacing: Separator.Spacing = Separator.Spacing.SMALL) = createMessageComponent { _, _ -> Separator.create(!invisible, spacing) }

fun container(
    vararg components: MessageComponent<out ContainerChildComponent>,
    spoiler: Boolean = false,
    color: Color? = null
) = container(components.toList(), spoiler, color)

fun container(
    components: List<MessageComponent<out ContainerChildComponent>>,
    spoiler: Boolean = false,
    color: Color? = null
) = createLayoutComponent(components) { config, id ->
    Container.of(components.flatMap { it.render(config, id) })
        .withSpoiler(spoiler)
        .withAccentColor(color)
}

fun section(
    accessory: MessageComponent<out SectionAccessoryComponent>,
    vararg components: MessageComponent<out SectionContentComponent>
) = section(accessory, components.toList())

fun section(
    accessory: MessageComponent<out SectionAccessoryComponent>,
    components: List<MessageComponent<out SectionContentComponent>>
) = createLayoutComponent(components + accessory) { config, id ->
    Section.of(
        accessory.render(config, id).single(),
        components.flatMap { it.render(config, id) }
    )
}

fun thumbnail(file: () -> FileUpload) = createMessageComponent { _, _ -> Thumbnail.fromFile(file()) }
fun thumbnail(file: FileUpload) = thumbnail { file }

fun thumbnail(url: String) = createMessageComponent { _, _ -> Thumbnail.fromUrl(url) }

fun textDisplay(content: String) = createMessageComponent { _, _ -> TextDisplay.create(content) }

fun localizedTextDisplay(name: String, path: CharSequence = DEFAULT_LABEL, localization: LocalizationFile? = null) = createMessageComponent { config, _ ->
    TextDisplay.create(config.readLocalizedString(localization, name, path, "content") ?: ZERO_WIDTH_SPACE)
}

fun fileDisplay(file: () -> FileUpload) = createMessageComponent { _, _ -> FileDisplay.fromFile(file()) }
fun fileDisplay(file: FileUpload) = fileDisplay { file }

fun mediaGallery(vararg media: MediaGalleryItem) = mediaGallery(media.toList())
fun mediaGallery(media: List<MediaGalleryItem>) = createMessageComponent { _, _ -> MediaGallery.of(media) }
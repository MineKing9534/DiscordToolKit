package de.mineking.discord.ui.builder.components.message

import de.mineking.discord.ui.message.MessageComponent
import de.mineking.discord.ui.message.createMessageLayoutComponent
import net.dv8tion.jda.api.components.MessageTopLevelComponent
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
import net.dv8tion.jda.api.utils.FileUpload

fun actionRow(vararg components: MessageComponent<out ActionRowChildComponent>) = actionRow(components.toList())
fun actionRow(components: List<MessageComponent<out ActionRowChildComponent>>) = createMessageLayoutComponent(components) { config, id ->
    listOf(
        ActionRow.of(components.flatMap { it.render(config, id) })
    )
}

private const val MAX_PER_ROW = 5

fun actionRows(vararg component: MessageComponent<out ActionRowChildComponent>) = actionRows(component.toList())
fun actionRows(components: List<MessageComponent<out ActionRowChildComponent>>) = createMessageLayoutComponent(components) { config, id ->
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

fun separator(invisible: Boolean = false, spacing: Separator.Spacing = Separator.Spacing.SMALL) = createMessageLayoutComponent { _, _ -> Separator.create(!invisible, spacing) }

fun container(
    vararg components: MessageComponent<out ContainerChildComponent>,
    spoiler: Boolean = false,
    color: Int? = null
) = container(components.toList(), spoiler, color)

fun container(
    components: List<MessageComponent<out ContainerChildComponent>>,
    spoiler: Boolean = false,
    color: Int? = null
) = createMessageLayoutComponent(components) { config, id ->
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
) = createMessageLayoutComponent(components + accessory) { config, id ->
    listOf(
        Section.of(
            accessory.render(config, id).single(),
            components.flatMap { it.render(config, id) }
        )
    )
}

fun <T> optionalSection(
    accessory: MessageComponent<out SectionAccessoryComponent>,
    components: List<MessageComponent<out TextDisplay>>
) where T : ContainerChildComponent, T : MessageTopLevelComponent = createMessageLayoutComponent(components + accessory) { config, id ->
    val accessory = accessory.render(config, id)
    val components = components.flatMap { it.render(config, id) }

    val result = if (accessory.isEmpty()) components
    else listOf(Section.of(accessory.single(), components))

    @Suppress("UNCHECKED_CAST")
    result as List<T>
}

fun thumbnail(file: () -> FileUpload) = createMessageLayoutComponent { _, _ -> Thumbnail.fromFile(file()) }
fun thumbnail(file: FileUpload) = thumbnail { file }

fun thumbnail(url: String) = createMessageLayoutComponent { _, _ -> Thumbnail.fromUrl(url) }

fun fileDisplay(file: () -> FileUpload) = createMessageLayoutComponent { _, _ -> FileDisplay.fromFile(file()) }
fun fileDisplay(file: FileUpload) = fileDisplay { file }

fun mediaGallery(vararg media: MediaGalleryItem) = mediaGallery(media.toList())
fun mediaGallery(media: List<MediaGalleryItem>) = createMessageLayoutComponent { _, _ -> MediaGallery.of(media) }
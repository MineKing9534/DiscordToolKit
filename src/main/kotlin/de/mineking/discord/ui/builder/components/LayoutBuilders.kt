package de.mineking.discord.ui.builder.components

import de.mineking.discord.ui.MessageComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent
import net.dv8tion.jda.api.components.container.ContainerChildComponent
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent
import net.dv8tion.jda.api.components.section.SectionContentComponent

open class ComponentBuilder<T> {
    val components = mutableListOf<T>()

    operator fun T.unaryPlus() {
        components += this
    }
}

typealias ActionRowBuilder = ComponentBuilder<MessageComponent<out ActionRowChildComponent>>
inline fun actionRow(multiple: Boolean = false, builder: ActionRowBuilder.() -> Unit): MessageComponent<ActionRow> {
    val components = ActionRowBuilder().apply(builder).components
    return if (multiple) actionRows(components)
    else actionRow(components)
}

typealias ContainerBuilder = ComponentBuilder<MessageComponent<out ContainerChildComponent>>
inline fun container(
    spoiler: Boolean = false,
    color: Int? = null,
    builder: ContainerBuilder.() -> Unit
) = container(ContainerBuilder().apply(builder).components, spoiler, color)

typealias SectionBuilder = ComponentBuilder<MessageComponent<out SectionContentComponent>>
inline fun section(
    accessory: MessageComponent<out SectionAccessoryComponent>,
    builder: SectionBuilder.() -> Unit
) = section(accessory, SectionBuilder().apply(builder).components)

typealias MediaGalleryBuilder = ComponentBuilder<MediaGalleryItem>
fun mediaGallery(builder: MediaGalleryBuilder.() -> Unit) = mediaGallery(MediaGalleryBuilder().apply(builder).components)
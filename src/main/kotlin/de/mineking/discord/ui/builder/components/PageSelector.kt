package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import de.mineking.discord.ui.builder.TextElement
import de.mineking.discord.ui.builder.paginate
import de.mineking.discord.ui.builder.text
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.emoji.Emoji
import java.lang.Integer.max
import java.lang.Integer.min

suspend fun MessageMenuConfig<*, *>.pageSelector(
    name: String,
    max: Int,
    ref: State<Int>,
    modal: Boolean = true,
    title: CharSequence = DEFAULT_LABEL,
    label: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null
): MessageComponent<ActionRow> {
    var page by ref

    return actionRow(
        button("$name-first", label = ZERO_WIDTH_SPACE, emoji = Emoji.fromUnicode("⏪")) { page = 1 }.disabled(page == 1),
        button("$name-back", label = ZERO_WIDTH_SPACE, emoji = Emoji.fromUnicode("⬅\uFE0F")) { page-- }.disabled(page <= 1),
        if (modal)
            modalButton(
                name, emoji = Emoji.fromUnicode("\uD83D\uDCD4"), label = "$page/$max", title = title, localization = localization,
                component = intInput("page", label = label, localization = localization, value = page, placeholder = "$page").unbox().map { it ?: terminateRender() }
            ) { page = clamp(it, 1, max) }
        else label(name, emoji = Emoji.fromUnicode("\uD83D\uDCD4"), label = "$page/$max"),
        button("$name-next", label = ZERO_WIDTH_SPACE, emoji = Emoji.fromUnicode("➡\uFE0F")) { page++ }.disabled(page >= max),
        button("$name-last", label = ZERO_WIDTH_SPACE, emoji = Emoji.fromUnicode("⏩")) { page = max }.disabled(page == max),
    )
}

fun pageFocusSelector(
    name: String,
    max: Int,
    ref: State<Int>
): MessageComponent<ActionRow> {
    var page by ref

    val pages = if (page > 2) min(page - 2, max - 4)..min(page + 2, max) else max(1, page - 2)..max(page + 2, 5)

    return actionRow(pages.map {
        button("$name-$it", label = "$it") { page = it }.disabled(it == page)
    })
}

data class PaginationResult(val text: TextElement, val component: MessageComponent<ActionRow>)

suspend fun <T> MessageMenuConfig<*, *>.pagination(
    name: String,
    entries: List<T>,
    display: T.(index: Int) -> TextElement = { index -> text("$index. ") + text(this) },
    perPage: Int = 20,
    ref: State<Int>,
    pageFocusSelector: Boolean = false,
    modal: Boolean = true,
    title: CharSequence = DEFAULT_LABEL,
    label: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null
): PaginationResult {
    val page by ref

    val max = (entries.size - 1) / perPage + 1
    val component = pageSelector(name, max, ref, modal, title, label, localization)

    return PaginationResult(
        paginate(entries, page, perPage, display),
        if (pageFocusSelector) createMessageComponent(component, pageFocusSelector(name, max, ref)) else component
    )
}


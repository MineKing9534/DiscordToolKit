package de.mineking.discord.ui.builder.components.message

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.MutableState
import de.mineking.discord.ui.builder.TextElement
import de.mineking.discord.ui.builder.components.modal.intInput
import de.mineking.discord.ui.builder.components.modal.localizedLabel
import de.mineking.discord.ui.builder.components.modal.unbox
import de.mineking.discord.ui.builder.paginate
import de.mineking.discord.ui.builder.text
import de.mineking.discord.ui.disabled
import de.mineking.discord.ui.disabledIf
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.message.MessageComponent
import de.mineking.discord.ui.message.MessageMenuConfig
import de.mineking.discord.ui.message.createMessageComponent
import de.mineking.discord.ui.message.parameter
import de.mineking.discord.ui.message.withParameter
import de.mineking.discord.ui.modal.map
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.terminateRender
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.emoji.Emoji
import java.lang.Integer.max
import java.lang.Integer.min

fun MessageMenuConfig<*, *>.pageSelector(
    name: String,
    max: Int,
    ref: MutableState<Int>,
    modal: Boolean = true,
    title: CharSequence = DEFAULT_LABEL,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    localization: LocalizationFile? = null
): MessageComponent<ActionRow> {
    var page by ref

    return actionRow {
        +button("$name-first", label = ZERO_WIDTH_SPACE, emoji = Emoji.fromUnicode("⏪")) {
            page = 1
        }.disabledIf(page == 1)

        +button(
            "$name-back",
            label = ZERO_WIDTH_SPACE,
            emoji = Emoji.fromUnicode("⬅\uFE0F")
        ) { page-- }.disabledIf(page <= 1)

        if (modal)
            +modalButton(
                name,
                emoji = Emoji.fromUnicode("\uD83D\uDCD4"),
                label = "$page/$max",
                title = title,
                localization = localization,
                component = localizedLabel(
                    intInput(
                    "page",
                    localization = localization,
                    value = page,
                    placeholder = "$page"
                ).unbox().map { it ?: terminateRender() }, label = label, description = description, localization = localization
                )
            ) { page = it.coerceIn(1, max) }
        else +button(name, emoji = Emoji.fromUnicode("\uD83D\uDCD4"), label = "$page/$max").disabled()

        +button(
            "$name-next",
            label = ZERO_WIDTH_SPACE,
            emoji = Emoji.fromUnicode("➡\uFE0F")
        ) { page++ }.disabledIf(page >= max)

        +button("$name-last", label = ZERO_WIDTH_SPACE, emoji = Emoji.fromUnicode("⏩")) {
            page = parameter()
        }.disabledIf(page == max).withParameter(max)
    }
}

fun pageFocusSelector(
    name: String,
    max: Int,
    ref: MutableState<Int>
): MessageComponent<ActionRow> {
    var page by ref

    val pages = if (page > 2) min(page - 2, max - 4)..min(page + 2, max) else max(1, page - 2)..max(page + 2, 5)

    return actionRow {
        pages.forEach {
            +button("$name-$it", label = "$it") { page = it }.disabledIf(it == page)
        }
    }
}

data class PaginationResult(val text: TextElement, val component: MessageComponent<ActionRow>)

fun <T> MessageMenuConfig<*, *>.pagination(
    name: String,
    entries: List<T>,
    display: T.(index: Int) -> TextElement = { index -> text("$index. ") + text(this) },
    perPage: Int = 20,
    ref: MutableState<Int>,
    pageFocusSelector: Boolean = false,
    modal: Boolean = true,
    title: CharSequence = DEFAULT_LABEL,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    localization: LocalizationFile? = null
): PaginationResult {
    val page by ref

    val max = (entries.size - 1) / perPage + 1
    val component = pageSelector(name, max, ref, modal, title, label, description, localization)

    return PaginationResult(
        paginate(entries, page, perPage, display),
        if (pageFocusSelector) createMessageComponent(component, pageFocusSelector(name, max, ref)) else component
    )
}


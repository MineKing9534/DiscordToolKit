package de.mineking.discord.ui.builder

import net.dv8tion.jda.api.utils.MarkdownSanitizer
import kotlin.math.max
import kotlin.math.min

typealias ElementListBuilder = ElementList.() -> Unit

data class ElementList(internal val entries: MutableList<TextElement> = mutableListOf()) {
    operator fun TextElement.unaryPlus() {
        entries += this
    }
}

fun ElementListBuilder.entries(): List<String> {
    val list = ElementList()
    this(list)
    return list.entries.map { "$it\n" }
}

fun TextElementBuilder.text(): String {
    val element = TextElement("")
    this(element)
    return element.text
}

typealias TextElementBuilder = TextElement.() -> Unit

data class TextElement(internal var text: String) {
    operator fun String.unaryPlus() {
        text += this
    }

    operator fun TextElement.unaryPlus() {
        this@TextElement.text += this@unaryPlus.text
    }

    operator fun plus(element: TextElement) = TextElement(text + element.text)
    operator fun plus(text: Any) = TextElement(this.text + text)
}

fun TextElement.append(element: TextElement) = +element
fun TextElement.append(text: String) = +text

fun raw(text: Any) = TextElement(text.toString())
fun block(init: TextElementBuilder) = raw(init.text())
fun empty() = raw("")

fun line(text: Any = "") = raw(text.toString() + "\n")
fun line(init: TextElementBuilder) = line(init.text())

fun line(element: TextElement) = element + "\n"
fun textLine(text: Any) = text(text.toString() + "\n")

fun text(text: Any?) = raw(MarkdownSanitizer.escape(text.toString()))
fun text(init: TextElementBuilder) = text(init.text())

fun paragraph(init: TextElementBuilder) = raw(init.text() + "\n")

fun quote(text: String) = line("> $text")
fun quote(text: TextElement) = quote(text.text)
fun quote(init: TextElementBuilder) = quote(init.text())

fun String.styled(apply: Boolean = true, transform: (String) -> String) = raw(if (apply) transform(this) else this)

fun bold(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "**$it**" }
fun bold(apply: Boolean = true, init: TextElementBuilder) = bold(init.text(), apply)
fun bold(text: TextElement, apply: Boolean = true) = bold(text.text, apply)
fun String.asBold(apply: Boolean = true) = bold(this, apply)

fun italic(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "*$it*" }
fun italic(apply: Boolean = true, init: TextElementBuilder) = italic(init.text(), apply)
fun italic(text: TextElement, apply: Boolean = true) = italic(text.text, apply)
fun String.asItalic(apply: Boolean = true) = italic(this, apply)

fun underline(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "__${it}__" }
fun underline(apply: Boolean = true, init: TextElementBuilder) = underline(init.text(), apply)
fun underline(text: TextElement, apply: Boolean = true) = underline(text.text, apply)
fun String.asUnderlined(apply: Boolean = true) = underline(this, apply)

fun strikethrough(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "~~$it~~" }
fun strikethrough(apply: Boolean = true, init: TextElementBuilder) = strikethrough(init.text(), apply)
fun strikethrough(text: TextElement, apply: Boolean = true) = strikethrough(text.text, apply)
fun String.asStrikethrough(apply: Boolean = true) = strikethrough(this, apply)

fun spoiler(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "||$it||" }
fun spoiler(apply: Boolean = true, init: TextElementBuilder) = spoiler(init.text(), apply)
fun spoiler(text: TextElement, apply: Boolean = true) = spoiler(text.text, apply)
fun String.asSpoiler(apply: Boolean = true) = spoiler(this, apply)

fun hyperlink(url: String, text: String) = raw("[$text]($url)")
fun hyperlink(url: String, init: TextElementBuilder) = hyperlink(url, init.text())
fun hyperlink(url: String, element: TextElement) = hyperlink(url, element.text)

fun code(text: String, apply: Boolean = true) = text.styled(apply) { "`$it`" }

fun codeBlock(language: String, text: String) = raw("```$language\n$text```")

fun heading(i: Int, text: String) = line("${"#".repeat(i)} $text")
fun h1(text: String) = heading(1, text)
fun h2(text: String) = heading(2, text)
fun h3(text: String) = heading(3, text)
fun h4(text: String) = line(bold(text))

fun heading(i: Int, init: TextElementBuilder) = heading(i, init.text())
fun h1(init: TextElementBuilder) = heading(1, init)
fun h2(init: TextElementBuilder) = heading(2, init)
fun h3(init: TextElementBuilder) = heading(3, init)

fun sub(text: String) = line("-# $text")
fun sub(init: TextElementBuilder) = sub(init.text())

fun orderedList(init: ElementListBuilder) = block { init.entries().forEachIndexed { index, element -> +raw("${index + 1}. $element") } }
fun orderedList(title: TextElement, init: ElementListBuilder) = paragraph {
    +title
    +orderedList(init)
}

fun unorderedList(init: ElementListBuilder) = block { init.entries().forEach { +"- $it" } }
fun unorderedList(title: TextElement, init: ElementListBuilder) = paragraph {
    +title
    +unorderedList(init)
}

fun quoteBlock(init: ElementListBuilder) = block { init.entries().forEach { +"> $it" } }
fun quoteBlock(title: TextElement, init: ElementListBuilder) = paragraph {
    +title
    +quoteBlock(init)
}

fun <T> paginate(entries: List<T>, page: Int, perPage: Int = 20, builder: T.(index: Int) -> TextElement = { index -> text("$index. ") + text(this) }) = block {
    val start = max(0, (page - 1) * perPage)
    val end = min(page * perPage, entries.size)

    entries.subList(start, end).forEachIndexed { index, element -> +line(element.builder(index + start)) }
}
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

fun bold(text: Any) = raw("**$text**")
fun bold(init: TextElementBuilder) = bold(init.text())
fun bold(text: TextElement) = bold(text.text)

fun italic(text: Any) = raw("*$text*")
fun italic(init: TextElementBuilder) = italic(init.text())
fun italic(text: TextElement) = italic(text.text)

fun underline(text: Any) = raw("__${text}__")
fun underline(init: TextElementBuilder) = underline(init.text())
fun underline(text: TextElement) = underline(text.text)

fun strikethrough(text: Any) = raw("~~$text~~")
fun strikethrough(init: TextElementBuilder) = strikethrough(init.text())
fun strikethrough(text: TextElement) = strikethrough(text.text)

fun spoiler(text: Any) = raw("||$text||")
fun spoiler(init: TextElementBuilder) = spoiler(init.text())
fun spoiler(text: TextElement) = spoiler(text.text)

fun hyperlink(url: String, text: String) = raw("[$text]($url)")
fun hyperlink(url: String, init: TextElementBuilder) = hyperlink(url, init.text())
fun hyperlink(url: String, element: TextElement) = hyperlink(url, element.text)

fun code(text: String) = raw("`$text`")
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
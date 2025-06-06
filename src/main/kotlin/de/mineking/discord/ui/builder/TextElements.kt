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

inline fun build(builder: TextElementBuilder): String {
    val element = TextElement("")
    element.builder()
    return element.toString()
}

typealias TextElementBuilder = TextElement.() -> Unit

data class TextElement(private var text: String) {
    operator fun String.unaryPlus() {
        text += this
    }

    operator fun TextElement.unaryPlus() {
        this@TextElement.text += this@unaryPlus.text
    }

    operator fun plus(element: TextElement) = TextElement(text + element.text)
    operator fun plus(text: Any) = TextElement(this.text + text)

    override fun toString() = text
}

fun TextElement.append(element: TextElement) = +element
fun TextElement.append(text: String) = +text

fun raw(text: Any) = TextElement(text.toString())
inline fun block(init: TextElementBuilder) = raw(build(init))
fun empty() = raw("")

fun line(text: Any = "") = raw(text.toString() + "\n")
inline fun line(init: TextElementBuilder) = line(build(init))

fun line(element: TextElement) = element + "\n"
fun textLine(text: Any) = text(text.toString() + "\n")

fun text(text: Any?) = raw(MarkdownSanitizer.escape(text.toString()))
inline fun text(init: TextElementBuilder) = text(build(init))

inline fun paragraph(init: TextElementBuilder) = raw(build(init) + "\n")

fun quote(text: String) = line("> $text")
fun quote(text: TextElement) = quote(text.toString())
inline fun quote(init: TextElementBuilder) = quote(build(init))

fun String.styled(apply: Boolean = true, transform: (String) -> String) = raw(if (apply) transform(this) else this)

fun bold(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "**$it**" }
inline fun bold(apply: Boolean = true, init: TextElementBuilder) = bold(build(init), apply)
fun bold(text: TextElement, apply: Boolean = true) = bold(text.toString(), apply)
fun String.asBold(apply: Boolean = true) = bold(this, apply)

fun italic(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "*$it*" }
inline fun italic(apply: Boolean = true, init: TextElementBuilder) = italic(build(init), apply)
fun italic(text: TextElement, apply: Boolean = true) = italic(text.toString(), apply)
fun String.asItalic(apply: Boolean = true) = italic(this, apply)

fun underline(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "__${it}__" }
inline fun underline(apply: Boolean = true, init: TextElementBuilder) = underline(build(init), apply)
fun underline(text: TextElement, apply: Boolean = true) = underline(text.toString(), apply)
fun String.asUnderlined(apply: Boolean = true) = underline(this, apply)

fun strikethrough(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "~~$it~~" }
inline fun strikethrough(apply: Boolean = true, init: TextElementBuilder) = strikethrough(build(init), apply)
fun strikethrough(text: TextElement, apply: Boolean = true) = strikethrough(text.toString(), apply)
fun String.asStrikethrough(apply: Boolean = true) = strikethrough(this, apply)

fun spoiler(text: Any, apply: Boolean = true) = text.toString().styled(apply) { "||$it||" }
inline fun spoiler(apply: Boolean = true, init: TextElementBuilder) = spoiler(build(init), apply)
fun spoiler(text: TextElement, apply: Boolean = true) = spoiler(text.toString(), apply)
fun String.asSpoiler(apply: Boolean = true) = spoiler(this, apply)

fun hyperlink(url: String, text: String) = raw("[$text]($url)")
inline fun hyperlink(url: String, init: TextElementBuilder) = hyperlink(url, build(init))
fun hyperlink(url: String, element: TextElement) = hyperlink(url, element.toString())

fun code(text: String, apply: Boolean = true) = text.styled(apply) { "`$it`" }

fun codeBlock(language: String, text: String) = raw("```$language\n$text```")
inline fun codeBlock(language: String, init: TextElementBuilder) = codeBlock(language, build(init))

fun heading(i: Int, text: String) = line("${"#".repeat(i)} $text")
fun h1(text: String) = heading(1, text)
fun h2(text: String) = heading(2, text)
fun h3(text: String) = heading(3, text)
fun h4(text: String) = line(bold(text))

inline fun heading(i: Int, init: TextElementBuilder) = heading(i, build(init))
fun h1(init: TextElementBuilder) = heading(1, init)
fun h2(init: TextElementBuilder) = heading(2, init)
fun h3(init: TextElementBuilder) = heading(3, init)

fun heading(i: Int, element: TextElement) = heading(i, element.toString())
fun h1(element: TextElement) = heading(1, element)
fun h2(element: TextElement) = heading(2, element)
fun h3(element: TextElement) = heading(3, element)

fun sub(text: String) = line("-# $text")
inline fun sub(init: TextElementBuilder) = sub(build(init))

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
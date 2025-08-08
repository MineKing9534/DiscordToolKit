package de.mineking.discord.ui.builder

import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.utils.FileUpload
import java.awt.Color
import java.time.Instant

data class EmbedField(internal var name: String, internal var value: String, internal var inline: Boolean)

data class EmbedImage(val url: String, val file: FileUpload?)
data class EmbedResult(val embed: net.dv8tion.jda.api.EmbedBuilder, val files: List<FileUpload>)

fun EmbedImage(url: String) = EmbedImage(url, null)
fun EmbedImage(file: FileUpload) = EmbedImage("attachment://${file.name}", file)

typealias EmbedConfigurator = EmbedBuilder.() -> Unit

class EmbedBuilder {
    private var title: String? = null
    private var url: String? = null

    private var thumbnail: EmbedImage? = null
    private var color: Color? = null

    private var author: String? = null
    private var authorUrl: String? = null
    private var authorIcon: EmbedImage? = null

    private var description: String? = null
    private var fields: MutableList<EmbedField> = mutableListOf()

    private var image: EmbedImage? = null

    private var footer: String? = null
    private var footerIcon: EmbedImage? = null

    private var timestamp: Instant? = null

    fun title(title: String) {
        this.title = title
    }

    inline fun title(init: TextElementBuilder) = title(renderTextElement(init))
    fun title(text: TextElement) = title(text.toString())

    fun url(url: String) {
        this.url = url
    }

    fun thumbnail(image: EmbedImage?) {
        this.thumbnail = image
    }

    fun thumbnail(url: String) = thumbnail(EmbedImage(url))
    fun thumbnail(file: FileUpload) = thumbnail(EmbedImage(file))

    fun color(color: Color) {
        this.color = color
    }

    fun authorName(name: String) {
        this.author = name
    }

    fun authorUrl(url: String?) {
        this.authorUrl = url
    }

    fun authorIcon(icon: EmbedImage?) {
        this.authorIcon = icon
    }

    fun author(name: String, url: String? = null, icon: EmbedImage? = null) {
        authorName(name)
        authorUrl(url)
        authorIcon(icon)
    }

    fun author(name: String, url: String? = null, icon: String?) = author(name, url, icon?.let { EmbedImage(it) })
    fun author(name: String, url: String? = null, icon: FileUpload?) = author(name, url, icon?.let { EmbedImage(it) })

    fun description(description: String) {
        this.description = description
    }

    inline fun description(init: TextElementBuilder) = description(renderTextElement(init))
    fun description(text: TextElement) = description(text.toString())

    fun field(field: EmbedField) {
        fields.add(field)
    }

    fun blankField(inline: Boolean = true) = field(EmbedField(ZERO_WIDTH_SPACE, ZERO_WIDTH_SPACE, inline))

    fun field(name: String, value: String, inline: Boolean = true) = field(EmbedField(name, value, inline))
    fun field(name: String, value: TextElement, inline: Boolean = true) = field(name, value.toString(), inline)
    inline fun field(name: String, value: TextElementBuilder, inline: Boolean = true) = field(name, renderTextElement(value), inline)

    fun image(image: EmbedImage?) {
        this.image = image
    }

    fun image(url: String) = image(EmbedImage(url))
    fun image(file: FileUpload) = image(EmbedImage(file))

    fun footer(text: String) {
        this.footer = text
    }

    fun footerIcon(icon: EmbedImage?) {
        this.footerIcon = icon
    }

    fun footer(text: String, icon: EmbedImage? = null) {
        footer(text)
        footerIcon(icon)
    }

    fun footer(text: String, icon: String?) = footer(text, icon?.let { EmbedImage(it) })
    fun footer(text: String, icon: FileUpload?) = footer(text, icon?.let { EmbedImage(it) })

    fun timestamp(timestamp: Instant? = null) {
        this.timestamp = timestamp
    }

    fun build(): EmbedResult {
        val builder = net.dv8tion.jda.api.EmbedBuilder()
            .setTitle(title)
            .setThumbnail(thumbnail?.url)
            .setColor(color)
            .setAuthor(author, authorUrl, authorIcon?.url)
            .setDescription(description)
            .setImage(image?.url)
            .setFooter(footer, footerIcon?.url)
            .setTimestamp(timestamp)

        fields.forEach { builder.addField(it.name, it.value, it.inline) }

        val files = mutableListOf<FileUpload>()

        if (thumbnail?.file != null) files += thumbnail?.file!!
        if (authorIcon?.file != null) files += authorIcon?.file!!
        if (image?.file != null) files += image?.file!!
        if (footerIcon?.file != null) files += footerIcon?.file!!

        return EmbedResult(builder, files)
    }
}

inline fun embed(init: EmbedConfigurator): EmbedBuilder = EmbedBuilder().apply(init)
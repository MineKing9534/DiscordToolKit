package de.mineking.discord.ui.builder

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import java.io.InputStream

typealias MessageBuilder = Message.() -> Unit

open class Message : IMessage {
    private var content: String = ""
    private var embeds: MutableList<EmbedResult> = mutableListOf()
    private var attachments: MutableList<FileUpload> = mutableListOf()

    override fun content(content: String) {
        this.content = content
    }

    override fun embed(embed: MessageEmbed) {
        embeds += EmbedResult(net.dv8tion.jda.api.EmbedBuilder(embed), emptyList())
    }

    override fun embed(embed: Embed) {
        embeds += embed.build()
    }

    override fun attachment(file: FileUpload) {
        attachments += file
    }

    override fun build(): MessageEditBuilder {
        return MessageEditBuilder()
            .setContent(content)
            .setAttachments(attachments + embeds.flatMap { it.files })
            .setEmbeds(embeds.map { it.embed.build() })
    }
}

fun message(init: MessageBuilder): Message {
    val message = Message()
    message.init()
    return message
}

interface IMessage {
    fun content(content: String)
    fun content(content: TextElement) = content(content.toString())

    fun embed(embed: MessageEmbed)
    fun embed(embed: Embed)
    fun embed(init: EmbedBuilder) {
        val embed = Embed()
        embed.init()
        embed(embed)
    }

    fun attachment(file: FileUpload)
    fun attachment(name: String, data: ByteArray) = attachment(FileUpload.fromData(data, name))
    fun attachment(name: String, data: () -> InputStream) = attachment(FileUpload.fromStreamSupplier(name, data))

    fun build(): MessageEditBuilder
}

inline fun IMessage.content(init: TextElementBuilder) = content(build(init))
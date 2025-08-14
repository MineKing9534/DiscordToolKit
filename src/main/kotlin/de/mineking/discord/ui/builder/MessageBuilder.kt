package de.mineking.discord.ui.builder

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder

typealias MessageConfigurator = MessageBuilder.() -> Unit

class MessageBuilder {
    private var content: String = ""
    private var embeds: MutableList<EmbedResult> = mutableListOf()
    private var attachments: MutableList<FileUpload> = mutableListOf()

    inline fun content(init: TextElementBuilder) = content(renderTextElement(init))
    fun content(content: TextElement) = content(content.toString())
    fun content(content: String) {
        this.content = content
    }

    fun embed(embed: MessageEmbed) {
        embeds += EmbedResult(net.dv8tion.jda.api.EmbedBuilder(embed), emptyList())
    }

    fun embed(embed: EmbedBuilder) {
        embeds += embed.build()
    }

    fun attachment(file: FileUpload) {
        attachments += file
    }

    fun build(): MessageEditBuilder {
        return MessageEditBuilder()
            .setContent(content)
            .setAttachments(attachments + embeds.flatMap { it.files })
            .setEmbeds(embeds.map { it.embed.build() })
    }
}

inline fun buildMessage(init: MessageConfigurator) = MessageBuilder().apply(init)
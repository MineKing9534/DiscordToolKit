package de.mineking.discord.ui.builder.components.modal

import de.mineking.discord.ui.modal.ModalResultHandler
import de.mineking.discord.ui.modal.createModalElement
import de.mineking.discord.ui.modal.map
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload
import net.dv8tion.jda.api.entities.Message
import kotlin.math.absoluteValue

fun fileUpload(
    name: String,
    required: Boolean = true,
    min: Int? = null,
    max: Int? = null,
    handler: ModalResultHandler<List<Message.Attachment>>? = null
) = createModalElement(name, {
    val temp = event.getValueByUniqueId(name.hashCode().absoluteValue)!!.asAttachmentList
    temp.also { handler?.invoke(this, it) }
}) { _, id ->
    AttachmentUpload.create(id)
        .setUniqueId(name.hashCode().absoluteValue)
        .setRequired(required)
        .apply {
            if (min != null) setMinValues(min)
            if (max != null) setMaxValues(max)
        }
        .build()
}

fun singleFileUpload(
    name: String,
    required: Boolean = true,
    handler: ModalResultHandler<Message.Attachment>? = null
) = fileUpload(name, required, 1, 1, handler?.map { it.first() })
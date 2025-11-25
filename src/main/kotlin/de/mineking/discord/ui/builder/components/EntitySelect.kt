package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.createSharedElement
import de.mineking.discord.ui.localizationPrefix
import de.mineking.discord.ui.message.ComponentHandler
import de.mineking.discord.ui.modal.ModalResultHandler
import de.mineking.discord.ui.modal.map
import de.mineking.discord.ui.readLocalizedString
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Mentions
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import kotlin.math.absoluteValue

typealias EntitySelectHandler = ComponentHandler<*, EntitySelectInteractionEvent>

fun entitySelect(
    name: String,
    vararg targets: EntitySelectMenu.SelectTarget,
    channelTypes: Collection<ChannelType> = emptyList(),
    default: Collection<EntitySelectMenu.DefaultValue> = emptyList(),
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<Mentions>? = null,
    handler: EntitySelectHandler? = null
) = createSharedElement(name, handler, {
    val temp = event.getValueByUniqueId(name.hashCode().absoluteValue)!!.asMentions
    temp.also { modalHandler?.invoke(this, it) }
}) { config, id ->
    EntitySelectMenu.create(id, targets.toList())
        .setPlaceholder(config.readLocalizedString(localization, name, placeholder, "placeholder", prefix = config.localizationPrefix()))
        .setMinValues(min)
        .setMaxValues(max)
        .setChannelTypes(channelTypes)
        .setDefaultValues(default)
        .setRequired(required)
        .build()
}

fun channelSelect(
    name: String,
    channelTypes: Collection<ChannelType> = emptyList(),
    default: Collection<GuildChannel> = emptyList(),
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<GuildChannel>>? = null,
    handler: EntitySelectHandler? = null
) = entitySelect(
    name, targets = arrayOf(EntitySelectMenu.SelectTarget.CHANNEL), channelTypes, default.map { EntitySelectMenu.DefaultValue.from(it) },
    placeholder, required, min, max, localization,
    modalHandler?.map { it.channels }, handler
)

fun userSelect(
    name: String,
    default: Collection<UserSnowflake> = emptyList(),
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<Member>>? = null,
    handler: EntitySelectHandler? = null
) = entitySelect(
    name, targets = arrayOf(EntitySelectMenu.SelectTarget.USER), emptyList(), default.map { EntitySelectMenu.DefaultValue.from(it) },
    placeholder, required, min, max, localization,
    modalHandler?.map { it.members }, handler
)

fun roleSelect(
    name: String,
    default: Collection<Role> = emptyList(),
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<Role>>? = null,
    handler: EntitySelectHandler? = null
) = entitySelect(
    name, targets = arrayOf(EntitySelectMenu.SelectTarget.ROLE), emptyList(), default.map { EntitySelectMenu.DefaultValue.from(it) },
    placeholder, required, min, max, localization,
    modalHandler?.map { it.roles }, handler
)

fun mentionableSelect(
    name: String,
    default: Collection<IMentionable> = emptyList(),
    placeholder: CharSequence? = DEFAULT_LABEL,
    required: Boolean = true,
    min: Int = 1,
    max: Int = 1,
    localization: LocalizationFile? = null,
    modalHandler: ModalResultHandler<List<IMentionable>>? = null,
    handler: EntitySelectHandler? = null
) = entitySelect(
    name, targets = arrayOf(EntitySelectMenu.SelectTarget.ROLE), emptyList(),
    default.map {
        when (it) {
            is UserSnowflake -> EntitySelectMenu.DefaultValue.from(it)
            is Role -> EntitySelectMenu.DefaultValue.from(it)
            else -> error("Unsupported mentionable type: $it")
        }
    },
    placeholder, required, min, max, localization,
    modalHandler?.map { it.getMentions() }, handler
)
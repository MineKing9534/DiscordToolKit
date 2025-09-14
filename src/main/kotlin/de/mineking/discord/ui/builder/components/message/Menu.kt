package de.mineking.discord.ui.builder.components.message

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.*
import de.mineking.discord.ui.builder.components.SelectOption
import de.mineking.discord.ui.builder.components.selectOption
import de.mineking.discord.ui.message.MessageElement
import de.mineking.discord.ui.message.MessageMenu
import de.mineking.discord.ui.message.MessageMenuConfig
import de.mineking.discord.ui.modal.ModalComponent
import de.mineking.discord.ui.modal.ModalContext
import de.mineking.discord.ui.modal.getValue
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.ModalTopLevelComponent
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.tree.ComponentTree
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.modals.ModalInteraction
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import net.dv8tion.jda.api.utils.messages.MessagePollData

class BackReference(val menu: MessageMenu<*, *>, val stateCount: Int) {
    fun asButton(
        name: String = menu.name.menuName(),
        color: ButtonColor = DEFAULT_BUTTON_COLOR,
        label: CharSequence? = DEFAULT_LABEL,
        emoji: Emoji? = null,
        localization: LocalizationFile? = null
    ) = switchMenuButton(menu, name, color, label, emoji, localization) {
        copy(stateCount)
        pushDefaults()
    }

    fun asSelectOption(
        label: CharSequence = DEFAULT_LABEL,
        description: CharSequence? = DEFAULT_LABEL,
        default: Boolean = false,
        emoji: Emoji? = null,
        localization: LocalizationFile? = null
    ) = switchMenuSelectOption(menu, label = label, description = description, default = default, emoji = emoji, localization = localization) {
        copy(stateCount)
        pushDefaults()
    }
}

fun switchMenuButton(
    menu: Menu<*, *, *>,
    name: String = menu.name.menuName(),
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = button(name, color = color, label = label, emoji = emoji, localization) {
    switchMenu(menu, state)
}

fun switchMenuButton(
    menu: String,
    name: String = menu.menuName(),
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = button(name, color = color, label = label, emoji = emoji, localization) {
    switchMenu(this.menu.manager.getMenu<Any?>(menu), state)
}

fun MessageMenuConfig<*, *>.menuButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    menuLocalization: LocalizationFile? = localization,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    config: suspend MessageMenuConfig<*, *>.(back: BackReference) -> Unit
) = localizedMenuButton(name, color, label, emoji, localization, menuLocalization, defer, useComponentsV2, detach) { _, back -> config(back) }

inline fun <reified L : LocalizationFile> MessageMenuConfig<*, *>.localizedMenuButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    noinline config: suspend MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): MessageElement<Button, ButtonInteractionEvent> {
    val file = menu.manager.manager.localizationManager.read<L>()
    return localizedMenuButton(name, color, label, emoji, localization, file, defer, useComponentsV2, detach, config)
}

fun <L : LocalizationFile?> MessageMenuConfig<*, *>.localizedMenuButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    @Suppress("UNCHECKED_CAST")
    menuLocalization: L = localization as L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    config: suspend MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): MessageElement<Button, ButtonInteractionEvent> {
    val currentState = if (detach) 0 else configState.currentState

    val menu = localizedSubmenu(name, defer, useComponentsV2, menuLocalization, detach) {
        config(it, BackReference(this@localizedMenuButton.menu, currentState))
    }

    return switchMenuButton(menu, color = color, label = label, emoji = emoji, localization = localization) {
        copy(currentState)
        pushDefaults()
    }
}

@MenuMarker
class ModalButtonContext<M, N>(val target: MessageMenu<N, *>, val context: ModalContext<M>) : ModalInteraction by context.event {
    private var update: StateBuilderConfig? = DEFAULT_STATE_BUILDER

    fun preventUpdate() = update(null)

    fun update(state: StateBuilderConfig?) {
        this.update = state
    }

    fun getStateBuilder() = update

    override fun getValue(customId: String) = context.event.getValue(customId)
    override fun getValueByUniqueId(id: Int) = context.event.getValueByUniqueId(id)
    override fun getGuildChannel() = context.event.guildChannel

    override fun deferReply(ephemeral: Boolean) = context.event.deferReply(ephemeral)
    override fun reply(message: MessageCreateData) = context.event.reply(message)
    override fun replyPoll(poll: MessagePollData) = context.event.replyPoll(poll)
    override fun reply(content: String) = context.event.reply(content)
    override fun replyEmbeds(embeds: Collection<MessageEmbed?>) = context.event.replyEmbeds(embeds)
    override fun replyEmbeds(embed: MessageEmbed, vararg embeds: MessageEmbed?) = context.event.replyEmbeds(embed, *embeds)
    override fun replyComponents(components: Collection<MessageTopLevelComponent?>) = context.event.replyComponents(components)
    override fun replyComponents(component: MessageTopLevelComponent, vararg other: MessageTopLevelComponent?) = context.event.replyComponents(component, *other)
    override fun replyComponents(tree: ComponentTree<out MessageTopLevelComponent?>) = context.event.replyComponents(tree)
    override fun replyFormat(format: String, vararg args: Any?) = context.event.replyFormat(format, *args)
    override fun replyFiles(files: Collection<FileUpload?>) = context.event.replyFiles(files)
    override fun replyFiles(vararg files: FileUpload?) = context.event.replyFiles(*files)

    override fun getType() = context.event.type
    override fun isFromAttachedGuild() = context.event.isFromAttachedGuild
    override fun isFromGuild() = context.event.isFromGuild
    override fun getChannelType() = context.event.channelType
    override fun getChannelId() = context.event.channelId
    override fun getMessageChannel() = context.event.messageChannel
    override fun getGuildLocale() = context.event.guildLocale
    override fun getId() = context.event.id
    override fun getCustomId() = context.event.customId
    override fun getTimeCreated() = context.event.timeCreated

    override fun editMessage(message: MessageEditData) = context.event.editMessage(message)
    override fun editMessage(content: String) = context.event.editMessage(content)
    override fun editComponents(components: Collection<MessageTopLevelComponent?>) = context.event.editComponents(components)
    override fun editComponents(vararg components: MessageTopLevelComponent?) = context.event.editComponents(*components)
    override fun editComponents(tree: ComponentTree<out MessageTopLevelComponent?>) = context.event.editComponents(tree)
    override fun editMessageEmbeds(embeds: Collection<MessageEmbed?>) = context.event.editMessageEmbeds(embeds)
    override fun editMessageEmbeds(vararg embeds: MessageEmbed?) = context.event.editMessageEmbeds(*embeds)
    override fun editMessageFormat(format: String, vararg args: Any?) = context.event.editMessageFormat(format, *args)
    override fun editMessageAttachments(attachments: Collection<AttachedFile?>) = context.event.editMessageAttachments(attachments)
    override fun editMessageAttachments(vararg attachments: AttachedFile?) = context.event.editMessageAttachments(*attachments)
}

inline fun <reified T> MessageMenuConfig<*, *>.modalButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    modalLocalization: LocalizationFile? = localization,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<out ModalTopLevelComponent, T>,
    noinline handler: suspend ModalButtonContext<*, *>.(value: T) -> Unit = { }
) = localizedModalButton(name, color, label, emoji, title, localization, modalLocalization, defer, detach, component) { _, value -> handler(value) }

inline fun <reified T, reified L : LocalizationFile> MessageMenuConfig<*, *>.localizedModalButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<out ModalTopLevelComponent, T>,
    noinline handler: suspend ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): MessageElement<Button, ButtonInteractionEvent> {
    val file = menu.manager.manager.localizationManager.read<L>()
    return localizedModalButton(name, color, label, emoji, title, localization, file, defer, detach, component, handler)
}

inline fun <reified T, L : LocalizationFile?> MessageMenuConfig<*, *>.localizedModalButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence? = DEFAULT_LABEL,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    @Suppress("UNCHECKED_CAST")
    modalLocalization: L = localization as L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<out ModalTopLevelComponent, T>,
    noinline handler: suspend ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): MessageElement<Button, ButtonInteractionEvent> {
    val currentState = if (detach) 0 else configState.currentState
    val modal = createModal(name, title, modalLocalization, defer, detach, component, handler)

    return switchMenuButton(modal, color = color, label = label, emoji = emoji, localization = localization) {
        copy(currentState)
        pushDefaults()
    }
}

fun switchMenuSelectOption(
    menu: Menu<*, *, *>,
    name: String = menu.name.menuName(),
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = selectOption(name, label, description, default, emoji, localization) {
    switchMenu(menu, state)
}

fun switchMenuSelectOption(
    menu: String,
    name: String = menu.menuName(),
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = selectOption(name, label, description, default, emoji, localization) {
    switchMenu(this.menu.manager.getMenu<Any?>(menu), state)
}

fun MessageMenuConfig<*, *>.menuSelectOption(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    menuLocalization: LocalizationFile? = localization,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    config: suspend MessageMenuConfig<*, *>.(back: BackReference) -> Unit
) = localizedMenuSelectOption(name, label, description, default, emoji, localization, menuLocalization, defer, useComponentsV2, detach) { _, back -> config(back) }

inline fun <reified L : LocalizationFile> MessageMenuConfig<*, *>.localizedMenuSelectOption(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    detach: Boolean = false,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    noinline config: suspend MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): SelectOption {
    val file = menu.manager.manager.localizationManager.read<L>()
    return localizedMenuSelectOption(name, label, description, default, emoji, localization, file, defer, useComponentsV2, detach, config)
}

fun <L : LocalizationFile?> MessageMenuConfig<*, *>.localizedMenuSelectOption(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    @Suppress("UNCHECKED_CAST")
    menuLocalization: L = localization as L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    config: suspend MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): SelectOption {
    val currentState = if (detach) 0 else configState.currentState

    val menu = localizedSubmenu(name, defer, useComponentsV2, menuLocalization, detach) {
        config(it, BackReference(this@localizedMenuSelectOption.menu, currentState))
    }

    return switchMenuSelectOption(menu, label = label, description = description, default = default, emoji = emoji, localization = localization) {
        copy(currentState)
        pushDefaults()
    }
}

inline fun <reified T> MessageMenuConfig<*, *>.modalSelectOption(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    modalLocalization: LocalizationFile? = localization,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<out ModalTopLevelComponent, T>,
    noinline handler: suspend ModalButtonContext<*, *>.(value: T) -> Unit = { }
) = localizedModalSelectOption(name, label, description, default, emoji, title, localization, modalLocalization, defer, detach, component) { _, value -> handler(value) }

inline fun <reified T, reified L : LocalizationFile> MessageMenuConfig<*, *>.localizedModalSelectOption(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<out ModalTopLevelComponent, T>,
    noinline handler: suspend ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): SelectOption {
    val file = menu.manager.manager.localizationManager.read<L>()
    return localizedModalSelectOption(name, label, description, default, emoji, title, localization, file, defer, detach, component, handler)
}

inline fun <reified T, L : LocalizationFile?> MessageMenuConfig<*, *>.localizedModalSelectOption(
    name: String,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    @Suppress("UNCHECKED_CAST")
    modalLocalization: L = localization as L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<out ModalTopLevelComponent, T>,
    noinline handler: suspend ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): SelectOption {
    val currentState = if (detach) 0 else configState.currentState
    val modal = createModal(name, title, modalLocalization, defer, detach, component, handler)

    return switchMenuSelectOption(modal, label = label, description = description, default = default, emoji = emoji, localization = localization) {
        copy(currentState)
        pushDefaults()
    }
}

fun <T, M, L : LocalizationFile?> MessageMenuConfig<M, *>.createModal(
    name: String,
    title: CharSequence,
    localization: L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean,
    component: ModalComponent<out ModalTopLevelComponent, T>,
    handler: suspend ModalButtonContext<*, *>.(localization: L, value: T) -> Unit
) = localizedModal(name, localization = localization, defer = defer, detach = detach) {
    title(title)

    val componentValue by +component

    execute {
        val target = menu.parent as MessageMenu<*, *>

        val context = ModalButtonContext(target, this)
        context.handler(it, componentValue)

        if (context.getStateBuilder() != null) switchMenu(target, context.getStateBuilder()!!)
    }
}
package de.mineking.discord.ui.builder.components

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.read
import de.mineking.discord.ui.*
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.modals.ModalInteraction

class BackReference(val menu: MenuInfo<*>, val stateCount: Int) {
    fun asButton(
        name: String = menu.name.menuName(),
        color: ButtonColor = DEFAULT_BUTTON_COLOR,
        label: CharSequence = DEFAULT_LABEL,
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
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = button(name, color = color, label = label, emoji = emoji, localization) {
    switchMenu(menu, state)
}

fun switchMenuButton(
    menu: MenuInfo<*>,
    name: String = menu.name.menuName(),
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = button(name, color = color, label = label, emoji = emoji, localization) {
    switchMenu(menu.menu, state)
}

fun switchMenuButton(
    menu: String,
    name: String = menu.menuName(),
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = button(name, color = color, label = label, emoji = emoji, localization) {
    switchMenu(this.menuInfo.manager.getMenu<Any?>(menu), state)
}

fun MessageMenuConfig<*, *>.menuButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    menuLocalization: LocalizationFile? = localization,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    config: MessageMenuConfig<*, *>.(back: BackReference) -> Unit
) = localizedMenuButton(name, color, label, emoji, localization, menuLocalization, defer, useComponentsV2, detach) { _, back -> config(back) }

inline fun <reified L : LocalizationFile> MessageMenuConfig<*, *>.localizedMenuButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    noinline config: MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): MessageElement<Button, ButtonInteractionEvent> {
    val file = menuInfo.manager.manager.localizationManager.read<L>()
    return localizedMenuButton(name, color, label, emoji, localization, file, defer, useComponentsV2, detach, config)
}

fun <L : LocalizationFile?> MessageMenuConfig<*, *>.localizedMenuButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    @Suppress("UNCHECKED_CAST")
    menuLocalization: L = localization as L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    useComponentsV2: Boolean = DEFAULT_COMPONENTS_V2,
    detach: Boolean = false,
    config: MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): MessageElement<Button, ButtonInteractionEvent> {
    val currentState = if (detach) 0 else currentState()

    val menu = localizedSubmenu(name, defer, useComponentsV2, menuLocalization, detach) {
        config(it, BackReference(this@localizedMenuButton.menuInfo, currentState))
    }

    return switchMenuButton(menu, color = color, label = label, emoji = emoji, localization = localization) {
        copy(currentState)
        pushDefaults()
    }
}

@MenuMarker
class ModalButtonContext<M, N>(val target: MessageMenu<N, *>, val context: ModalContext<M>) : StateContext<M> by context, ModalInteraction by context.event {
    private var update: StateBuilderConfig? = DEFAULT_STATE_BUILDER

    fun preventUpdate() = update(null)

    fun update(state: StateBuilderConfig?) {
        this.update = state
    }

    fun getStateBuilder() = update
}

inline fun <reified T> MessageMenuConfig<*, *>.modalButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    modalLocalization: LocalizationFile? = localization,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<T>,
    noinline handler: ModalButtonContext<*, *>.(value: T) -> Unit = { }
) = localizedModalButton(name, color, label, emoji, title, localization, modalLocalization, defer, detach, component) { _, value -> handler(value) }

inline fun <reified T, reified L : LocalizationFile> MessageMenuConfig<*, *>.localizedModalButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<T>,
    noinline handler: ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): MessageElement<Button, ButtonInteractionEvent> {
    val file = menuInfo.manager.manager.localizationManager.read<L>()
    return localizedModalButton(name, color, label, emoji, title, localization, file, defer, detach, component, handler)
}

inline fun <reified T, L : LocalizationFile?> MessageMenuConfig<*, *>.localizedModalButton(
    name: String,
    color: ButtonColor = DEFAULT_BUTTON_COLOR,
    label: CharSequence = DEFAULT_LABEL,
    emoji: Emoji? = null,
    title: CharSequence = DEFAULT_LABEL,
    localization: LocalizationFile? = null,
    @Suppress("UNCHECKED_CAST")
    modalLocalization: L = localization as L,
    defer: DeferMode = DEFAULT_DEFER_MODE,
    detach: Boolean = false,
    component: ModalComponent<T>,
    noinline handler: ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): MessageElement<Button, ButtonInteractionEvent> {
    val currentState = if (detach) 0 else currentState()
    val modal = createModal(name, title, modalLocalization, defer, detach, component, handler)

    return switchMenuButton(modal.name, color = color, label = label, emoji = emoji, localization = localization) {
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
    menu: MenuInfo<*>,
    name: String = menu.name.menuName(),
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    default: Boolean = false,
    emoji: Emoji? = null,
    localization: LocalizationFile? = null,
    state: StateBuilderConfig = DEFAULT_STATE_BUILDER
) = selectOption(name, label, description, default, emoji, localization) {
    switchMenu(menu.menu, state)
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
    switchMenu(menuInfo.manager.getMenu<Any?>(menu), state)
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
    config: MessageMenuConfig<*, *>.(back: BackReference) -> Unit
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
    noinline config: MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): SelectOption {
    val file = menuInfo.manager.manager.localizationManager.read<L>()
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
    config: MessageMenuConfig<*, L>.(localization: L, back: BackReference) -> Unit
): SelectOption {
    val currentState = if (detach) 0 else currentState()

    val menu = localizedSubmenu(name, defer, useComponentsV2, menuLocalization, detach) {
        config(it, BackReference(this@localizedMenuSelectOption.menuInfo, currentState))
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
    component: ModalComponent<T>,
    noinline handler: ModalButtonContext<*, *>.(value: T) -> Unit = { }
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
    component: ModalComponent<T>,
    noinline handler: ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): SelectOption {
    val file = menuInfo.manager.manager.localizationManager.read<L>()
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
    component: ModalComponent<T>,
    noinline handler: ModalButtonContext<*, *>.(localization: L, value: T) -> Unit = { _, _ -> }
): SelectOption {
    val currentState = if (detach) 0 else currentState()
    val modal = createModal(name, title, modalLocalization, defer, detach, component, handler)

    return switchMenuSelectOption(modal, label = label, description = description, default = default, emoji = emoji, localization = localization) {
        copy(currentState)
        pushDefaults()
    }
}

fun <T, M, L : LocalizationFile?> MessageMenuConfig<M, *>.createModal(name: String, title: CharSequence, localization: L, defer: DeferMode = DEFAULT_DEFER_MODE, detach: Boolean, component: ModalComponent<T>, handler: ModalButtonContext<*, *>.(localization: L, value: T) -> Unit) = localizedModal(name, localization = localization, defer = defer, detach = detach) {
    title(title)

    val componentValue = +component

    execute {
        val target = menuInfo.parent as MessageMenu<*, *>

        val context = ModalButtonContext(target, this)
        context.handler(it, componentValue())

        if (context.getStateBuilder() != null) switchMenu(target, context.getStateBuilder()!!)
    }
}
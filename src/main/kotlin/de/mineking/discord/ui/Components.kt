package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.attribute.IDisableable

interface IComponent<C : Component> {
    fun render(config: MenuConfig<*, *>, generator: IdGenerator): List<C>
    fun transform(mapper: (IdGenerator, (IdGenerator) -> List<C>) -> List<C>): IComponent<C>
}

@Suppress("UNCHECKED_CAST")
fun <C : Component, W : IComponent<C>> W.visibleIf(visible: Boolean) = transform { id, render -> if (visible) render(id) else emptyList() } as W
fun <C : Component, W : IComponent<C>> W.hiddenIf(hide: Boolean) = visibleIf(!hide)

@Suppress("NOTHING_TO_INLINE")
inline fun <C : Component, W : IComponent<C>> W.visible() = visibleIf(true)
@Suppress("NOTHING_TO_INLINE")
inline fun <C : Component, W : IComponent<C>> W.hidden() = visibleIf(false)

@Suppress("UNCHECKED_CAST")
fun <C : IDisableable, W : IComponent<C>> W.enabledIf(enabled: Boolean = true) = transform { id, render -> render(id).map { it.withDisabled(!enabled) as C } } as W
fun <C : IDisableable, W : IComponent<C>> W.disabledIf(disabled: Boolean = true) = enabledIf(!disabled)

@Suppress("NOTHING_TO_INLINE")
inline fun <C : IDisableable, W : IComponent<C>> W.enabled() = enabledIf(true)
@Suppress("NOTHING_TO_INLINE")
inline fun <C : IDisableable, W : IComponent<C>> W.disabled() = enabledIf(false)

internal fun MenuConfig<*, *>.readLocalizedString(
    localization: LocalizationFile?,
    element: String?,
    base: CharSequence?,
    name: String,
    prefix: String? = null,
    postfix: String? = null
) = menu.manager.localization.readLocalizedString(this, localization, element, base, name, prefix, postfix)
package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.attribute.IDisableable

interface IComponent<C : Component> {
    fun render(config: MenuConfig<*, *>, generator: IdGenerator): List<C>
    fun transform(mapper: (IdGenerator, (IdGenerator) -> List<C>) -> List<C>): IComponent<C>
}

@Suppress("UNCHECKED_CAST")
fun <C : Component, W : IComponent<C>> W.show(show: Boolean = true) = transform { id, render -> if (show) render(id) else emptyList() } as W
fun <C : Component, W : IComponent<C>> W.hide(hide: Boolean = true) = show(!hide)

@Suppress("UNCHECKED_CAST")
fun <C : IDisableable, W : IComponent<C>> W.enabled(enabled: Boolean = true) = transform { id, render -> render(id).map { it.withDisabled(!enabled) as C } } as W
fun <C : IDisableable, W : IComponent<C>> W.disabled(disabled: Boolean = true) = enabled(!disabled)

internal fun MenuConfig<*, *>.readLocalizedString(
    localization: LocalizationFile?,
    element: String?,
    base: CharSequence?,
    name: String,
    prefix: String? = null,
    postfix: String? = null
) = menuInfo.manager.localization.readLocalizedString(this, localization, element, base, name, prefix, postfix)
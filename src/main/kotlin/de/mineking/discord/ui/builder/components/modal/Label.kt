package de.mineking.discord.ui.builder.components.modal

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.modal.ModalComponent
import de.mineking.discord.ui.modal.ModalElement
import de.mineking.discord.ui.modal.createModalLayoutComponent
import de.mineking.discord.ui.readLocalizedString
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.label.LabelChildComponent

fun <T> label(
    child: ModalComponent<out LabelChildComponent, out T>,
    label: String,
    description: String? = null
) = createModalLayoutComponent({ child.handle(this) }) { config, id ->
    listOf(
        Label.of(label, description, child.render(config, id).single())
    )
}

fun <T> localizedLabel(
    name: String,
    child: ModalComponent<out LabelChildComponent, T>,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    localization: LocalizationFile? = null
) = createModalLayoutComponent({ child.handle(this) }) { config, id ->
    listOf(
        Label.of(
            config.readLocalizedString(localization, name, label, "label", prefix = "components")?.takeIf { it.isNotBlank() } ?: name,
            config.readLocalizedString(localization, name, description, "description", prefix = "components")?.takeIf { it.isNotEmpty() },
            child.render(config, id).single()
        )
    )
}

fun <T> localizedLabel(
    child: ModalElement<out LabelChildComponent, T>,
    label: CharSequence = DEFAULT_LABEL,
    description: CharSequence? = DEFAULT_LABEL,
    localization: LocalizationFile? = null
) = localizedLabel(child.name, child, label, description, localization)
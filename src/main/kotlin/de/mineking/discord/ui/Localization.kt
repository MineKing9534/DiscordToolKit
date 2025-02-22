package de.mineking.discord.ui

import de.mineking.discord.localization.*
import de.mineking.discord.ui.builder.components.SelectOption
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.modals.Modal
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.typeOf

const val DEFAULT_LABEL = "#~-~#"

interface MenuLocalizationHandler {
    fun <T> localize(menu: MenuConfigImpl<*, *>, element: Element?, component: T): T
}

interface IDefaultMenuLocalizationHandler : MenuLocalizationHandler {
    @Suppress("UNCHECKED_CAST")
    override fun <T> localize(menu: MenuConfigImpl<*, *>, element: Element?, component: T) = when (component) {
        is Button -> localizeButton(menu, element, component)
        is SelectMenu -> localizeSelect(menu, element, component)
        is SelectOption -> localizeSelectOption(menu, element, component)
        is TextInput -> localizeTextInput(menu, element, component)
        is Modal -> localizeModal(menu, element, component)
        else -> component
    } as T

    fun readString(menu: MenuConfigImpl<*, *>, element: Element?, name: String, base: String?, override: LocalizationFile? = null, prefix: String = "", postfix: String = ""): String?

    fun localizeButton(menu: MenuConfigImpl<*, *>, element: Element?, button: Button) = button
        .withLabel(readString(menu, element, "label", button.label)?.takeIf { it.isNotBlank() } ?: ZERO_WIDTH_SPACE)

    fun localizeSelect(menu: MenuConfigImpl<*, *>, element: Element?, select: SelectMenu): SelectMenu = select.createCopy()
        .setPlaceholder(readString(menu, element, "placeholder", select.placeholder)?.takeIf { it.isNotBlank() })
        .apply { if (this is StringSelectMenu.Builder) {
            val localizedOptions = options.map { if (it is SelectOption) localize(menu, element, it) else it }
            options.clear()
            options.addAll(localizedOptions)
        } }
        .build()

    fun localizeSelectOption(menu: MenuConfigImpl<*, *>, element: Element?, option: SelectOption) = option
        .withLabel(readString(menu, element, "label", option.label, override = option.localization, postfix = "options.${option.value}")?.takeIf { it.isNotBlank() } ?: ZERO_WIDTH_SPACE)
        .withDescription(readString(menu, element, "description", option.description, override = option.localization, postfix = "options.${option.value}")?.takeIf { it.isNotBlank() })

    fun localizeTextInput(menu: MenuConfigImpl<*, *>, element: Element?, input: TextInput) = TextInput.create(input.id, "-", input.style)
        .setLabel(readString(menu, element, "label", input.label, prefix = "inputs")?.takeIf { it.isNotBlank() } ?: ZERO_WIDTH_SPACE)
        .setPlaceholder(readString(menu, element, "placeholder", input.placeHolder, prefix = "inputs")?.takeIf { it.isNotBlank() })
        .setValue(input.value)
        .setRequired(input.isRequired)
        .setMinLength(input.minLength)
        .setMaxLength(input.maxLength)
        .build()

    fun localizeModal(menu: MenuConfigImpl<*, *>, element: Element?, modal: Modal) = modal.createCopy()
        .setTitle(readString(menu, element, "title", modal.title)?.takeIf { it.isNotBlank() } ?: ZERO_WIDTH_SPACE)
        .build()
}

class SimpleMenuLocalizationHandler : IDefaultMenuLocalizationHandler {
    override fun readString(menu: MenuConfigImpl<*, *>, element: Element?, name: String, base: String?, override: LocalizationFile?, prefix: String, postfix: String): String? =
        if (base?.shouldLocalize() == true) error("Cannot handle actual localization. Use localize() in your UIManager config to enable localization")
        else if (base != DEFAULT_LABEL) base
        else " "
}

class DefaultMenuLocalizationHandler(val prefix: String) : IDefaultMenuLocalizationHandler {
    override fun readString(menu: MenuConfigImpl<*, *>, element: Element?, name: String, base: String?, override: LocalizationFile?, prefix: String, postfix: String): String? {
        val file = override ?: element?.localization ?: menu.menuInfo.menu.localization
        val localize = file != null && (base?.shouldLocalize() == true || base == DEFAULT_LABEL)

        return if (localize) {
            @Suppress("UNCHECKED_CAST")
            val config = menu.localizationConfig ?: error("You have to configure the localization context for a localized menu (e.g. call localize(DiscordLocale) in the menu builder)")

            val locale = config.locale.takeIf { it in menu.menuInfo.manager.manager.localizationManager.locales } ?: menu.menuInfo.manager.manager.localizationManager.defaultLocale

            val globalPrefix = this.prefix.takeIf { it.isNotBlank() }?.let { "$it." } ?: ""
            val prefix = prefix.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
            val element = element?.name?.let { ".$it" } ?: ""
            val postfix = postfix.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""

            val key = if (base == DEFAULT_LABEL) "$globalPrefix${menu.menuInfo.name}$prefix$element$postfix.$name" else base.substring(LOCALIZATION_PREFIX.length)

            file.register(key, config.args.mapValues { it.value.second }, typeOf<String>())
            file.readString(key, locale, config.args.mapValues { it.value.first })
        } else if (base != DEFAULT_LABEL) base else " "
    }
}

fun <T> MenuConfig<*, *>.read(function: KFunction<T>): T {
    require(localizationConfig != null) { "Cannot read localization before localize() call" }

    return function.call(*function.parameters.map {
        if (it.hasAnnotation<Locale>()) localizationConfig!!.locale
        else if (it.hasAnnotation<LocalizationParameter>()) {
            val name = it.findAnnotation<LocalizationParameter>()?.name?.takeIf { it.isNotBlank() } ?: it.name!!
            localizationConfig?.args[name]?.first
        } else null
    }.toTypedArray())
}

data class LocalizationConfig(val locale: DiscordLocale, val args: MutableMap<String, Pair<Any?, KType>> = mutableMapOf()) {
    inline fun <reified T> argument(name: String, value: T) = argument(name, typeOf<T>(), value)
    fun argument(name: String, type: KType, value: Any?) {
        args[name] = value to type
    }
}

val MenuConfig<out GuildChannel, *>.channelLocale get() = channelLocale()()
fun MenuConfig<out GuildChannel, *>.channelLocale() = parameter(
    { menuInfo.manager.manager.localizationManager.defaultLocale },
    { it.guild.locale },
    { event.guildLocale }
)

fun MenuConfig<out GuildChannel, *>.localizeForChannel(config: LocalizationConfig.() -> Unit = {}) = localize(channelLocale, config)

val MenuConfig<out Interaction, *>.guildLocale get() = guildLocale()()
fun MenuConfig<out Interaction, *>.guildLocale() = parameter(
    { menuInfo.manager.manager.localizationManager.defaultLocale },
    { it.guildLocale },
    { event.guildLocale }
)

fun MenuConfig<out Interaction, *>.localizeForGuild(config: LocalizationConfig.() -> Unit = {}) = localize(guildLocale, config)

val MenuConfig<out Interaction, *>.userLocale get() = userLocale()()
fun MenuConfig<out Interaction, *>.userLocale() = parameter(
    { menuInfo.manager.manager.localizationManager.defaultLocale },
    { it.userLocale },
    { event.userLocale }
)

fun MenuConfig<out Interaction, *>.localizeForUser(config: LocalizationConfig.() -> Unit = {}) = localize(userLocale, config)
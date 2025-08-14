package de.mineking.discord.ui

import de.mineking.discord.localization.*
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.typeOf

interface MenuLocalizationHandler {
    fun readLocalizedString(
        menu: MenuConfig<*, *>,
        localization: LocalizationFile?,
        element: String?,
        base: CharSequence?,
        name: String,
        prefix: String? = null,
        postfix: String? = null
    ): String?
}

class UnlocalizedLocalizationHandler : MenuLocalizationHandler {
    override fun readLocalizedString(
        menu: MenuConfig<*, *>,
        localization: LocalizationFile?,
        element: String?,
        base: CharSequence?,
        name: String,
        prefix: String?,
        postfix: String?
    ): String? = when {
        base.shouldLocalize() -> error("Cannot handle actual localization. Use localize() in your UIManager config to enable localization")
        !base.isDefault() -> base.toString()
        else -> null
    }
}

class DefaultLocalizationHandler(val prefix: String) : MenuLocalizationHandler {
    override fun readLocalizedString(
        menu: MenuConfig<*, *>,
        localization: LocalizationFile?,
        element: String?,
        base: CharSequence?,
        name: String,
        prefix: String?,
        postfix: String?
    ): String? {
        val file = localization ?: menu.menu.localization
        val localize = file != null && (base.shouldLocalize() || base.isDefault())

        return when {
            localize -> {
                @Suppress("UNCHECKED_CAST")
                val config = menu.context.localizationContext ?: error("You have to configure the localization context for a localized menu (e.g. call localize(DiscordLocale) in the menu builder)")
                val locale = config.locale.takeIf { it in menu.menu.manager.manager.localizationManager.locales } ?: menu.menu.manager.manager.localizationManager.defaultLocale

                val key =
                    if (base.isDefault()) listOfNotNull(this.prefix, menu.menu.name, prefix, element, postfix, name).joinToString(".")
                    else base.toString()

                file.readString(key, locale, config.args.mapValues { it.value.first })
            }
            base != null && !base.isDefault() -> base.toString()
            else -> null
        }
    }
}

fun <T> MenuConfig<*, *>.read(function: KFunction<T>): T {
    val localizationConfig = context.localizationContext
    require(localizationConfig != null) { "Cannot read localization before localize() call" }

    return function.call(*function.parameters.map {
        if (it.hasAnnotation<Locale>()) localizationConfig.locale
        else if (it.hasAnnotation<LocalizationParameter>()) {
            val name = it.findAnnotation<LocalizationParameter>()?.name?.takeIf { it.isNotBlank() } ?: it.name!!
            localizationConfig.args[name]?.first
        } else null
    }.toTypedArray())
}

data class LocalizationContext(val locale: DiscordLocale, val args: MutableMap<String, Pair<Any?, KType>> = mutableMapOf()) {
    inline fun <reified T> bindParameter(name: String, value: T) = bindParameter(name, typeOf<T>(), value)
    fun bindParameter(name: String, type: KType, value: Any?) {
        args[name] = value to type
    }
}

val MenuConfig<out GuildChannel, *>.channelLocale get() = channelLocale()
fun MenuConfig<out GuildChannel, *>.channelLocale() = parameter(
    { menu.manager.manager.localizationManager.defaultLocale },
    { it.guild.locale },
    { event.guildLocale }
)

inline fun MenuConfig<out GuildChannel, *>.localizeForChannel(config: LocalizationContext.() -> Unit = {}) = localize(channelLocale, config)

val MenuConfig<out Interaction, *>.guildLocale get() = guildLocale()
fun MenuConfig<out Interaction, *>.guildLocale() = parameter(
    { menu.manager.manager.localizationManager.defaultLocale },
    { it.guildLocale },
    { event.guildLocale }
)

inline fun MenuConfig<out Interaction, *>.localizeForGuild(config: LocalizationContext.() -> Unit = {}) = localize(guildLocale, config)

val MenuConfig<out Interaction, *>.userLocale get() = userLocale()
fun MenuConfig<out Interaction, *>.userLocale() = parameter(
    { menu.manager.manager.localizationManager.defaultLocale },
    { it.userLocale },
    { event.userLocale }
)

inline fun MenuConfig<out Interaction, *>.localizeForUser(config: LocalizationContext.() -> Unit = {}) = localize(userLocale, config)
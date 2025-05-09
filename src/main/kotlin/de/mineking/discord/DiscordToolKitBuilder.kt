package de.mineking.discord

import de.mineking.discord.commands.CommandManager
import de.mineking.discord.localization.AdvancedLocalizationManager
import de.mineking.discord.localization.LocalizationManager
import de.mineking.discord.localization.SimpleLocalizationManager
import de.mineking.discord.ui.UIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale

typealias ManagerConfigurator<M> = M.() -> Unit

open class Manager(val manager: DiscordToolKit<*>) : ListenerAdapter()

class DiscordToolKit<B> internal constructor(
    val jda: JDA,
    val bot: B,
    val managers: Set<Manager>,
    val coroutineScope: CoroutineScope
) {
    lateinit var localizationManager: LocalizationManager internal set

    inline fun <reified T : Manager> get(): T = managers.filterIsInstance<T>().first()

    inline fun runSuspending(crossinline block: suspend () -> Unit) {
        coroutineScope.launch {
            block()
        }
    }
}

fun <B> discordToolKit(jda: JDA, bot: B) = DiscordToolKitBuilder(jda, bot)
fun discordToolKit(jda: JDA) = discordToolKit(jda, Unit)

class DiscordToolKitBuilder<B>(val jda: JDA, val bot: B) {
    val managers = mutableListOf<(manager: DiscordToolKit<B>) -> Manager>()
    var localizationManager: (manager: DiscordToolKit<B>) -> LocalizationManager = { SimpleLocalizationManager(it) }

    var coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default) //TODO

    inline fun <reified T : Manager> get(): T = managers.filterIsInstance<T>().first()

    private fun <T : Manager> addManager(instance: (manager: DiscordToolKit<B>) -> T, config: ManagerConfigurator<T>): DiscordToolKitBuilder<B> {
        managers += {
            val manager = instance(it)
            manager.config()

            jda.addEventListener(manager)

            manager
        }

        return this
    }

    fun withAdvancedLocalization(
        locales: List<DiscordLocale>,
        defaultLocale: DiscordLocale = locales.first(),
        botPackage: String = "",
        base: (locale: DiscordLocale) -> String = { "text/${it.locale.lowercase().replace("-", "_")}" },
        config: ManagerConfigurator<AdvancedLocalizationManager> = {}
    ): DiscordToolKitBuilder<B> {
        localizationManager = {
            val temp = AdvancedLocalizationManager(it, locales, defaultLocale, botPackage, base)
            temp.config()

            temp
        }

        return this
    }

    fun withCommandManager(config: ManagerConfigurator<CommandManager> = {}) = addManager({ CommandManager(it) }, config)
    fun withUIManager(config: ManagerConfigurator<UIManager> = {}) = addManager({ UIManager(it) }, config)

    fun build(): DiscordToolKit<B> {
        val managers = hashSetOf<Manager>()
        val manager = DiscordToolKit(jda, bot, managers, coroutineScope)

        manager.localizationManager = localizationManager(manager)
        this.managers.forEach { managers += it(manager) }

        return manager
    }
}
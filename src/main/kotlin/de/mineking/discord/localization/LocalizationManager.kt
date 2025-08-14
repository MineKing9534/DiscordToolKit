package de.mineking.discord.localization

import de.mineking.discord.DiscordToolKit
import net.dv8tion.jda.api.interactions.DiscordLocale
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LocalizationPath(val path: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Locale

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LocalizationParameter(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Localize(val name: String = "")

interface LocalizationManager {
    val manager: DiscordToolKit<*>

    val locales: List<DiscordLocale>
    val defaultLocale: DiscordLocale

    fun <T : LocalizationFile> read(type: KClass<T>): T
}

inline fun <reified T : LocalizationFile> LocalizationManager.read(): T = read(T::class)

interface LocalizationFile {
    val manager: LocalizationManager

    fun readString(name: String, locale: DiscordLocale, args: Map<String, Any?> = emptyMap()): String
}
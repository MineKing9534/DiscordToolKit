package de.mineking.discord.localization

import de.mineking.discord.DiscordToolKit
import net.dv8tion.jda.api.interactions.DiscordLocale
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.typeOf

class SimpleLocalizationManager(override val manager: DiscordToolKit<*>) : LocalizationManager {
    override val locales: List<DiscordLocale> = listOf(DiscordLocale.ENGLISH_US)
    override val defaultLocale: DiscordLocale = locales.first()

    @Suppress("UNCHECKED_CAST")
    override fun <T : LocalizationFile> read(type: KClass<T>): T = Proxy.newProxyInstance(SimpleLocalizationManager::class.java.classLoader, arrayOf(type.java), object : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            val arguments = args ?: emptyArray()
            val function = method.kotlinFunction

            return if (method.name == "getManager" && method.parameters.isEmpty()) manager
            else if (method.name == "toString" && method.parameters.isEmpty()) "LocalizationFile[type=$type]"
            else if (method.name == "hashCode" && method.parameters.isEmpty()) hashCode()
            else if (function.equals("readString", typeOf<String>(), typeOf<DiscordLocale>(), typeOf<Map<String, Any?>>())) {
                val name = arguments[0] as String
                name
            } else error("Cannot handle $method")
        }
    }) as T
}

internal fun KFunction<*>?.equals(name: String, vararg args: KType): Boolean = this != null && this.name == name && this.valueParameters.map { it.type } == args.toList()
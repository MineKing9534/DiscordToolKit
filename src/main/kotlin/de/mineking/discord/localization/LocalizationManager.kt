package de.mineking.discord.localization

import de.mineking.discord.DiscordToolKit
import mu.KotlinLogging
import net.dv8tion.jda.api.interactions.DiscordLocale
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.typeOf

val logger = KotlinLogging.logger {}


@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Locale

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LocalizationParameter(val name: String = "")

interface LocalizationManager {
    val manager: DiscordToolKit<*>

    val locales: List<DiscordLocale>
    val defaultLocale: DiscordLocale

    val annotationHandlers: MutableList<LocalizationAnnotationHandler>

    fun <T : LocalizationFile> read(type: KClass<T>): T
}

inline fun <reified T : LocalizationFile> LocalizationManager.read(): T = read(T::class)

interface LocalizationFile {
    val manager: LocalizationManager

    fun register(name: String, args: Map<String, KType>, type: KType, function: KFunction<*>? = null, default: MessageProvider<*>? = null)

    fun readString(name: String, locale: DiscordLocale, args: Map<String, Any?>): String = readObject(name, locale, args)
    fun <T> readObject(name: String, locale: DiscordLocale, args: Map<String, Any?>): T
}

inline fun <reified T> LocalizationFile.register(
    name: String,
    args: Map<String, KType>,
    function: KFunction<*>? = null,
    noinline default: MessageProvider<T>? = null
) = register(name, args, typeOf<T>(), function, default)

typealias MessageProvider<T> = (args: Map<String, Any?>) -> T

@Suppress("UNCHECKED_CAST")
fun <T : LocalizationFile> createLocalizationFile(
    manager: LocalizationManager, type: KClass<T>,
    registration: (file: T, name: String, locale: DiscordLocale, result: KType, args: Map<String, KType>, function: KFunction<*>?, default: MessageProvider<*>?) -> MessageProvider<*>
): T {
    lateinit var file: T
    file = Proxy.newProxyInstance(SimpleLocalizationManager::class.java.classLoader, arrayOf(type.java), object : InvocationHandler {
        val messages = mutableMapOf<Pair<String, DiscordLocale>, Pair<MessageProvider<*>, List<String>>>()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            val arguments = args ?: emptyArray()
            val function = method.kotlinFunction

            fun invoke(name: String, locale: DiscordLocale, params: (name: String) -> Any?): Any? {
                val message = messages[name to locale] ?: throw LocalizationNotFoundException("Cannot find localization for $name in $locale ($type)")
                return message.first(message.second.associateWith { params(it) })
            }

            return if (method.name == "getManager" && method.parameters.isEmpty()) manager
            else if (method.name == "toString" && method.parameters.isEmpty()) "LocalizationFile[type=$type]"
            else if (method.name == "hashCode" && method.parameters.isEmpty()) hashCode()
            else if (function.equals("register", typeOf<String>(), typeOf<Map<String, KType>>(), typeOf<KType>(), typeOf<KFunction<*>?>(), typeOf<MessageProvider<*>?>())) {
                val name = arguments[0] as String

                if (messages.keys.none { it.first == name }) {
                    val params = arguments[1] as Map<String, KType>
                    val result = arguments[2] as KType
                    val functionRef = arguments[3] as KFunction<*>?
                    val default = arguments[4] as MessageProvider<*>?

                    manager.locales.forEach { locale ->
                        messages += (name to locale) to (registration(file, name, locale, result, params, functionRef, default) to params.keys.toList())
                    }
                } else Unit
            } else if (function.equals("readObject", typeOf<String>(), typeOf<DiscordLocale>(), typeOf<Map<String, Any?>>())) {
                val name = arguments[0] as String
                val locale = arguments[1] as DiscordLocale
                val params = arguments[2] as Map<String, Any?>

                invoke(name, locale) { params[it] }
            } else if (function != null) {
                val handler = manager.annotationHandlers.find { it.accepts(file, function) }
                if (handler == null) type.java.classes.first { it.simpleName == "DefaultImpls" }.getMethod(method.name, type.java, *method.parameterTypes).invoke(null, proxy, *arguments)
                else {
                    val locale = function.valueParameters.find { it.hasAnnotation<Locale>() }?.let { arguments[it.index - 1] as DiscordLocale } ?: manager.defaultLocale
                    val params = function.valueParameters
                        .filter { it.hasAnnotation<LocalizationParameter>() }
                        .associate { (it.findAnnotation<LocalizationParameter>()?.name?.takeIf { it.isNotBlank() } ?: it.name!!) to arguments[it.index - 1] }

                    handler.parse(file, function) { invoke(it, locale) { name -> params[name] } }
                }
            } else error("Cannot handle $method")
        }
    }) as T

    type.memberFunctions.forEach { function ->
        val handler = manager.annotationHandlers.find { it.accepts(file, function) }
        if (handler == null) return@forEach

        val params = function.valueParameters
            .filter { it.hasAnnotation<LocalizationParameter>() }
            .associate { (it.findAnnotation<LocalizationParameter>()?.name?.takeIf { it.isNotBlank() } ?: it.name!!) to it.type }

        handler.lines(file, function).forEach { (name, meta) -> file.register(name, params, meta.first, function, meta.second) }
    }

    return file
}

class LocalizationNotFoundException(message: String) : RuntimeException(message)

internal fun KFunction<*>?.equals(name: String, vararg args: KType): Boolean = this != null && this.name == name && this.valueParameters.map { it.type } == args.toList()
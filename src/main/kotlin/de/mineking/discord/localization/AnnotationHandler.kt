package de.mineking.discord.localization

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import org.simpleyaml.configuration.ConfigurationSection
import java.awt.Color
import java.time.Instant
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.typeOf

interface LocalizationAnnotationHandler {
    fun accepts(file: LocalizationFile, function: KFunction<*>): Boolean

    fun lines(file: LocalizationFile, function: KFunction<*>): Map<String, Pair<KType, MessageProvider<*>?>>
    fun parse(file: LocalizationFile, function: KFunction<*>, read: (name: String) -> Any?): Any?
}

fun KFunction<*>.defaultLocalizationName() = name.replace("(?<=[^[A-Z]])[A-Z]".toRegex(), ".$0").lowercase()

inline fun <reified A : Annotation> localizationAnnotationHandler(
    crossinline lines: (annotation: A, name: String, function: KFunction<*>) -> Map<String, Pair<KType, MessageProvider<*>?>>,
    crossinline handler: (annotation: A, name: String, function: KFunction<*>, read: (name: String) -> Any?) -> Any?
) = object : LocalizationAnnotationHandler {
    override fun accepts(file: LocalizationFile, function: KFunction<*>) = function.hasAnnotation<A>()

    override fun lines(file: LocalizationFile, function: KFunction<*>) = lines(function.findAnnotation<A>()!!, function.defaultLocalizationName(), function)
    override fun parse(file: LocalizationFile, function: KFunction<*>, read: (name: String) -> Any?) = handler(function.findAnnotation<A>()!!, function.defaultLocalizationName(), function, read)
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Localize(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Embed(val name: String = "")

private data class EmbedComponent<T>(val name: String, val type: KType, val builder: EmbedBuilder.(value: T?) -> Unit)

private inline fun <reified T> embedComponent(name: String, noinline builder: EmbedBuilder.(value: T?) -> Unit) = EmbedComponent<T>(name, typeOf<T?>(), builder)
private val embedComponents = listOf(
    embedComponent<String>("title") { setTitle(it) },
    embedComponent<String>("url") { setUrl(it) },
    embedComponent<Color>("color") { setColor(it) },
    embedComponent<ConfigurationSection>("author") { if (it != null) setAuthor(it.getString("name"), it.getString("url"), it.getString("icon")) },
    embedComponent<String>("thumbnail") { setThumbnail(it) },
    embedComponent<String>("description") { setDescription(it) },
    embedComponent<List<Map<String, Any>>>("fields") { fields -> fields?.forEach {
        addField(it["name"] as String, it["value"] as String, it.getOrDefault("inline", false) as Boolean)
    } },
    embedComponent<String>("image") { setImage(it) },
    embedComponent<ConfigurationSection>("footer") { if (it != null) setFooter(it.getString("text"), it.getString("icon")) },
    embedComponent<Instant>("timestamp") { setTimestamp(it) },
)

object AdvancedLocalizationAnnotationHandlers {
    val LINE = localizationAnnotationHandler<Localize>({ annotation, name, function -> mapOf((annotation.name.takeIf { it.isNotBlank() } ?: name) to (function.returnType to null)) }) { annotation, name, _, read ->
        read(annotation.name.takeIf { it.isNotBlank() } ?: name)
    }

    val EMBED = localizationAnnotationHandler<Embed>({ annotation, name, _ ->
        val key = annotation.name.takeIf { it.isNotBlank() } ?: name
        embedComponents.associate { (name, type, _) -> "$key.$name" to (type to { null }) }
    }) { annotation, name, function, read ->
        val key = annotation.name.takeIf { it.isNotBlank() } ?: name
        val embed = EmbedBuilder()

        @Suppress("UNCHECKED_CAST")
        embedComponents.map { it as EmbedComponent<Any?> }.forEach { (name, _, builder) ->
            embed.builder(read("$key.$name"))
        }

        when (function.returnType) {
            typeOf<MessageEmbed>() -> embed.build()
            typeOf<EmbedBuilder>() -> embed
            else -> error("")
        }
    }
}

object SimpleLocalizationAnnotationHandlers {
    val LINE = localizationAnnotationHandler<Localize>({ annotation, name, function -> mapOf((annotation.name.takeIf { it.isNotBlank() } ?: name) to (function.returnType to null)) }) { annotation, name, _, read ->
        read(annotation.name.takeIf { it.isNotBlank() } ?: name)
    }

    val EMBED = localizationAnnotationHandler<Embed>({ annotation, name, function -> mapOf((annotation.name.takeIf { it.isNotBlank() } ?: name) to (function.returnType to null)) }) { annotation, name, _, read ->
        read(annotation.name.takeIf { it.isNotBlank() } ?: name)
    }
}
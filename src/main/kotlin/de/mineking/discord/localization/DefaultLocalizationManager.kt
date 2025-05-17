package de.mineking.discord.localization

import de.mineking.discord.DiscordToolKit
import de.mineking.discord.Manager
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.DiscordLocale
import org.simpleyaml.configuration.ConfigurationSection
import org.simpleyaml.configuration.file.YamlConfiguration
import org.simpleyaml.utils.SupplierIO.InputStream
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.jvmTarget
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

private fun textElement(text: String) = Element("'$text'") { text }
private data class Element(val text: String, val content: MessageProvider<*>) {
    operator fun invoke(args: Map<String, Any?>) = content(args)
    override fun toString() = text
}

private fun createDefaultValue(name: String, type: KType): Any? = when {
    type == typeOf<String>() -> name
    type == typeOf<MessageEmbed>() -> EmbedBuilder().setDescription(name).build()
    type == typeOf<EmbedBuilder>() -> EmbedBuilder().setDescription(name)
    type.isMarkedNullable -> null
    else -> error("Cannot produce result type $type")
}

class SimpleLocalizationManager(override val manager: DiscordToolKit<*>) : LocalizationManager {
    override val locales: List<DiscordLocale> = listOf(DiscordLocale.ENGLISH_US)
    override val defaultLocale: DiscordLocale = locales.first()

    override val annotationHandlers = SimpleLocalizationAnnotationHandlers::class.memberProperties.map { it.get(SimpleLocalizationAnnotationHandlers) as LocalizationAnnotationHandler }.toMutableList()

    override fun <T : LocalizationFile> read(type: KClass<T>): T = createLocalizationFile(this, type) { _, name, _, result, _, _, default ->
        if (default != null) return@createLocalizationFile { default(it) }

        val value = createDefaultValue(name, result)
        return@createLocalizationFile { value }
    }
}

data class DefaultValue<T>(val name: String, val type: KType, val value: (manager: LocalizationManager, args: Map<String, Any?>) -> T)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LocalizationPath(val path: String)

class AdvancedLocalizationManager(
    manager: DiscordToolKit<*>,
    override val locales: List<DiscordLocale>,
    override val defaultLocale: DiscordLocale,
    val botPackage: String,
    val base: (locale: DiscordLocale) -> String
) : LocalizationManager, Manager(manager) {
    private val engine = BasicJvmScriptingHost()
    private val defaultValues = mutableListOf<DefaultValue<*>>()
    private val imports = mutableMapOf<String, String>()

    override val annotationHandlers = AdvancedLocalizationAnnotationHandlers::class.memberProperties.map { it.get(AdvancedLocalizationAnnotationHandlers) as LocalizationAnnotationHandler }.toMutableList()

    private val cache = mutableMapOf<KClass<*>, Any>()

    inline fun <reified T> defaultValue(name: String, noinline value: (manager: LocalizationManager, args: Map<String, Any?>) -> T) = defaultValue(name, typeOf<T>(), value)
    fun defaultValue(name: String, type: KType, value: (manager: LocalizationManager, args: Map<String, Any?>) -> Any?) {
        defaultValues += DefaultValue(name, type, value)
    }

    fun import(simpleName: String, fullyQualifiedName: String) {
        imports += simpleName to fullyQualifiedName
    }

    inline fun <reified T> import() = import(T::class)
    fun import(type: KClass<*>) = import(type.simpleName ?: error("Cannot import anonymous type $type"), type.qualifiedName ?: error("Cannot import unqualifiable type $type"))

    fun read(type: KClass<*>, locale: DiscordLocale): YamlConfiguration {
        val name =
            type.findAnnotation<LocalizationPath>()?.path ?:
            (type.qualifiedName!!.removePrefix("$botPackage.").removeSuffix(type.simpleName!!) + type.simpleName!!.replace("(?<=[^A-Z])[A-Z]".toRegex(), "_$0").lowercase())

        val path = "${base(locale)}/${name.replace(".", "/")}.yaml"

        return YamlConfiguration.loadConfiguration(object : InputStream {
            override fun get(): java.io.InputStream? {
                return this::class.java.classLoader.getResourceAsStream(path) ?: error("Cannot find localization file for $type at $path")
            }
        })
    }

    override fun <T : LocalizationFile> read(type: KClass<T>): T {
        @Suppress("UNCHECKED_CAST")
        if (type in cache) return cache[type] as T

        val (file, time) = measureTimedValue {
            createLocalizationFile(this, type) { file, name, locale, result, types, function, default ->
                val location = function?.javaMethod?.declaringClass?.kotlin ?: type
                val section = read(location, locale)

                val value = section.get(name)

                if (result.jvmErasure == List::class && value is List<*>) {
                    val list = section.getList(name)
                    list.forEachIndexed { index, value -> file.register("$name[$index]", types, if (value is String) typeOf<String>() else typeOf<ConfigurationSection>(), function, default) }

                    return@createLocalizationFile { value }
                }

                if (result.jvmErasure == ConfigurationSection::class && (value is ConfigurationSection || value is Map<*, *>)) {
                    @Suppress("UNCHECKED_CAST")
                    val keys = if (value is ConfigurationSection) value.getKeys(false) else (value as Map<String, *>).keys
                    keys.forEach { file.register("$name.$it", types, typeOf<String>(), function, default) }

                    return@createLocalizationFile { value }
                }

                val line = section.getString(name) ?: if (default != null) return@createLocalizationFile { default(it) }
                else {
                    logger.error("Cannot find localization for ${location.simpleName}#$name for $locale")
                    ""
                }

                val elements = parseLine(
                    line, locale,
                    file to type.createType(type.typeParameters.map { KTypeProjection.invariant(it.starProjectedType) }),
                    location.simpleName!!,
                    name,
                    types,
                    result
                )

                return@createLocalizationFile { args ->
                    if (elements.size == 1) elements.first()(args)
                    else elements.joinToString("") { it(args).toString() }
                }
            }
        }

        cache[type] = file

        logger.info("Compiled localization for ${type.simpleName} in ${time.toString(DurationUnit.SECONDS, 2)}")
        return file
    }

    private fun parseLine(
        line: String,
        locale: DiscordLocale,
        file: Pair<LocalizationFile, KType>,
        location: String,
        key: String,
        args: Map<String, KType>,
        type: KType
    ): List<Element> {
        val elements = arrayListOf<Element>()

        var last = 0.toChar()
        var temp = ""
        var code = 0

        line.forEach {
            when (it) {
                '{' -> {
                    if (code > 0) code++

                    if (last == '$' && code <= 0) {
                        temp = temp.dropLast(1)

                        val value = temp
                        if (value.isNotEmpty()) elements += textElement(value)

                        temp = ""

                        code = 1
                    } else temp += it
                }

                '}' -> {
                    code--

                    if (code == 0) {
                        elements += createScriptElement(temp, locale, file, location, key, args, type)
                        temp = ""
                    } else temp += it
                }

                else -> temp += it
            }

            last = it
        }

        if (temp.isNotEmpty()) {
            if (code <= 0) elements += textElement(temp)
            else error("Invalid line")
        }

        return elements
    }

    private fun createScriptElement(
        script: String,
        locale: DiscordLocale,
        file: Pair<LocalizationFile, KType>,
        location: String,
        key: String,
        args: Map<String, KType>,
        type: KType
    ): Element = runBlocking {
        var element = Element(script) {}

        val scriptWithImports = imports.entries.joinToString(";", postfix = ";") { (simpleName, fullName) -> "typealias $simpleName = $fullName" } + script

        engine.compiler(scriptWithImports.toScriptSource(), ScriptCompilationConfiguration {
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
                jvmTarget("21")
            }

            providedProperties("LOCALE" to typeOf<DiscordLocale>())
            providedProperties("FILE" to file.second)
            providedProperties("ARGS" to typeOf<ArgumentMap>())
            providedProperties("MANAGER" to typeOf<AdvancedLocalizationManager>())

            defaultValues.forEach { (name, type) -> providedProperties(name to type) }
            args.forEach { (name, type) -> providedProperties(name to type) }
        }).onSuccess { compiled ->
            val scriptClass = compiled.getClass(null).valueOrThrow().java
            val constructor = scriptClass.constructors.first()

            val resultField = scriptClass.getDeclaredField(compiled.resultField!!.first).apply { isAccessible = true }

            element = Element(script.trim()) { args ->
                runBlocking {
                    val config = ScriptEvaluationConfiguration {
                        providedProperties("LOCALE" to locale)
                        providedProperties("FILE" to file.first)
                        providedProperties("ARGS" to ArgumentMap(args))
                        providedProperties("MANAGER" to this@AdvancedLocalizationManager)

                        defaultValues.forEach { (name, _, value) -> providedProperties(name to value(this@AdvancedLocalizationManager, args)) }
                        args.forEach { (name, value) -> providedProperties(name to value) }
                    }

                    try {
                        val wrappedResult = constructor.newInstance(*config[ScriptEvaluationConfiguration.providedProperties]!!.map { it.value }.toTypedArray())
                        resultField.get(wrappedResult)
                    } catch (e: Exception) {
                        logger.error("Error executing $location#$key for $locale", e)
                        createDefaultValue("\${EXECUTION ERROR}", type)
                    }
                }
            }
            ResultWithDiagnostics.Failure()
        }.onFailure { result ->
            val report = result.reports.find { it.isError() }
            if (report == null) return@onFailure

            if (report.exception != null) logger.error("Error compiling $location#$key for $locale", report.exception)
            else logger.error("Error compiling $location#$key for $locale: ${report.message}")

            element = textElement("\${COMPILATION ERROR}")
        }

        element
    }
}

class ArgumentMap(val args: Map<String, Any?>) : Map<String, Any?> by args {
    override fun toString() = args.toString()
}
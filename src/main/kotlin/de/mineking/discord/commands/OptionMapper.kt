package de.mineking.discord.commands

import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import java.io.InputStream
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

interface OptionMapper<T> {
    fun accepts(manager: CommandManager, type: KType): Boolean
    fun getType(manager: CommandManager, type: KType): OptionType

    fun read(manager: CommandManager, type: KType, context: IOptionContext<*>, name: String): T

    fun configure(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType, option: OptionData)
}

annotation class EnumChoice(val name: String = "", val localize: Boolean = true)

object DefaultOptionMappers {
    val INTEGER = nullSafeOptionMapper<Int>(OptionType.INTEGER) { asString.toIntOrNull() }
    val LONG = nullSafeOptionMapper<Long>(OptionType.INTEGER) { asString.toLongOrNull() }
    val DOUBLE = nullSafeOptionMapper<Double>(OptionType.NUMBER) { asString.toDoubleOrNull() }

    val STRING = nullSafeOptionMapper<String>(OptionType.STRING) { asString }
    val BOOLEAN = nullSafeOptionMapper<Boolean>(OptionType.BOOLEAN) { asBoolean }

    val MENTIONABLE = nullSafeOptionMapper<IMentionable>(OptionType.MENTIONABLE) { asMentionable }
    val USER = nullSafeOptionMapper<User>(OptionType.USER) { asUser }
    val MEMBER = nullSafeOptionMapper<Member>(OptionType.USER) { asMember }
    val CHANNEL = nullSafeOptionMapper<Channel>(OptionType.CHANNEL) { asChannel }
    val ROLE = nullSafeOptionMapper<Role>(OptionType.ROLE) { asRole }

    val ATTACHMENT = nullSafeOptionMapper<Attachment>(OptionType.ATTACHMENT) { asAttachment }
    val INPUT_STREAM = nullSafeOptionMapper<InputStream>(OptionType.ATTACHMENT) { asAttachment.proxy.download().get() }
    val BYTE_ARRAY = nullSafeOptionMapper<ByteArray>(OptionType.ATTACHMENT) { asAttachment.proxy.download().get().readAllBytes() }

    val ENUM = nullSafeOptionMapper<Enum<*>>(OptionType.STRING, { manager, command, option, type -> type.jvmErasure.java.enumConstants
        .map { it as Enum<*> }
        .mapNotNull { enum -> enum.declaringJavaClass.getField(enum.name).getAnnotation(EnumChoice::class.java)?.let { annotation ->
            choice(enum.name, annotation.name.takeIf { it.isNotBlank() } ?: enum.name, localize = annotation.localize).build(manager, command, option, OptionType.STRING)
        } }
        .forEach { addChoices(it) }
    }) { type -> type.jvmErasure.java.enumConstants
        .map { it as Enum<*> }
        .find { it.name == asString }
    }
}

inline fun <reified T> optionMapper(
    optionType: OptionType,
    crossinline configure: OptionData.(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType) -> Unit = { _, _, _, _ -> },
    crossinline parser: IOptionContext<*>.(name: String, type: KType) -> T,
): OptionMapper<T> = object : OptionMapper<T> {
    override fun accepts(manager: CommandManager, type: KType): Boolean = type.isSubtypeOf(typeOf<T>())
    override fun getType(manager: CommandManager, type: KType): OptionType = optionType

    override fun read(manager: CommandManager, type: KType, context: IOptionContext<*>, name: String): T = context.parser(name, type)

    override fun configure(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType, option: OptionData) = option.configure(manager, command, info, type)

    override fun toString(): String = "OptionMapper[type=${typeOf<T>()}]"
}

inline fun <reified T> simpleOptionMapper(
    optionType: OptionType,
    crossinline configure: OptionData.(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType) -> Unit = { _, _, _, _ -> },
    crossinline parser: OptionMapping?.(type: KType) -> T,
): OptionMapper<T> = optionMapper(optionType, configure) { name, type -> getOption(name).parser(type) }

inline fun <reified T> nullSafeOptionMapper(
    type: OptionType,
    crossinline configure: OptionData.(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType) -> Unit = { _, _, _, _ -> },
    crossinline parser: OptionMapping.(type: KType) -> T?
) = simpleOptionMapper(type, configure) { type -> this?.parser(type) }

inline fun <reified T, reified D> optionMapper(
    mapper: OptionMapper<D>,
    crossinline configure: OptionData.(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType) -> Unit = { _, _, _, _ -> },
    crossinline parser: IOptionContext<*>.(value: D, type: KType) -> T
): OptionMapper<T> = object : OptionMapper<T> {
    override fun accepts(manager: CommandManager, type: KType): Boolean = type.isSubtypeOf(typeOf<T>())
    override fun getType(manager: CommandManager, type: KType): OptionType = mapper.getType(manager, type)

    override fun read(manager: CommandManager, type: KType, context: IOptionContext<*>, name: String): T = context.parser(mapper.read(manager, type, context, name), type)

    override fun configure(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType, option: OptionData) {
        mapper.configure(manager, command, info, type, option)
        option.configure(manager, command, info, type)
    }
}

inline fun <reified T, reified D> nullSafeOptionMapper(
    mapper: OptionMapper<D?>,
    crossinline configure: OptionData.(manager: CommandManager, command: SlashCommandImpl, info: OptionInfo, type: KType) -> Unit = { _, _, _, _ -> },
    crossinline parser: IOptionContext<*>.(value: D, type: KType) -> T
) = optionMapper<T?, D?>(mapper, configure) { value, type -> value?.let { parser(it, type) } }
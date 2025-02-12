package de.mineking.discord.commands

import de.mineking.discord.localization.LocalizationFile
import net.dv8tion.jda.api.interactions.DiscordLocale
import kotlin.reflect.KType
import kotlin.reflect.typeOf

data class LocalizationInfo(val default: String, val localization: Map<DiscordLocale, String> = emptyMap())

interface CommandLocalizationHandler {
    fun getCommandDescription(file: LocalizationFile?, command: CommandImpl<*, *>): LocalizationInfo
    fun getOptionDescription(file: LocalizationFile?, command: CommandImpl<*, *>, option: OptionInfo): LocalizationInfo
    fun getChoiceLabel(file: LocalizationFile?, command: CommandImpl<*, *>, option: OptionInfo, choice: Choice): LocalizationInfo
}

class DefaultCommandLocalizationHandler(val prefix: String, val args: Map<String, Pair<KType, (command: CommandImpl<*, *>) -> Any?>> = emptyMap()) : CommandLocalizationHandler {
    private fun CommandImpl<*, *>.path(): String {
        val temp = path
        return temp.joinToString(".subcommands.").substring(temp[0].length)
    }

    private fun createLocalization(file: LocalizationFile, command: CommandImpl<*, *>, key: String): LocalizationInfo {
        file.register(key, args.mapValues { it.value.first }, typeOf<String>())
        val localization = file.manager.locales.associateWith { file.readString(key, it, args.mapValues { it.value.second(command) }) }

        return LocalizationInfo(localization[file.manager.defaultLocale]!!, localization)
    }

    override fun getCommandDescription(file: LocalizationFile?, command: CommandImpl<*, *>): LocalizationInfo {
        val default = if (command is SlashCommandImpl) command.description else command.name
        if (file == null) return LocalizationInfo(default)

        val key = default.takeIf { it != DEFAULT_COMMAND_DESCRIPTION } ?: "$prefix${command.path()}.description"
        return createLocalization(file, command, key)
    }

    private fun optionPath(command: CommandImpl<*, *>, option: OptionInfo) = option.description.takeIf { it != DEFAULT_OPTION_DESCRIPTION } ?: "$prefix${command.path()}.options.${option.name}"

    override fun getOptionDescription(file: LocalizationFile?, command: CommandImpl<*, *>, option: OptionInfo): LocalizationInfo {
        val default = option.description
        if (file == null) return LocalizationInfo(default)

        val key = "${optionPath(command, option)}.description"
        return createLocalization(file, command, key)
    }

    override fun getChoiceLabel(file: LocalizationFile?, command: CommandImpl<*, *>, option: OptionInfo, choice: Choice): LocalizationInfo {
        val default = choice.label
        if (file == null) return LocalizationInfo(default)

        val key = "${optionPath(command, option)}.choices.$default"
        return createLocalization(file, command, key)
    }
}
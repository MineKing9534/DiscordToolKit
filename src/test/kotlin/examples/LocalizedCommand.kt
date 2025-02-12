package examples

import de.mineking.discord.commands.localizedSlashCommand
import de.mineking.discord.commands.requiredOption
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.Locale
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.LocalizationParameter
import de.mineking.discord.localization.Localize
import net.dv8tion.jda.api.interactions.DiscordLocale
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withAdvancedLocalization(listOf(DiscordLocale.ENGLISH_US, DiscordLocale.GERMAN)) //Implicitly uses the first entry as default locale (used for locales that are not supported)
        .withCommandManager {
            localize()

            +localizedSlashCommand<EchoLocalization>("echo") { localization ->
                val text = requiredOption<String>("text")

                execute {
                    reply(localization.commandResponse(event.userLocale, text())).queue()
                }
            }

            updateCommands().queue()
        }
        .build()
}

//See resources/test/de/examples/echo_localization.yaml
interface EchoLocalization : LocalizationFile {
    @Localize
    fun commandResponse(@Locale locale: DiscordLocale, @LocalizationParameter text: String): String
}
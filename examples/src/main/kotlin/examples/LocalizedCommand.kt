package examples

import de.mineking.discord.commands.localizedSlashCommand
import de.mineking.discord.commands.requiredOption
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.DefaultLocalizationManager
import de.mineking.discord.localization.Locale
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.LocalizationParameter
import de.mineking.discord.localization.Localize
import de.mineking.discord.withLocalization
import net.dv8tion.jda.api.interactions.DiscordLocale
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withLocalization<_, DefaultLocalizationManager>() //This is generated at compile time by the gradle plugin. See build.gradle.kts to see the setup for that
        .withCommandManager {
            localize()

            +localizedSlashCommand<EchoLocalization>("echo") { localization ->
                val text = requiredOption<String>("text")

                execute {
                    reply(localization.commandEchoResponse(event.userLocale, text())).queue()
                }
            }

            updateCommands().queue()
        }
        .build()
}

//See resources/text/de/examples/echo_localization.yaml
interface EchoLocalization : LocalizationFile {
    @Localize
    fun commandEchoResponse(@Locale locale: DiscordLocale, @LocalizationParameter text: String): String
}
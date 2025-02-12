package examples

import de.mineking.discord.commands.localizedMenuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.*
import de.mineking.discord.ui.builder.components.*
import de.mineking.discord.ui.localizeForUser
import de.mineking.discord.ui.read
import de.mineking.discord.ui.state
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.DiscordLocale
import setup.createJDA
import java.awt.Color

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withAdvancedLocalization(listOf(DiscordLocale.ENGLISH_US, DiscordLocale.GERMAN)) { import<Color>() }
        .withUIManager { localize() }
        .withCommandManager {
            localize()

            +localizedMenuCommand<MenuLocalization>("test") { localization ->
                var state by state(0)

                localizeForUser {
                    argument("a", state)
                }

                embed(read(localization::testEmbed))

                +label("label")
                +button("button") { state++ }
                +label("constant", label = "Not Localized")
                +label("custom", label = "abc".localize())

                +endRow()

                val test = state

                +menuButton("menu1") { back ->
                    content("$test")
                    +back.asButton("back")
                }

                +localizedMenuButton<SubmenuLocalization>("menu2") { _, back ->
                    +back.asButton("back")
                }
            }

            updateCommands().queue()
        }.build()
}

//See resources/text/de/examples/menu_localization.yaml
interface MenuLocalization : LocalizationFile {
    @Embed fun testEmbed(@Locale locale: DiscordLocale, @LocalizationParameter a: Int): MessageEmbed
}

interface SubmenuLocalization : LocalizationFile
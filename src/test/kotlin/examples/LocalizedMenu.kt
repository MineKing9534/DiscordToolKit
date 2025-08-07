package examples

import de.mineking.discord.commands.localizedMenuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.*
import de.mineking.discord.ui.*
import de.mineking.discord.ui.builder.components.*
import de.mineking.discord.ui.message.message
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
                    bindParameter("a", state)
                }

                message {
                    embed(read(localization::testEmbed))
                }

                +actionRow(
                    label("label"),
                    button("button") { state++ },
                    label("constant", label = "Not Localized"),
                    label("custom", label = "abc".localize())
                )

                val test = state

                +actionRow(
                    menuButton("menu1") { back ->
                        message {
                            content("$test")
                        }

                        +actionRow(back.asButton("back"))
                    },
                    localizedMenuButton<SubmenuLocalization>("menu2") { _, back ->
                        +actionRow(back.asButton("back"))
                    }
                )
            }

            updateCommands().queue()
        }.build()
}

//See resources/text/de/examples/menu_localization.yaml
interface MenuLocalization : LocalizationFile {
    @Embed fun testEmbed(@Locale locale: DiscordLocale, @LocalizationParameter a: Int): MessageEmbed
}

interface SubmenuLocalization : LocalizationFile
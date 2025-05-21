package examples

import de.mineking.discord.commands.localizedMenuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.builder.components.*
import de.mineking.discord.ui.localizeForUser
import de.mineking.discord.ui.state
import net.dv8tion.jda.api.interactions.DiscordLocale
import setup.createJDA
import java.awt.Color

interface ComponentsV2Localization : LocalizationFile

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withAdvancedLocalization(listOf(DiscordLocale.ENGLISH_US, DiscordLocale.GERMAN))
        .withUIManager { localize() }
        .withCommandManager {
            localize()

            +localizedMenuCommand<ComponentsV2Localization>("menu", useComponentsV2 = true) {
                var count by state(0)
                var text by state("")

                localizeForUser {
                    bindParameter("count", count)
                    bindParameter("text", text)
                }

                +container(
                    section(
                        modalButton("modal", component =
                            textInput("text", value = text, required = false)
                        ) {
                            text = it
                        },
                        localizedTextDisplay("text"),
                        localizedTextDisplay("count")
                    ),
                    separator(),
                    actionRow(
                        button("inc", color = ButtonColor.GREEN) { count++ },
                        button("dec", color = ButtonColor.RED) { count-- }
                    ),
                    color = Color.decode("#00FF00")
                )
            }

            updateCommands().queue()
        }
        .build()
}
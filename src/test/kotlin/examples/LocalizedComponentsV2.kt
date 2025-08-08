package examples

import de.mineking.discord.commands.localizedMenuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.builder.components.*
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.localizeForUser
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.state
import net.dv8tion.jda.api.interactions.DiscordLocale
import setup.createJDA

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

                +container(color = 0x00ff00) {
                    +section(
                        modalButton(
                            "modal", component =
                                textInput("text", value = text, required = false)
                        ) {
                            text = it
                        }
                    ) {
                        +localizedTextDisplay("text")
                        +localizedTextDisplay("count")
                    }
                    +separator()
                    +actionRow {
                        +button("inc", color = ButtonColor.GREEN) { count++ }
                        +button("dec", color = ButtonColor.RED) { count-- }
                    }
                }
            }

            updateCommands().queue()
        }
        .build()
}
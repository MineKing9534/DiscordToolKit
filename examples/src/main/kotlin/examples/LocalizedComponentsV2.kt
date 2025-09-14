package examples

import de.mineking.discord.commands.localizedMenuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.DefaultLocalizationManager
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.builder.components.localizedTextDisplay
import de.mineking.discord.ui.builder.components.message.*
import de.mineking.discord.ui.builder.components.modal.textInput
import de.mineking.discord.ui.builder.components.modal.withLocalizedLabel
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.localizeForUser
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.state
import de.mineking.discord.withLocalization
import setup.createJDA

interface ComponentsV2Localization : LocalizationFile

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withLocalization<DefaultLocalizationManager>() //This is generated at compile time by the gradle plugin. See build.gradle.kts to see the setup for that
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
                            "modal", component = textInput("text", value = text, required = false).withLocalizedLabel()
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
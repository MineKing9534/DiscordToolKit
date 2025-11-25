package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.message.ButtonColor
import de.mineking.discord.ui.builder.components.message.actionRow
import de.mineking.discord.ui.builder.components.message.button
import de.mineking.discord.ui.builder.components.message.container
import de.mineking.discord.ui.builder.components.message.modalButton
import de.mineking.discord.ui.builder.components.message.section
import de.mineking.discord.ui.builder.components.message.separator
import de.mineking.discord.ui.builder.components.modal.textInput
import de.mineking.discord.ui.builder.components.modal.withLabel
import de.mineking.discord.ui.builder.components.textDisplay
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.state
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("menu", useComponentsV2 = true) {
                var count by state(0)
                var text by state("")

                +container(color = 0x00ff00) {
                    +section(
                        modalButton(
                            "text", label = "Modal", title = "Enter Text", component =
                                textInput("text", placeholder = "Hello World!", value = text).withLabel("Enter Text")
                        ) {
                            text = it
                        }
                    ) {
                        +textDisplay("Current Text: $text")
                        +textDisplay("Current Count: **$count**")
                    }

                    +separator()
                    +actionRow {
                        +button("inc", label = "+", color = ButtonColor.GREEN) { count++ }
                        +button("dec", label = "-", color = ButtonColor.RED) { count-- }
                    }
                }
            }

            updateCommands().queue()
        }
        .build()
}
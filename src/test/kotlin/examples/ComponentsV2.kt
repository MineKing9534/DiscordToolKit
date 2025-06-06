package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.*
import de.mineking.discord.ui.state
import setup.createJDA
import java.awt.Color

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
                                textInput("text", label = "Enter Text", placeholder = "Hello World!", value = text)
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
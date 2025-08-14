package examples

import de.mineking.discord.commands.createState
import de.mineking.discord.commands.menuCommand
import de.mineking.discord.commands.requiredOption
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.ButtonColor
import de.mineking.discord.ui.builder.components.actionRow
import de.mineking.discord.ui.builder.components.button
import de.mineking.discord.ui.builder.line
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.initialize
import de.mineking.discord.ui.message.message
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.state
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("test") {
                val option = requiredOption<Int>("a") //You can declare options as you would in a normal command
                var optionState by requiredOption<Int>("b").createState(default = 0) //You can store options as state

                var state by state(0) //You can use normal states

                initialize {
                    state = 2 * option(it) //Options without a backing state can only be accessed inside initialize and have to be called with the lambda parameter
                }

                message {
                    content {
                        +line("State: $state")
                        +line("Option: $optionState")
                    }
                }

                +actionRow(
                    button("inc", color = ButtonColor.BLUE, label = "Increase Values") {
                        state++
                        optionState++
                    }
                )
            }

            updateCommands().queue()
        }.build()
}
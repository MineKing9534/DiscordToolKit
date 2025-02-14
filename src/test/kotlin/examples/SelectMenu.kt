package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.selectOption
import de.mineking.discord.ui.builder.components.statefulSingleStringSelect
import de.mineking.discord.ui.builder.components.stringSelect
import de.mineking.discord.ui.state
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("select") {
                val selectedRef = state("A")
                val selected by selectedRef

                content("**Selected**: $selected")

                +statefulSingleStringSelect("stateful", options = listOf(
                    selectOption("A", label = "A"),
                    selectOption("B", label = "B"),
                    selectOption("C", label = "C")
                ), ref = selectedRef)

                +stringSelect("select", min = 1, max = 2, options = listOf(
                    selectOption("A", label = "A") { println("Option Handler A") }, //Option Handlers are called fist
                    selectOption("B", label = "B") { println("Option Handler B") },
                    selectOption("C", label = "C") //You don't have to provide an option handler
                )) { //You can add a handler to the entire select (not required)
                    println("Select Handler: " + event.values)
                }
            }

            updateCommands().queue()
        }.build()
}
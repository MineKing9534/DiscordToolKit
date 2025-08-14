package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.actionRow
import de.mineking.discord.ui.builder.components.button
import de.mineking.discord.ui.cache
import setup.createJDA
import kotlin.random.Random

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("cache") {
                val value = cache {
                    //This is only executed:
                    //- When the menu is initially rendered and sent
                    //- When the button is clicked
                    //But NOT when rerendering the menu after the button was clicked (because the value from the button interaction is cached and reused for that)

                    val value = Random.nextInt()
                    println("Resolve ($phase): $value")
                    "$value"
                }

                println("Config ($phase): $value")

                +actionRow(
                    button("test", label = value) {
                        println(value)
                        forceUpdate()
                    }
                )
            }

            updateCommands().queue()
        }
        .build()
}
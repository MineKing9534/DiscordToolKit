package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.button
import de.mineking.discord.ui.builder.components.label
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("lazy") {
                val value by lazy("DEFAULT") {
                    //This will only print on render and when "b" is clicked
                    println("Lazy load")
                    "lazy"
                }

                //This will print on every render and every interaction
                println("Render")

                +button("a") {
                    //Clicking "a" will not load the lazy value(even though the lazy value is used in the render function that is called before the handler is executed!)
                }

                //Rendering will load the lazy value because it is required
                +button("b", label = value) {
                    //Because the value is used here, clicking "b" will load the lazy value
                    value
                }
            }

            updateCommands().queue()
        }
        .build()
}
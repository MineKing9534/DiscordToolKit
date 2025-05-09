package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.actionRow
import de.mineking.discord.ui.builder.components.button
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

                //This will print on every config call (rerender and interaction)
                println("Config")

                +actionRow(
                    button("a") {
                        //Clicking "a" will not load the lazy value(even though the lazy value is used in the render function that is called before the handler is executed!)

                        //Keep in mind, that if this component would trigger a rerender, the lazy value would still be loaded because an actual render
                        //always requires the value to be resolved in this example
                    },

                    //Rendering will load the lazy value because it is required
                    button("b", label = value) {
                        //Because the value is used here, clicking "b" will load the lazy value
                        value

                        //Using it twice will not load again (result is cached)
                        value

                        //Keep in mind that if this component would trigger a rerender the lazy value would be loaded twice, once for the handler and again for the rerender afterward
                    }
                )
            }

            updateCommands().queue()
        }
        .build()
}
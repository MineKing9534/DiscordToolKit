package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.DeferMode
import de.mineking.discord.ui.builder.components.button
import de.mineking.discord.ui.builder.components.modalButton
import de.mineking.discord.ui.builder.components.textInput
import de.mineking.discord.ui.state
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("modal", defer = DeferMode.UNLESS_PREVENTED) { //Default deferMode ist ALWAYS. You have to set it to anything else if you want to use modals because otherwise the interaction is acknowledged before the handler is called
                val modal = modal("modal") {
                    title("Modal Title")

                    val text = +textInput("text", label = "Text")

                    execute {
                        hook.sendMessage(text()).setEphemeral(true).queue()
                    }
                }

                +button("a", label = "a") { switchMenu(modal) }

                var count by state(0)
                var text by state("")

                //Same as above but simpler
                +modalButton("b", label = "b", component = textInput("text", label = "Text"), title = "Modal Title") {
                    count++
                    hook.sendMessage(it).setEphemeral(true).queue()
                }

                +modalButton("c", label = "c", title = "Modal Title", component = textInput("text", label = "Text", value = text)) {
                    text = it
                }

                content {
                    +"**Clicked B:** $count\n"
                    +"**Text:** $text"
                }
            }

            updateCommands().queue()
        }.build()
}
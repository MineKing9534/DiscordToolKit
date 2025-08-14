package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.actionRow
import de.mineking.discord.ui.builder.components.button
import de.mineking.discord.ui.builder.components.modalButton
import de.mineking.discord.ui.builder.components.textInput
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.message.message
import de.mineking.discord.ui.message.modal
import de.mineking.discord.ui.modal.getValue
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.state
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            //If you change your DEFAULT_DEFER_MODE to ALWAYS, you have to manually change it to anything else (using the defer parameter) for menus that use modals because otherwise the interaction is acknowledged before the handler is called and therefore prevents responding with a modal
            +menuCommand("modal") {
                val modal = modal("modal") {
                    title("Modal Title")

                    val text by +textInput("text", label = "Text")

                    execute {
                        reply(text).setEphemeral(true).queue()
                    }
                }

                var count by state(0)
                var text by state("")

                +actionRow(
                    button("a", label = "a") { switchMenu(modal) },

                    //Same as above but simpler by inlining the modal definition
                    modalButton("b", label = "b", component = textInput("text", label = "Text"), title = "Modal Title") {
                        //Using the hook here instead of reply because the modalButton implementation automatically handles acknowledging the interaction and rerenders the message menu
                        hook.sendMessage(it).setEphemeral(true).queue()
                    },

                    modalButton("c", label = "c", title = "Modal Title", component = textInput("text", label = "Text", value = text)) {
                        count++
                        text = it
                    }
                )

                message {
                    content {
                        +"**Clicked C:** $count\n"
                        +"**Text:** $text"
                    }
                }
            }

            updateCommands().queue()
        }.build()
}
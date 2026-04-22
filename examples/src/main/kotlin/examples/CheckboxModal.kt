package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.message.actionRow
import de.mineking.discord.ui.builder.components.message.button
import de.mineking.discord.ui.builder.components.modal.checkboxGroupOption
import de.mineking.discord.ui.builder.components.modal.radioGroup
import de.mineking.discord.ui.builder.components.modal.requiredCheckbox
import de.mineking.discord.ui.builder.components.modal.withLabel
import de.mineking.discord.ui.message.modal
import de.mineking.discord.ui.modal.getValue
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("checkbox") {
                val modal = modal("checkbox") {
                    val selected by +radioGroup("test", checkboxGroupOption("a"), checkboxGroupOption("b")).withLabel("Radio Group")
                    +requiredCheckbox("require").withLabel("I confirm that this action cannot be undone")

                    execute {
                        reply("Selected: $selected").setEphemeral(true).queue()
                    }
                }

                +actionRow(
                    button("open", label = "open") {
                        switchMenu(modal)
                    }
                )
            }

            updateCommands().queue()
        }.build()
}

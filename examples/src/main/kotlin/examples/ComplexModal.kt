package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.builder.components.message.*
import de.mineking.discord.ui.builder.components.modal.label
import de.mineking.discord.ui.builder.components.modal.textInput
import de.mineking.discord.ui.builder.line
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.modal.createModalComponentFor
import de.mineking.discord.ui.modal.map
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.state
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            +menuCommand("modal", useComponentsV2 = true) {
                var text by state("Initial Text")
                var option by state(Option.A)

                +section(
                    modalButton("modal", label = "Open Modal", component = customModalComponent(FormData(text, option)), title = "Modal Title") {
                        text = it.text
                        option = it.option
                    },
                    buildTextDisplay {
                        +line("**Text:** $text")
                        +line("**Option:** $option")
                    }
                )
            }

            updateCommands().queue()
        }.build()
}

enum class Option { A, B, C }
data class FormData(val text: String, val option: Option)

private fun customModalComponent(value: FormData) = createModalComponentFor<FormData>(
    label(textInput("text", value = value.text), label = "Text", description = "Enter some text"),
    label(stringSelect("option", Option.entries.map { selectOption(it, it.name, default = it == value.option) }), label = "Option", description = "Select an option")
        .map { Option.valueOf(it.first()) }
)

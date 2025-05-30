package examples

import de.mineking.discord.commands.*
import de.mineking.discord.discordToolKit
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withCommandManager {
            +slashCommand("echo", description = "Sends a message as the bot") {
                val text = requiredStringOption("text", description = "The text to send") {
                    replyChoices(
                        choice(currentValue!!.lowercase(), label = currentValue!!.lowercase()),
                        choice(currentValue!!.uppercase(), label = currentValue!!.uppercase())
                    )
                }

                execute {
                    reply(text()).queue()
                }
            }

            +slashCommand("calculate", description = "Calculates a simple operation") {
                val a = requiredDoubleOption("a", description = "First number")
                val operator = requiredEnumOption<Operator>("operator", description = "The operator")
                val b = requiredDoubleOption("b", description = "Second number")

                execute {
                    val result = operator().handler(a(), b()).toString()
                    reply("${a()} ${operator().operator} ${b()} = $result").setEphemeral(true).queue()
                }
            }

            +slashCommand("custom", description = "Command with custom option") {
                val a = customOption("custom")

                execute {
                    reply(a()).setEphemeral(true).queue()
                }
            }

            updateCommands().queue()
        }.build()
}

fun OptionConfig.customOption(name: String) = requiredOption<String>(name, description = "Custom Option")

enum class Operator(val operator: String, val handler: (a: Double, b: Double) -> Double) {
    PLUS("+", { a, b -> a + b }),
    MINUS("-", { a, b -> a - b }),
    MULTIPLY("*", { a, b -> a * b }),
    DIVIDE("/", { a, b -> a / b })
}
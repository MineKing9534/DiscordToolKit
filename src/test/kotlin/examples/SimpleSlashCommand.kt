package examples

import de.mineking.discord.commands.*
import de.mineking.discord.discordToolKit
import setup.createJDA

fun main() {
    val adminId = 0L

    val jda = createJDA()
    discordToolKit(jda)
        .withCommandManager {
            +slashCommand("echo", description = "Sends the given text as the bot") {
                val text = option<String>("text", description = "The text to repeat", required = true).get()
                val amount = option<Int>("amount", description = "The amount to repeat the given text").orElse(1)

                execute {
                    reply((1..amount()).joinToString("\n") { text() }).queue()
                }
            }

            slashCommand("test") {
                val option = option<Int>("option", choices = listOf(
                    choice("value", label = "label")
                ))
            }

            lateinit var command: SlashCommandImpl
            command = +slashCommand("test", description = "Command with subcommands") {
                require({ user.idLong == adminId }, orElse = { event.reply(":x: You are not allowed to use this command").setEphemeral(true).queue() }) //Set precondition for this command and all children (inherited over all layers)

                +slashCommand("group", description = "Subcommand Group") {
                    +slashCommand("group_subcommand_a", description = "Subcommand Group Subcommand A") {
                        execute {
                            reply("Group Subcommand A").setEphemeral(true).queue()
                        }
                    }

                    +slashCommand("group_subcommand_b", description = "Subcommand Group Subcommand B") {
                        execute {
                            reply("Group Subcommand B").setEphemeral(true).queue()
                        }
                    }
                }
                +slashCommand("subcommand", description = "Subcommand") {
                    ignoreParentConditions() //Ignore precondition inherited from parents

                    execute {
                        reply("Subcommand. ${ command.subcommand("group", "group_subcommand_a")!!.asMention }").setEphemeral(true).queue()
                    }
                }
            }

            updateCommands().queue()
        }.build()
}
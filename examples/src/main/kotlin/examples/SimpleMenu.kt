package examples

import de.mineking.discord.commands.channel
import de.mineking.discord.commands.menuCommand
import de.mineking.discord.commands.slashCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.*
import de.mineking.discord.ui.builder.components.message.actionRow
import de.mineking.discord.ui.builder.components.message.button
import de.mineking.discord.ui.builder.components.message.counter
import de.mineking.discord.ui.builder.line
import de.mineking.discord.ui.message.message
import de.mineking.discord.ui.message.replyMenu
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager {
            registerMenu<Unit>("menu") {
                var count by state(0)

                //Code inside "render" is only executed during render and not during other phases (Should be used for expensive renders, like image generation)
                message {
                    content("Current Count: $count")
                }

                +actionRow(
                    button("inc", label = "Increase Counter") { count++ },
                    button("current", label = "$count").disabled()
                )
            }

            registerMenu<Int>("counter") {
                val countRef = state(0)
                var count by countRef

                //Initialize the "count" state to the menu parameter
                initialize { count = it }

                +actionRow(counter("counter", ref = countRef))
            }
        }
        .withCommandManager {
            +slashCommand("counter") {
                execute {
                    replyMenu(manager.manager.get<UIManager>().getMessageMenu("counter"), 5).queue()
                }
            }

            +slashCommand("menu1") {
                execute {
                    replyMenu(manager.manager.get<UIManager>().getMessageMenu("menu")).queue()
                }
            }

            //Same as above but simpler
            +menuCommand("menu2") {
                var count by state(0)

                //Access the channel the menu is sent in. You can access the current event or custom date in a similar fashion
                val channel = channel()

                message {
                    content {
                        +line("Channel: **${channel?.name}**")
                        +line("Current Count: $count")
                    }
                }

                +actionRow(
                    button("inc", label = "Increase Counter") { count++ },
                    button("current", label = "$count").disabled()
                )
            }

            updateCommands().queue()
        }
        .build()
}
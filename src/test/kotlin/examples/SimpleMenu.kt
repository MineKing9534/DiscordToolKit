package examples

import de.mineking.discord.commands.channel
import de.mineking.discord.commands.menuCommand
import de.mineking.discord.commands.slashCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.MessageComponent
import de.mineking.discord.ui.UIManager
import de.mineking.discord.ui.builder.components.actionRow
import de.mineking.discord.ui.builder.components.button
import de.mineking.discord.ui.builder.components.counter
import de.mineking.discord.ui.builder.components.label
import de.mineking.discord.ui.builder.line
import de.mineking.discord.ui.replyMenu
import de.mineking.discord.ui.state
import net.dv8tion.jda.api.components.button.Button
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager {
            registerMenu<Unit>("menu") {
                var count by state(0)

                //Code inside "render" is only executed during render and not during other phases (Should be used for expensive renders, like image generation)
                render {
                    content("Current Count: $count")
                }

                +actionRow(
                    button("inc", label = "Increase Counter") { count++ },
                    label("current", label = "$count")
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
                val channel by channel()

                //Everything that uses the "channel" parameter has to be inside "render" because it is not available during the BUILDER phase
                render {
                    content {
                        +line("Channel: **${channel.name}**")
                        +line("Current Count: $count")
                    }
                }

                +actionRow(
                    button("inc", label = "Increase Counter") { count++ },
                    label("current", label = "$count")
                )
            }

            updateCommands().queue()
        }
        .build()
}
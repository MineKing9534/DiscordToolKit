package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.*
import de.mineking.discord.ui.builder.bold
import de.mineking.discord.ui.builder.components.message.*
import de.mineking.discord.ui.builder.h1
import de.mineking.discord.ui.builder.line
import de.mineking.discord.ui.builder.text
import de.mineking.discord.ui.message.message
import de.mineking.discord.ui.message.submenu
import org.kodein.emoji.Emoji
import org.kodein.emoji.symbols.keycap.listKeycap
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            //Manual pagination
            +menuCommand("paginate1") {
                val pageRef = state(1) { old, new -> println("Page: $old -> $new") } //You can define a state with an update listener
                var page by pageRef

                message {
                    content(if (page == 1) "First Page" else "Second Page")
                }

                +pageSelector("page", max = 2, ref = pageRef)
            }

            //Example list of entries. Can also be generated inside the menu based on state
            val entries = (0 until 100).map { it % 15 + 1 }.map { Character.forDigit(it, 16).toString().repeat(it) }

            //Automated pagination. Shows up to 10 entries per page, where each entry is rendered its own line
            +menuCommand("paginate2") {
                val page = state(1)
                val (content, component) = pagination("page", entries.toList(), display = { index -> text("$index. ") + bold(this) }, perPage = 10, ref = page)

                message {
                    content(content)
                }

                +component
            }

            +menuCommand("complex") {
                val outerRef = state(3) //Integer state with starting value 3
                var outer by outerRef

                message {
                    content("# Root Menu")
                }

                val submenu = submenu("test") {
                    val state by state(0)

                    message {
                        content {
                            +h1("Custom Submenu")
                            +"$state"
                        }
                    }

                    +actionRow(switchMenuButton("complex", label = "Back")) //Simple back button. Will also override the outer state to the value of "state"
                }

                +actionRow(
                    button("a", label = "A") { switchMenu(submenu) { pushDefaults() } }, //Pushed only default values (3 for the parent sate and 0 for the submenu state)

                    //You can also use switchMenuButton to switch to a different menu. Instead of a click handler, you can pass the state builder as a lambda
                    switchMenuButton(submenu, "b", label = "B"), //Implicitly copies all parent state values and pushes the default values of the submenu (Parent state value will be preserved)
                    switchMenuButton(submenu, "c", label = "C") { copyAll(); push(5) } //Keeps the parent state value but explicitly pushes 5 as submenu state
                )

                +actionRow(counter("counter", ref = outerRef))

                //Simpler version for sub-menu if it is only required once
                //Will automatically preserve all parent state values defined before this function call
                val menuA = menuButton("menu", label = "Menu") { back ->
                    val innerRef = state(0)
                    var inner by innerRef

                    //Alternative way to declare states
                    val (step, _, stepRef) = state(1)

                    message {
                        content {
                            +h1("Menu Button")
                            +line("**Parent:** $outer")
                            +line("**Inner: ** $inner")
                        }
                    }

                    +actionRow(back.asButton(label = "Back"))

                    +actionRow(
                        button("parent", label = "Parent").disabled(),
                        counter("parent_counter", ref = outerRef)
                    )

                    +actionRow(
                        button("inner", label = "Inner").disabled(),
                        counter("inner_counter", ref = innerRef, step = step())
                    )

                    +actionRow(statefulSingleStringSelect(
                        "step",
                        placeholder = "Select Step Size",
                        options = (1..10).map { selectOption(it, label = "$it", emoji = Emoji.listKeycap()[2 + it % 11].jda()) },
                        ref = stepRef.map({ "$it" }, { it.toInt() }) //Transform the int state for step to the required string state
                    ))
                }

                //Normal submenus inherit properties from the parent like state or localization context
                //If you detach a submenu, this does NOT happen. This can improve performance and reduce required state size.
                //However, keep in mind that this therefore might cause unexpected behavior when using properties defined in the parent menu.
                val menuB = menuButton("detached", label = "Detached", detach = true) { back ->
                    var count by state(0)

                    message {
                        content {
                            +h1("Detached")
                            +line("Parent: $outer") //The outer state is NOT inherited as actual state to this menu because it is registered as detached. This value will therefore ALWAYS reference the state value during the HANDLE phase
                                                           //Hover, this value is still mutable. It will, however, NOT be persisted over bot restarts (this value is stored in RAM) and is shared between all instances of this menu.
                                                           //I don't think there is any situation where this should be used. It is NOT recommended to use state values from the parent menu in a detached submenu.
                            +line("Count: $count")
                        }
                    }

                    +actionRow(
                        button("inc", label = "+") { count++ },
                        button("ign", label = "Parent +") { outer++ }, //This will NOT update the UI because this state is not part of this menu's state calculations.
                        // However, the value is updated in the BUILD phase RAM version of the state. Updates will be visible after a rerender. See the note above for more details about parent state behavior.
                        back.asButton(label = "Back")
                    )
                }

                +actionRow(menuA, menuB)
            }

            updateCommands().queue()
        }.build()
}
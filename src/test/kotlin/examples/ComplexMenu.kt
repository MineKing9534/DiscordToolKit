package examples

import de.mineking.discord.commands.menuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.ui.DeferMode
import de.mineking.discord.ui.builder.bold
import de.mineking.discord.ui.builder.components.*
import de.mineking.discord.ui.builder.h1
import de.mineking.discord.ui.builder.line
import de.mineking.discord.ui.builder.text
import de.mineking.discord.ui.jda
import de.mineking.discord.ui.state
import org.kodein.emoji.Emoji
import org.kodein.emoji.symbols.keycap.listKeycap
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withUIManager()
        .withCommandManager {
            //Manual pagination
            +menuCommand("paginate1", defer = DeferMode.UNLESS_PREVENTED) { //Change deferMode away from default ALWAYS to allow modals (used by pageSelector)
                val pageRef = state(1) { old, new -> println("Page: $old -> $new") } //You can define a state with an update listener
                var page by pageRef

                render {
                    content(if (page == 1) "First Page" else "Second Page")
                }

                +pageSelector("page", max = 2, ref = pageRef)
            }

            //Example list of entries. Can also be generated inside the menu based on state
            val entries = (0 until 100).map { it % 15 + 1 }.map { Character.forDigit(it, 16).toString().repeat(it) }

            //Automated pagination. Shows up to 10 entries per page, where each entry is rendered its own line
            +menuCommand("paginate2", defer = DeferMode.UNLESS_PREVENTED) {
                val page = state(1)
                val (content, component) = pagination("page", entries.toList(), display = { index -> text("$index. ") + bold(this) }, perPage = 10, ref = page)

                content(content)

                +component
            }

            +menuCommand("complex") {
                val outerRef = state(3) //Integer state with starting value 3
                var outer by outerRef

                content("# Root Menu")

                val submenu = submenu("test") {
                    val state by state(0)
                    content {
                        +h1("Custom Submenu")
                        +"$state"
                    }

                    +actionRow(switchMenuButton("complex", label = "Back")) //Simple back button. Will also override the outer state to the value of "state"
                }

                +actionRow(
                    button("a", label = "A") { switchMenu(submenu) }, //Doesn't pass any value -> default (0) is used
                    switchMenuButton(submenu, "b", label = "B"), //Implicitly copies the n-th state to the n-th state -> "outer" is used as "state"
                    switchMenuButton(submenu, "c", label = "C") { push(5) } //Explicitly pushes 5 as first state -> 5 ist used
                )

                +actionRow(counter("counter", ref = outerRef))

                //Simpler version for sub-menu if it is only required once
                //Will preserve all parent state values
                val menuA = menuButton("menu", label = "Menu") { back ->
                    val innerRef = state(0)
                    var inner by innerRef

                    //Alternative way to declare states
                    val (step, _, stepRef) = state(0)

                    content {
                        +h1("Menu Button")
                        +line("**Parent:** $outer")
                        +line("**Inner: ** $inner")
                    }

                    +actionRow(back.asButton(label = "Back"))

                    +actionRow(
                        label("parent", label = "Parent"),
                        counter("parent_counter", ref = outerRef)
                    )

                    +actionRow(
                        label("inner", label = "Inner"),
                        counter("inner_counter", ref = innerRef, step = step())
                    )

                    +statefulSingleStringSelect(
                        "step",
                        placeholder = "Select Step Size",
                        options = (1..10).map { selectOption(it, label = "$it", emoji = Emoji.listKeycap()[2 + it % 11].jda()) },
                        ref = stepRef.transform({ "$it" }, { it.toInt() }) //Transform the int state for step to the required string state
                    )
                }

                //Normal submenus inherit properties from the parent like state or localization context
                //If you detach a submenu, this does NOT happen. This can improve performance and reduce required state size
                val menuB = menuButton("detached", label = "Detached", detach = true) { back ->
                    var count by state(0)

                    content {
                        +h1("Detached")
                        +line("Parent: $outer") //Outer value will always be 3 (The default value) because the parent state is not preserved in detached menus
                        +line("Count: $count")
                    }

                    +actionRow(
                        button("inc", label = "+") { count++ },
                        button("ign", label = "Parent +") { outer++ }, //This will do nothing
                        back.asButton(label = "Back")
                    )
                }

                +actionRow(menuA, menuB)
            }

            updateCommands().queue()
        }.build()
}
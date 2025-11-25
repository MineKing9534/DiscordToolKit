package examples

import de.mineking.discord.commands.localizedMenuCommand
import de.mineking.discord.discordToolKit
import de.mineking.discord.localization.DefaultLocalizationManager
import de.mineking.discord.localization.Locale
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.localization.LocalizationParameter
import de.mineking.discord.localization.Localize
import de.mineking.discord.localization.localize
import de.mineking.discord.ui.builder.components.message.actionRow
import de.mineking.discord.ui.builder.components.message.button
import de.mineking.discord.ui.builder.components.message.localizedMenuButton
import de.mineking.discord.ui.builder.components.message.menuButton
import de.mineking.discord.ui.disabled
import de.mineking.discord.ui.getValue
import de.mineking.discord.ui.localizeForUser
import de.mineking.discord.ui.message.message
import de.mineking.discord.ui.read
import de.mineking.discord.ui.setValue
import de.mineking.discord.ui.state
import de.mineking.discord.withLocalization
import net.dv8tion.jda.api.interactions.DiscordLocale
import setup.createJDA

fun main() {
    val jda = createJDA()
    discordToolKit(jda)
        .withLocalization<DefaultLocalizationManager>() //This is generated at compile time by the gradle plugin. See build.gradle.kts to see the setup for that
        .withUIManager { localize() }
        .withCommandManager {
            localize()

            +localizedMenuCommand<MenuLocalization>("test") { localization ->
                var state by state(0)

                localizeForUser {
                    bindParameter("a", state)
                }

                message {
                    content(read(localization::testContent))
                }

                +actionRow(
                    button("label").disabled(),
                    button("button") { state++ },
                    button("constant", label = "Not Localized").disabled(),
                    button("custom", label = "abc".localize()).disabled()
                )

                val test = state

                +actionRow(
                    menuButton("menu1") { back ->
                        message {
                            content("$test")
                        }

                        +actionRow(back.asButton("back"))
                    },
                    localizedMenuButton<SubmenuLocalization>("menu2") { _, back ->
                        +actionRow(back.asButton("back"))
                    }
                )
            }

            updateCommands().queue()
        }.build()
}

//See resources/text/de/examples/menu_localization.yaml
interface MenuLocalization : LocalizationFile {
    @Localize
    fun testContent(@Locale locale: DiscordLocale, @LocalizationParameter a: Int): String
}

interface SubmenuLocalization : LocalizationFile
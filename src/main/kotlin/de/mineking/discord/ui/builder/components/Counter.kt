package de.mineking.discord.ui.builder.components

import de.mineking.discord.ui.MessageComponent
import de.mineking.discord.ui.State
import de.mineking.discord.ui.createMessageComponent

fun counter(
    name: String,
    min: Int = 0,
    max: Int = Integer.MAX_VALUE,
    step: Int = 1,
    ref: State<Int>
): MessageComponent {
    var count by ref

    return createMessageComponent(
        button("$name-dec", color = ButtonColor.RED, label = "-") { count -= step }.disabled(count - step < min),
        label(name, label = "$count"),
        button("$name-inc", color = ButtonColor.GREEN, label = "+") { count += step }.disabled(count + step > max),
    )
}
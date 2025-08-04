@file:OptIn(ExperimentalContracts::class)

package de.mineking.discord.ui

import de.mineking.discord.localization.LocalizationFile
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.interactions.DiscordLocale
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty

enum class MenuCallbackPhase { BUILD, RENDER, HANDLE }

@MenuMarker
interface MenuCallbackContext {
    val phase: MenuCallbackPhase
}

interface MenuConfig<M, L : LocalizationFile?> : MenuCallbackContext {
    val configState: MenuConfigState

    val context: MenuContext<M>
    val menu: Menu<M, *, L>
}

fun interface Lazy<out T> {
    suspend fun getValue(): T

    val value get() = runBlocking { getValue() }
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value
}

@Suppress("NOTHING_TO_INLINE")
inline fun MenuCallbackContext.isBuild() = phase == MenuCallbackPhase.BUILD

@Suppress("NOTHING_TO_INLINE")
inline fun MenuCallbackContext.isRender() = phase == MenuCallbackPhase.RENDER

inline fun MenuCallbackContext.render(handler: () -> Unit) {
    contract {
        callsInPlace(handler, InvocationKind.AT_MOST_ONCE)
    }

    if (isRender()) handler()
}

inline fun <T> MenuCallbackContext.renderValue(default: T, handler: () -> T): T {
    contract {
        callsInPlace(handler, InvocationKind.AT_MOST_ONCE)
    }

    return if (isRender()) handler()
    else default
}

inline fun <T> MenuCallbackContext.renderValue(handler: () -> T): T? {
    contract {
        callsInPlace(handler, InvocationKind.AT_MOST_ONCE)
    }

    return renderValue(null, handler)
}

@Suppress("UNCHECKED_CAST")
inline fun <T> MenuConfig<*, *>.setup(block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val value = if (menu.setup.size <= configState.currentSetup) block().also { menu.setup += it }
    else menu.setup[configState.currentSetup] as T

    configState.currentSetup++

    return value
}

inline fun <M, T> MenuConfig<M, *>.parameter(
    default: () -> T,
    initial: (param: M) -> T,
    render: HandlerContext<M, *>.() -> T
): T {
    contract {
        callsInPlace(default, InvocationKind.AT_MOST_ONCE)
        callsInPlace(initial, InvocationKind.AT_MOST_ONCE)
        callsInPlace(render, InvocationKind.AT_MOST_ONCE)
    }

    val context = context
    return when (context) {
        is InitialMenuContext<M> -> initial(context.parameter)
        is HandlerContext<M, *> -> render(context)
        else -> default()
    }
}

inline fun <M> MenuConfig<M, *>.initialize(block: (param: M) -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val context = context
    if (context is InitialMenuContext<M>) block(context.parameter)
}

fun <T> MenuConfig<*, *>.lazy(default: T, block: suspend () -> T): Lazy<T> =
    MenuLazyImpl(menu, active = isRender(), default = default, provider = block).also { context.lazy += it }

fun <T> MenuConfig<*, *>.lazy(block: suspend () -> T) = lazy(null, block)

inline fun MenuConfig<*, *>.localize(locale: DiscordLocale, config: LocalizationContext.() -> Unit = {}) {
    context.localizationContext = LocalizationContext(locale).also(config)
}

@Suppress("NOTHING_TO_INLINE")
inline val MenuConfig<*, *>.currentLocalizationConfig get() = context.localizationContext

//TODO support cache

class MenuConfigState(val menu: Menu<*, *, *>) {
    @PublishedApi
    internal var currentSetup = 0

    @PublishedApi
    internal var currentState = 0
}
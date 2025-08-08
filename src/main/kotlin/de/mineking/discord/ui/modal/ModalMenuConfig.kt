package de.mineking.discord.ui.modal

import de.mineking.discord.localization.DEFAULT_LABEL
import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import kotlin.reflect.KProperty

typealias ModalConfigurator<M> = suspend ModalMenuConfig<M, *>.() -> Unit
typealias LocalizedModalConfigurator<M, L> = suspend ModalMenuConfig<M, L>.(localization: L) -> Unit

fun interface ModalResult<T> {
    fun getValue(): T
}

operator fun <T> ModalResult<T>.getValue(thisRef: Any?, property: KProperty<*>): T = getValue()

interface ModalMenuConfig<M, L : LocalizationFile?> : MenuConfig<M, L> {
    operator fun <T> ModalComponent<T>.unaryPlus(): ModalResult<T>

    fun title(title: CharSequence)
    fun execute(handler: ModalHandler<M>)
}

sealed class ModalMenuConfigImpl<M, L : LocalizationFile?>(
    override val menu: ModalMenu<M, L>,
    override val phase: MenuCallbackPhase,
    override val context: MenuContext<M>
) : ModalMenuConfig<M, L> {
    override val configState = MenuConfigState(menu)
}

class ModalMenuBuilder<M, L : LocalizationFile?>(menu: ModalMenu<M, L>) : ModalMenuConfigImpl<M, L>(menu, MenuCallbackPhase.BUILD, BuildMenuContext(menu)) {
    internal val components = mutableListOf<ModalComponent<*>>()

    override fun <T> ModalComponent<T>.unaryPlus(): ModalResult<T> {
        components += this
        return emptyModalResult<T>()
    }
    override fun title(title: CharSequence) {}
    override fun execute(handler: ModalHandler<M>) {}
}

class ModalMenuRenderer<M, L : LocalizationFile?>(
    menu: ModalMenu<M, L>,
    context: MenuContext<M>
) : ModalMenuConfigImpl<M, L>(menu, MenuCallbackPhase.RENDER, context) {
    internal val components = mutableListOf<ModalComponent<*>>()
    internal var title: CharSequence = DEFAULT_LABEL
        private set

    override fun <T> ModalComponent<T>.unaryPlus(): ModalResult<T> {
        components += this
        return emptyModalResult()
    }

    override fun title(title: CharSequence) {
        this.title = title
    }

    override fun execute(handler: ModalHandler<M>) {}
}

class ModalMenuExecutor<M, L : LocalizationFile?>(
    menu: ModalMenu<M, L>,
    override val context: ModalContext<M>
) : ModalMenuConfigImpl<M, L>(menu, MenuCallbackPhase.HANDLE, context) {
    val handlers = mutableListOf<ModalHandler<M>>()

    override fun <T> ModalComponent<T>.unaryPlus() = object : ModalResult<T> {
        override fun getValue() = handle(context)
    }

    override fun title(title: CharSequence) {}

    override fun execute(handler: ModalHandler<M>) {
        handlers += handler
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T> emptyModalResult(): ModalResult<T> = EmptyModalResult as ModalResult<T>
internal object EmptyModalResult : ModalResult<Nothing> {
    override fun getValue(): Nothing {
        error("Component value cannot be used during render")
    }
}
package de.mineking.discord.localization

val DEFAULT_LABEL = object : CharSequence by "" {}
class LocalizedString internal constructor(val name: String) : CharSequence by name {
    override fun toString() = name
}

fun CharSequence.localize(localize: Boolean = true) = if (!localize || shouldLocalize() || isDefault()) this else LocalizedString(this.toString())
fun CharSequence?.shouldLocalize() = this is LocalizedString
fun CharSequence?.isDefault() = this === DEFAULT_LABEL
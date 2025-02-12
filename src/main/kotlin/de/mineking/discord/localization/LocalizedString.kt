package de.mineking.discord.localization

const val LOCALIZATION_PREFIX = "§§"

fun String.localize() = if (shouldLocalize()) this else "$LOCALIZATION_PREFIX$this"
fun String.shouldLocalize() = startsWith(LOCALIZATION_PREFIX)
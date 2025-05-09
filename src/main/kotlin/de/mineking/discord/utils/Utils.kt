package de.mineking.discord.utils

import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer

inline fun <reified T : Channel> IGuildChannelContainer<in T>.getChannel(id: Long) = getChannelById(T::class.java, id)
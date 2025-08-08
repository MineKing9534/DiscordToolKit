package de.mineking.discord.ui

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.IntegrationOwners
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback

sealed class MenuContext<M>(override val stateData: StateData) : StateContainer {
    override val cache: MutableList<Any?> = mutableListOf()

    @PublishedApi
    internal val lazy = mutableListOf<MenuLazyImpl<*>>()

    @PublishedApi
    internal var localizationContext: LocalizationContext? = null
}

class BuildMenuContext<M>(menu: Menu<*, *, *>) : MenuContext<M>(StateData(mutableListOf(), menu.states))

class InitialMenuContext<M>(stateData: StateData, val parameter: M) : MenuContext<M>(stateData) {
    override val cache: MutableList<Any?> = mutableListOf()
}

abstract class HandlerContext<M, out E>(
    stateData: StateData,
    val event: E
) : MenuContext<M>(stateData), IMessageEditCallback by event, IReplyCallback by event
        where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback
{

    abstract val message: Message

    val after: MutableList<() -> Unit> = mutableListOf()

    fun then(handler: () -> Unit) {
        after += handler
    }

    override fun getIdLong(): Long = event.idLong
    override fun getTypeRaw(): Int = event.typeRaw
    override fun getToken(): String = event.token
    override fun getGuild(): Guild? = event.guild
    override fun getUser(): User = event.user
    override fun getMember(): Member? = event.member
    override fun isAcknowledged(): Boolean = event.isAcknowledged
    override fun getChannel(): Channel? = event.channel
    override fun getChannelIdLong(): Long = event.channelIdLong
    override fun getUserLocale(): DiscordLocale = event.userLocale
    override fun getEntitlements(): MutableList<Entitlement> = event.entitlements
    override fun getJDA(): JDA = event.jda
    override fun getHook(): InteractionHook = event.hook
    override fun getContext(): InteractionContextType = event.context
    override fun getIntegrationOwners(): IntegrationOwners = event.integrationOwners
}

class TransferContext<M, E>(
    stateData: StateData,
    event: E,
    override val message: Message
) : HandlerContext<M, E>(stateData, event) where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback {
    override val cache: MutableList<Any?> = mutableListOf()
}
package de.mineking.discord.ui.message

import de.mineking.discord.localization.LocalizationFile
import de.mineking.discord.ui.*
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.components.ActionComponent
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.attribute.IDisableable
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData

fun IMessageEditCallback.disableComponents(message: Message) = editComponents(message.components.map {
    if (it is IDisableable) it.asDisabled() as MessageTopLevelComponent
    else it
}).useComponentsV2(message.isUsingComponentsV2)

@Suppress("UNCHECKED_CAST")
fun Collection<MessageComponent<out MessageTopLevelComponent>>.render(id: IdGenerator, config: MessageMenuConfig<*, *>, force: Boolean = false) = this
    .map { if (force) it.visibleIf() else it }
    .flatMap {
        try {
            it.render(config, id)
        } catch (e: Exception) {
            throw RuntimeException("Error rendering component $it", e)
        }
    }

class MessageMenu<M, L : LocalizationFile?>(
    manager: UIManager, name: String, defer: DeferMode,
    val useComponentsV2: Boolean,
    localization: L,
    val config: LocalizedMessageMenuConfigurator<M, L>,
    val handler: MessageMenuHandler
) : Menu<M, GenericComponentInteractionCreateEvent, L>(manager, name, defer, localization) {
    suspend fun render(state: MenuContext<M>): MessageEditData {
        val renderer = MessageMenuRenderer(this, state)
        return handler.render(renderer, this, state)
    }

    override suspend fun handle(event: GenericComponentInteractionCreateEvent) {
        if (defer == DeferMode.ALWAYS) event.disableComponents(event.message).queue()

        val name = event.componentId.split(":", limit = 3)[1]

        val data = event.message.decodeState()
        val context = ComponentContext<M, GenericComponentInteractionCreateEvent>(this, decodeState(data), event)

        val finder = MessageMenuComponentFinder(name, this, context)
        handler.handleComponent(finder, this, context, data, name)
    }

    suspend fun update(context: HandlerContext<M, *>) {
        try {
            if (defer != DeferMode.NEVER) {
                if (defer == DeferMode.UNLESS_PREVENTED && !context.isAcknowledged) context.disableComponents(context.message).queue()
                context.hook.editOriginal(render(context)).queue()
            } else if (!context.isAcknowledged) context.editMessage(render(context)).queue()
            else context.hook.editOriginal(render(context)).queue()
        } catch (_: RenderTermination) {
            if (!context.event.isAcknowledged) context.deferEdit().queue()
        }
    }

    suspend fun createInitial(param: M): MessageEditData {
        val state = InitialMenuContext(StateData.createInitial(states), param)
        return render(state)
    }
}

fun MessageEditData.toCreateData() = MessageCreateData.fromEditData(this)

suspend fun MessageChannel.sendMenu(menu: MessageMenu<Unit, *>) = sendMenu(menu, Unit)
suspend fun <C : MessageChannel> C.sendChannelMenu(menu: MessageMenu<in C, *>) = sendMenu(menu, this)
suspend fun <M> MessageChannel.sendMenu(menu: MessageMenu<in M, *>, param: M) = sendMessage(menu.createInitial(param).toCreateData())

suspend fun IReplyCallback.replyMenu(menu: MessageMenu<Unit, *>, ephemeral: Boolean = true) = replyMenu(menu, Unit, ephemeral)
suspend fun IReplyCallback.replyChannelMenu(menu: MessageMenu<in MessageChannel, *>, ephemeral: Boolean = true) = replyMenu(menu, messageChannel, ephemeral)
suspend fun <C : IReplyCallback> C.replyEventMenu(menu: MessageMenu<in C, *>, ephemeral: Boolean = true) = replyMenu(menu, this, ephemeral)
suspend fun <M> IReplyCallback.replyMenu(menu: MessageMenu<in M, *>, param: M, ephemeral: Boolean = true): RestAction<*> =
    if (isAcknowledged) hook.sendMessage(menu.createInitial(param).toCreateData()).setEphemeral(true)
    else reply(menu.createInitial(param).toCreateData()).setEphemeral(ephemeral)

fun Message.decodeState() = componentTree.findAll(ActionComponent::class.java).mapNotNull { it.customId }.decodeState(3)

fun <M, E> Message.rerenderBlocking(menu: MessageMenu<M, *>, event: E): RestAction<*> where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback = runBlocking {
    rerender(menu, event)
}

suspend fun <M, E> Message.rerender(menu: MessageMenu<M, *>, event: E): RestAction<*> where E : GenericInteractionCreateEvent, E : IMessageEditCallback, E : IReplyCallback {
    val context = TransferContext<M, E>(menu.decodeState(decodeState()), event, this)
    return editMessage(menu.render(context))
}
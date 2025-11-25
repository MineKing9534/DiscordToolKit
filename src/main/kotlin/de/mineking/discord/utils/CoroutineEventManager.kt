package de.mineking.discord.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mu.KLogger
import mu.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.hooks.IEventManager
import net.dv8tion.jda.api.requests.RestAction
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

fun interface CoroutineEventListener {
    suspend fun onEvent(event: GenericEvent)
}

fun createCoroutineScope(logger: KLogger, dispatcher: CoroutineDispatcher): CoroutineScope {
    val parent = SupervisorJob()
    return CoroutineScope(dispatcher + parent + CoroutineExceptionHandler { _, throwable ->
        logger.error("Uncaught exception from coroutine", throwable)
        if (throwable is Error) {
            parent.cancel()
            throw throwable
        }
    })
}

class CoroutineEventManager(
    val scope: CoroutineScope = createCoroutineScope(logger, Dispatchers.Default)
) : IEventManager {
    private val listeners = CopyOnWriteArrayList<Any>()

    override fun handle(event: GenericEvent) {
        scope.launch {
            for (listener in listeners) try {
                runListener(listener, event)
            } catch (ex: Exception) {
                logger.error("Uncaught exception in event listener", ex)
            }
        }
    }

    private suspend fun runListener(listener: Any, event: GenericEvent) = when (listener) {
        is CoroutineEventListener -> listener.onEvent(event)
        is EventListener -> listener.onEvent(event)
        else -> Unit
    }

    override fun register(listener: Any) {
        require(listener is EventListener || listener is CoroutineEventListener)
        listeners.add(listener)
    }

    override fun getRegisteredListeners() = mutableListOf(listeners)

    override fun unregister(listener: Any) {
        listeners.remove(listener)
    }

    inline fun <reified T : GenericEvent> listen(crossinline consumer: suspend T.() -> Unit) = object : CoroutineEventListener {
        override suspend fun onEvent(event: GenericEvent) {
            if (event is T) consumer(event)
        }
    }.also { register(it) }
}

inline fun <reified T : GenericEvent> JDA.listen(crossinline consumer: suspend T.() -> Unit) = (eventManager as CoroutineEventManager).listen(consumer)

suspend fun <T> RestAction<T>.await() = submit().await()
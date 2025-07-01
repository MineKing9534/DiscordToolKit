package de.mineking.discord.commands

interface CommandExecutor {
    suspend fun <C : ICommandContext<*>> C.execute(command: CommandImpl<C, *>)

    companion object {
        val DEFAULT = object : CommandExecutor {
            override suspend fun <C : ICommandContext<*>> C.execute(command: CommandImpl<C, *>) {
                try {
                    command.handle(this)
                } catch (_: CommandTermination) {}
            }
        }
    }
}

fun CommandExecutor(handler: suspend ICommandContext<*>.(CommandImpl<*, *>) -> Unit) = object : CommandExecutor {
    override suspend fun <C : ICommandContext<*>> C.execute(command: CommandImpl<C, *>) = handler(command)
}

inline fun <reified E: Throwable> CommandExecutor.handleException(
    crossinline handler: suspend ICommandContext<*>.(CommandImpl<*, *>, E) -> Unit
) = object : CommandExecutor {
    override suspend fun <C : ICommandContext<*>> C.execute(command: CommandImpl<C, *>) = try {
        with(this@handleException) {
            this@execute.execute(command)
        }
    } catch (e: Throwable) {
        if (e !is E) throw e
        try {
            handler(command, e)
        } catch (_: CommandTermination) {}
    }
}
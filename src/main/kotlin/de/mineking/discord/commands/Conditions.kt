package de.mineking.discord.commands

fun <C : ICommandContext<*>> GenericCommandBuilder<C>.condition(condition: ExecutionCondition<C>) = before(condition)
fun <C : ICommandContext<*>> GenericCommandBuilder<C>.condition(inherit: Boolean = true, condition: ExecutionCondition<C>) = before(inherit, condition)

fun <C : ICommandContext<*>> GenericCommandBuilder<C>.require(condition: C.() -> Boolean, orElse: CommandHandler<C>, inherit: Boolean = true) = condition(inherit) {
    if (!condition(it)) {
        orElse(it)
        false
    } else true
}

fun interface ExecutionCondition<C : ICommandContext<*>> : BeforeHandler<C> {
    override val inherit: Boolean get() = true

    fun check(context: C): Boolean
    fun handleForbidden(context: C) {
        if (!context.event.isAcknowledged) context.event.deferReply(true).flatMap { it.deleteOriginal() }.queue()
    }

    override operator fun invoke(context: C) {
        if (!check(context)) {
            handleForbidden(context)
            context.terminateCommand()
        }
    }
}
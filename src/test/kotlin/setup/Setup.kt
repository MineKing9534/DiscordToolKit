package setup

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder

fun createJDA(): JDA = JDABuilder.createDefault(System.getProperty("TOKEN"))
    .build().apply { awaitReady() }
package ts3musicbot.client

import ts3musicbot.util.BotSettings
import ts3musicbot.util.CommandRunner

open class Client(open val botSettings: BotSettings) {
    protected val commandRunner = CommandRunner()

    protected fun encode(message: String): String {
        val distro =
            commandRunner.runCommand("cat /etc/os-release", printOutput = false).outputText.lines()
        return if (
            distro.any {
                it.lowercase().contains("id(_like)?=\"?debian\"?".toRegex())
            }
        ) {
            message.replace(" ", "\\s")
                .replace("\n", "\\\\\\n")
                .replace("/", "\\/")
                .replace("|", "\\\\p")
                .replace("'", "\\\\'")
                .replace("\"", "\\\"")
                .replace("&quot;", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$")
                .replace("[", "\\\\\\\\\\[")
                .replace("]", "\\\\\\\\\\]")
        } else {
            message.replace(" ", "\\s")
                .replace("\n", "\\n")
                .replace("/", "\\/")
                .replace("|", "\\p")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("&quot;", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$")
                .replace("[", "\\\\\\[")
                .replace("]", "\\\\\\]")
        }
    }

    protected fun decode(message: String) =
        message.replace("\\s", " ")
            .replace("\\n", "\n")
            .replace("\\/", "/")
            .replace("\\p", "|")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\`", "`")
            .replace("\\$", "$")

    open suspend fun joinChannel(
        channelName: String = botSettings.channelName,
        channelPassword: String = botSettings.channelPassword,
    ): Boolean = false

    open fun sendMsgToChannel(message: String) {}

    open fun getClientList(): List<String> = emptyList()

    open fun getChannelList(): List<String> = emptyList()
}

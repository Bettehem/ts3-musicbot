package ts3_musicbot.client

import ts3_musicbot.util.BotSettings

open class Client(open val botSettings: BotSettings) {
    open suspend fun joinChannel(channelName: String = botSettings.channelName, channelPassword: String = botSettings.channelPassword): Boolean = false
    open fun sendMsgToChannel(message: String) {}
    open fun getClientList(): List<String> = emptyList()
    open fun getChannelList(): List<String> = emptyList()
}
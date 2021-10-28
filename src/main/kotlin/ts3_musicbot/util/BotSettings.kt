package ts3_musicbot.util

class BotSettings(
    var apiKey: String = "", var serverAddress: String = "", var serverPort: String = "",
    var serverPassword: String = "", var channelName: String = "", var channelFilePath: String = "",
    var nickname: String = "", var market: String = "", var spotifyPlayer: String = "spotify",
    var useOfficialTsClient: Boolean = true, var mpvVolume: Int = ts3_musicbot.mpvVolume
)

package ts3_musicbot.util

class BotSettings(
    var apiKey: String = "",
    var serverAddress: String = "",
    var serverPort: Int = 9987,
    var serverPassword: String = "",
    var channelName: String = "",
    var channelPassword: String = "",
    var channelFilePath: String = "",
    var nickname: String = "MusicBot",
    var market: String = "",
    var spotifyPlayer: String = "spotify",
    var spotifyUsername: String = "",
    var spotifyPassword: String = "",
    var useOfficialTsClient: Boolean = true,
    var scVolume: Int = 77,
    var ytVolume: Int = 89
)

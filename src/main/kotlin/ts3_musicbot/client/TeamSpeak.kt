package ts3_musicbot.client

class TeamSpeak(
    val nickname: String,
    val serverAddress: String,
    val serverPassword: String = "",
    val channelName: String = "",
    val serverPort: Int = 9987
)

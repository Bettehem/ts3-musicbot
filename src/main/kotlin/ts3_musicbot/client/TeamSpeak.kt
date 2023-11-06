package ts3_musicbot.client

import com.github.manevolent.ts3j.event.TS3Listener
import com.github.manevolent.ts3j.identity.LocalIdentity
import com.github.manevolent.ts3j.protocol.client.ClientConnectionState
import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket
import ts3_musicbot.util.BotSettings
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress

class TeamSpeak(botSettings: BotSettings) : Client(botSettings), TS3Listener {
    private val clientSocket: LocalTeamspeakClientSocket = LocalTeamspeakClientSocket()

    init {
        clientSocket.addListener(this)
        clientSocket.setIdentity(getIdentity())
        clientSocket.nickname = botSettings.nickname
    }

    fun addListener(listener: TS3Listener) = clientSocket.addListener(listener)

    private fun getIdentity(): LocalIdentity {
        val identityFile = File("${botSettings.nickname}_${botSettings.serverAddress}.identity")
        return if (identityFile.exists()) {
            LocalIdentity.read(identityFile)
        } else {
            val newIdentity = LocalIdentity.generateNew(10)
            newIdentity.save(identityFile)
            newIdentity
        }
    }

    /**
     * Connect to a server
     * @param address server address
     * @param password server password
     * @param port server port
     * @return returns true if is connected
     */
    fun connect(
        address: String = botSettings.serverAddress,
        password: String = botSettings.serverPassword,
        port: Int = botSettings.serverPort
    ): Boolean {
        //connect to server and timeout if no connection after 10 seconds
        clientSocket.connect(InetSocketAddress(InetAddress.getByName(address), port), password, 10000L)
        clientSocket.waitForState(ClientConnectionState.CONNECTED, 10000L)
        clientSocket.subscribeAll()

        return clientSocket.isConnected
    }

    /**
     * Disconnect from current server
     */
    fun disconnect() {
        clientSocket.disconnect("Disconnecting")
    }

    /**
     * Reconnect to the current server
     */
    fun reconnect() {
        disconnect()
        connect()
    }

    /**
     * Get current channel list
     * @return returns a list of Strings, each containing a channel's data
     * A channel's data will look something like this:
     * cid=90 pid=6 channel_order=85 channel_name=Test\sChannel channel_flag_are_subscribed=1 total_clients=0
     */
    override fun getChannelList(): List<String> = clientSocket.listChannels().map {
        encode("cid=${it.id} pid=${it.parentChannelId} channel_order=${it.order} channel_name=${it.name} channel_flag_are_subscribed=1 total_clients=${it.totalClients}")
    }

    /**
     * Get a list of clients on the current server.
     * @return returns a list of Strings, each containing a client's info
     * A client's data will look something like this
     * clid=83 cid=8 client_database_id=100 client_nickname=TeamSpeakUser client_type=0
     */
    override fun getClientList(): List<String> = clientSocket.listClients().map {
        encode("clid=${it.id} cid=${it.channelId} client_database_id=${it.databaseId} client_nickname=${it.nickname} client_type=${it.type}")
    }

    /**
     * get a channel's id
     * @param channelName path to channel
     * @return returns the channel's id
     */
    private fun getChannelId(channelName: String): Int {
        val targetChannel = channelName.substringAfterLast("/")
        val channelList = clientSocket.listChannels()
        val channels = if (channelList.any { it.name == targetChannel })
            channelList.filter { it.name == targetChannel }
        else emptyList()

        var targetCid = 0
        channelLoop@ for (channelData in channels) {
            val subChannels = channelName.split("/")
            val channelPath = ArrayList<String>()
            channelPath.addAll(subChannels.reversed())
            channelPath.removeFirst()

            var pid = channelData.parentChannelId
            while (channelPath.isNotEmpty()) {
                fun getChannelName(cid: Int) = channelList.first { it.id == cid }.name

                fun checkPid(pid: Int, name: String) = getChannelName(pid) == name
                if (checkPid(pid, channelPath.first())) {
                    pid = channelList.first { it.id == pid }.parentChannelId
                    channelPath.removeFirst()
                } else {
                    continue@channelLoop
                }
            }
            targetCid = channelData.id
        }
        return targetCid
    }

    /**
     * Join a channel
     * @param channelName name of the channel to join
     * @param channelPassword channel password
     * @return returns true if successful
     */
    override suspend fun joinChannel(channelName: String, channelPassword: String): Boolean {
        try {
            clientSocket.joinChannel(getChannelId(channelName), channelPassword)
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to join channel!")
        }
        return clientSocket.getClientInfo(clientSocket.clientId).channelId == getChannelId(channelName)
    }

    /**
     * Send a message to current channel
     * @param message the message to send
     */
    override fun sendMsgToChannel(message: String) {
        val tsCharLimit = 8192

        /**
         * Send a message to the current TeamSpeak channel's chat via the official client.
         * @param message The message that should be sent. Max length is 8192 chars and messages need to be encoded!
         */
        fun sendTeamSpeakMessage(message: String) {
            try {
                clientSocket.sendChannelMessage(clientSocket.getClientInfo(clientSocket.clientId).channelId, message)
            } catch (e: Exception) {
                e.printStackTrace()
                println("Failed to send message!")
            }
        }


        //splits the message in to size of tsCharLimit at most
        //and then returns the result as a pair. First item contains the
        //correctly sized message, and the second part contains anything that is left over.
        fun splitMessage(msg: String): Pair<String, String> {
            val escapedLength = encode(
                msg.substring(
                    0,
                    if (msg.length > tsCharLimit)
                        tsCharLimit + 1
                    else
                        msg.lastIndex + 1
                )
            ).length
            val charLimit = if (escapedLength <= tsCharLimit) msg.length else {
                //address the extended message length that the escape characters add
                val compensation = escapedLength - msg.substring(
                    0,
                    if (msg.length > tsCharLimit)
                        tsCharLimit
                    else
                        msg.lastIndex
                ).length
                //first split the message in to a size that fits tsCharLimit
                msg.substring(0, tsCharLimit - compensation + 1).let { str ->
                    //find index where to split the string.
                    //Only check at most 10 last lines from the message.
                    str.lastIndexOf(
                        "\n\n",
                        "\\n".toRegex().findAll(str).map { it.range.first }.toList().let {
                            if (it.size < 10 && str.length < tsCharLimit - compensation + 1) 0
                            else it.reversed().last()
                        }
                    ).let { index ->
                        if (index == -1) {
                            //no empty lines were found so find the last newline character
                            //and use that as the index
                            str.let { text ->
                                text.indexOfLast { it == '\n' }.let { lastNewline ->
                                    if (lastNewline == -1) {
                                        //if no newlines are found, use the last space
                                        text.indexOfLast { it == ' ' }.let {
                                            if (it == -1) tsCharLimit else it
                                        }
                                    } else lastNewline
                                }
                            }
                        } else {
                            index + 1
                        }
                    }
                }
            }
            return Pair(
                msg.substring(0, charLimit),
                if (escapedLength > tsCharLimit)
                    msg.substring(charLimit, msg.lastIndex + 1)
                else
                    ""
            )
        }

        var msg = message
        while (true) {
            //If msg has more tha one line, add an empty line to the start of the message if there isn't one already.
            if (msg.lines().size > 1)
                msg = msg.replace("^\n?".toRegex(), ":\n")
            val split = splitMessage(msg)
            sendTeamSpeakMessage(split.first)
            val escapedLength = split.second.substring(
                0,
                if (split.second.length > tsCharLimit)
                    tsCharLimit + 1
                else
                    split.second.lastIndex + 1
            ).length
            if (escapedLength > tsCharLimit) {
                msg = split.second
            } else {
                if (split.second.isNotEmpty()) {
                    sendTeamSpeakMessage(split.second.replace("^\n".toRegex(), ":\n"))
                }
                break
            }
        }
    }
}

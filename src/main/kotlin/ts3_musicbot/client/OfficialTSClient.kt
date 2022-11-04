package ts3_musicbot.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ts3_musicbot.util.BotSettings
import ts3_musicbot.util.CommandRunner
import java.io.File
import java.net.URLEncoder

class OfficialTSClient(
    private val settings: BotSettings
) {
    lateinit var channelFile: File
    lateinit var serverName: String
    private val commandRunner = CommandRunner()

    private fun encode(message: String): String {
        val distro =
            commandRunner.runCommand("cat /etc/issue", printOutput = false).first.outputText
        return when {
            distro.contains("(Ubuntu|Debian)".toRegex()) -> {
                message.replace(" ", "\\\\\\s")
                    .replace("\n", "\\\\\\n")
                    .replace("/", "\\/")
                    .replace("|", "\\\\p")
                    .replace("'", "\\\\'")
                    .replace("\"", "\\\"")
                    .replace("&quot;", "\\\"")
                    .replace("`", "\\`")
                    .replace("$", "\\\\$")
            }

            else -> {
                message.replace(" ", "\\s")
                    .replace("\n", "\\n")
                    .replace("/", "\\/")
                    .replace("|", "\\p")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("&quot;", "\\\"")
                    .replace("`", "\\`")
                    .replace("$", "\\$")
            }
        }
    }

    private fun decode(message: String) = message.replace("\\s", " ")
        .replace("\\n", "\n")
        .replace("\\/", "/")
        .replace("\\p", "|")
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\`", "`")
        .replace("\\$", "$")

    /**
     * Perform a TeamSpeak ClientQuery
     * @param queryMsg text for query.
     * @return returns proper data only when connected to a server
     */
    private fun clientQuery(queryMsg: String) = CommandRunner().runCommand(
        "(echo \"auth apikey=${settings.apiKey}\"; " +
                "echo \"$queryMsg\"; echo quit) | nc localhost 25639",
        printOutput = false,
        printCommand = false
    ).first.outputText

    /**
     * get current user's client id
     * @return returns current user's clid
     */
    private fun getCurrentUserId(): String {
        val query = clientQuery("whoami").lines()
        return if (query.any { it.startsWith("clid=") })
            query.first { it.startsWith("clid=") }.substringAfter("clid=").substringBefore(" ")
        else ""
    }

    /**
     * Get current channel list.
     * @return returns a list of Strings, each containing a channel's data
     * A channel's data will look something like this:
     * cid=90 pid=6 channel_order=85 channel_name=TestChannel channel_flag_are_subscribed=1 total_clients=0
     */
    fun getChannelList(): List<String> {
        val channels = clientQuery("channellist").lines()
        return if (channels.any { it.startsWith("cid=") })
            channels.first { it.startsWith("cid=") }.split("|")
        else emptyList()
    }

    /**
     * Get a list of clients on the current server.
     * @return returns a list of Strings, each containing a client's info
     * A client's data will look something like this
     * clid=83 cid=8 client_database_id=100 client_nickname=TeamSpeakUser client_type=0
     */
    fun getClientList(): List<String> {
        val clients = clientQuery("clientlist").lines()
        return if (clients.any { it.startsWith("clid=") })
            clients.first { it.startsWith("clid=") }.split("|")
        else emptyList()
    }

    /**
     * get a channel's id
     * @param channelName path to channel
     * @return returns the channel's id
     */
    private fun getChannelId(channelName: String): String {
        val targetChannel = channelName.substringAfterLast("/")
        val channelList = getChannelList()
        val channels = if (channelList.any { it.contains("channel_name=$targetChannel".toRegex()) })
            channelList.filter { it.contains("channel_name=$targetChannel") }
        else emptyList()

        var targetCid = "0"
        channelLoop@ for (channelData in channels) {
            fun getChannelId(): String = channelData.substringAfter("cid=").substringBefore(" ")

            val subChannels = channelName.split("/")
            val channelPath = ArrayList<String>()
            channelPath.addAll(subChannels.reversed())
            channelPath.removeFirst()

            var pid = channelData.substringAfter("pid=").substringBefore(" ")
            while (channelPath.isNotEmpty()) {
                fun getChannelName(cid: String) =
                    channelList.first { it.contains("cid=$cid".toRegex()) }
                        .substringAfter("channel_name=").substringBefore(" ")

                fun checkPid(pid: String, name: String) = getChannelName(pid) == name
                if (checkPid(pid, channelPath.first())) {
                    pid = getChannelList().first { it.contains("cid=$pid") }.substringAfter("pid=").substringBefore(" ")
                    channelPath.removeFirst()
                } else {
                    continue@channelLoop
                }
            }
            targetCid = getChannelId()
        }
        return targetCid
    }

    /**
     * join a specific channel
     * @param channelName the name of the channel to join
     * @param channelPassword the password of the channel to join
     */
    fun joinChannel(channelName: String = settings.channelName, channelPassword: String = settings.channelPassword) {
        if (channelName != getCurrentChannelName()) {
            if (channelPassword.isNotEmpty()) {
                clientQuery("disconnect")
                clientQuery(
                    "connect address=${settings.serverAddress} password=${encode(settings.serverPassword)} " +
                            "nickname=${encode(settings.nickname)} " +
                            "channel=${encode(channelName)} channel_pw=${encode(channelPassword)}"
                )
            } else {
                val channelId = getChannelId(channelName)
                clientQuery("clientmove cid=$channelId clid=${getCurrentUserId()}")
            }
            updateChannelFile()
        }
    }

    /**
     * get then name of the current channel
     * @return returns the name of the current channel
     */
    private fun getCurrentChannelName(): String =
        getChannelId(decode(clientQuery("channelconnectinfo").lines().first { it.startsWith("path=") }
            .substringAfter("path=")))

    /**
     * @return returns true if starting teamspeak is successful, false if requirements aren't met
     */
    suspend fun startTeamSpeak(): Boolean {
        if (settings.apiKey.isNotEmpty() && settings.serverAddress.isNotEmpty()) {
            /**
             * get the current TeamSpeak server's name
             * @return returns current server name when connected and empty string if not
             */
            fun getVirtualServerName(): String {
                val query = clientQuery("servervariable virtualserver_name").lines()
                val name = if (query.any { it.startsWith("virtualserver_name=") }) {
                    decode(query.first { it.startsWith("virtualserver_name=") }.substringAfter("virtualserver_name="))
                } else ""
                return name
            }


            //start teamspeak
            commandRunner.runCommand(
                ignoreOutput = true,
                command = "teamspeak3 -nosingleinstance" +
                        (if (settings.serverAddress.isNotEmpty()) " \"ts3server://${settings.serverAddress}" else "") +
                        "?port=${settings.serverPort}" +
                        "&nickname=${
                            withContext(Dispatchers.IO) {
                                URLEncoder.encode(settings.nickname, Charsets.UTF_8.toString())
                            }.replace("+", "%20")
                        }" +
                        (if ((settings.serverPassword.isNotEmpty()))
                            "&password=${
                                withContext(Dispatchers.IO) {
                                    URLEncoder.encode(
                                        settings.serverPassword,
                                        Charsets.UTF_8.toString().replace("+", "%20")
                                    )
                                }
                            }"
                        else "") + "\" &"
            )
            delay(1000)
            //wait for teamspeak to start
            while (!commandRunner.runCommand("ps aux | grep ts3client | grep -v grep", printOutput = false)
                    .first.outputText.contains("ts3client_linux".toRegex())
            ) {
                println("Waiting for TeamSpeak process to start...")
            }
            while (getVirtualServerName().isEmpty()) {
                println("Waiting for TeamSpeak to connect to server...")
                delay(1000)
            }
            println("Getting server name...")
            serverName = getVirtualServerName()
            println("Server name: $serverName")
            delay(1000)
            return true
        } else {
            return false
        }
    }

    /**
     * Updates the channelFile variable. It is used to read the chat from the current channel
     * This should be called always when switching channels or making a new connection
     * @return returns the channel file
     */
    private fun updateChannelFile(){
        var file = File("")
        if (settings.channelFilePath.isEmpty()) {
            //get a path to the channel.txt file
            println("\nGetting path to channel.txt file...")
            val chatDir = File("${System.getProperty("user.home")}/.ts3client/chats")
            println("Looking in \"$chatDir\" for chat files.")
            if (chatDir.exists()) {
                for (dir in chatDir.list() ?: return) {
                    println("Checking in $dir")
                    val serverFile = File("${System.getProperty("user.home")}/.ts3client/chats/$dir/server.html")
                    for (line in serverFile.readLines()) {
                        if (line.contains("TextMessage_Connected") && line.contains("channelid://0")) {
                            //compare serverName to the one in server.html
                            val htmlServerName = line.replace(
                                "&apos;",
                                "'"
                            ).split("channelid://0\">&quot;".toRegex())[1].split("&quot;".toRegex())[0].replace(
                                "\\s",
                                " "
                            )
                            if (htmlServerName == serverName) {
                                file =
                                    File("${System.getProperty("user.home")}/.ts3client/chats/$dir/channel.txt")
                                println("Using channel file at \"$file\"\n\n")
                                break
                            }
                        }
                    }
                }
            }
        } else {
            file = File(settings.channelFilePath)
        }

        channelFile = file
    }

    fun sendMsgToChannel(message: String) {
        //TeamSpeak's character limit is 8192 per message
        val tsCharLimit = 8192

        /**
         * Send a message to the current TeamSpeak channel's chat via the official client.
         * @param message The message that should be sent. Max length is 8192 chars and messages need to be encoded!
         */
        fun sendTeamSpeakMessage(message: String) {
            clientQuery("sendtextmessage targetmode=2 msg=$message")
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
                            else it.reversed()[9]
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
                encode(msg.substring(0, charLimit)),
                if (escapedLength > tsCharLimit)
                    msg.substring(charLimit, msg.lastIndex + 1)
                else
                    ""
            )
        }

        var msg = message
        while (true) {
            //add an empty line to the start of the message if there isn't one already.
            if (msg.lines().size > 1 && !msg.startsWith("\n"))
                msg = "\n$msg"
            val split = splitMessage(msg)
            sendTeamSpeakMessage(split.first)
            val escapedLength = encode(
                split.second.substring(
                    0,
                    if (split.second.length > tsCharLimit)
                        tsCharLimit + 1
                    else
                        split.second.lastIndex + 1
                )
            ).length
            if (escapedLength > tsCharLimit) {
                msg = split.second
            } else {
                if (split.second.isNotEmpty()) {
                    sendTeamSpeakMessage(encode(split.second))
                }
                break
            }
        }
    }
}

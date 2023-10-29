package ts3_musicbot.client

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ts3_musicbot.util.BotSettings
import ts3_musicbot.util.CommandRunner
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import kotlin.properties.Delegates
import kotlin.system.exitProcess

class OfficialTSClient(botSettings: BotSettings): Client(botSettings) {
    val tsClientDirPath = "${System.getProperty("user.home")}/.ts3client"
    lateinit var channelFile: File
    private lateinit var serverName: String

    /**
     * Perform a TeamSpeak ClientQuery
     * @param queryMsg text for query.
     * @return returns proper data only when connected to a server
     */
    private fun clientQuery(queryMsg: String) = CommandRunner().runCommand(
        "(echo \"auth apikey=${botSettings.apiKey}\"; " +
                "echo \"$queryMsg\"; echo quit) | nc localhost 25639",
        printOutput = false,
        printErrors = false,
        printCommand = false
    ).outputText

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
    override fun getChannelList(): List<String> {
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
    override fun getClientList(): List<String> {
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
    private fun getChannelId(channelName: String): Int {
        val targetChannel = channelName.substringAfterLast("/")
        val channelList = getChannelList()
        val channels = if (channelList.any { it.contains("channel_name=${encode(targetChannel)}") })
            channelList.filter { it.contains("channel_name=${encode(targetChannel)}") }
        else emptyList()

        var targetCid = 0
        channelLoop@ for (channelData in channels) {
            fun getChannelId(): Int = channelData.substringAfter("cid=").substringBefore(' ').toInt()

            val subChannels = channelName.split('/')
            val channelPath = ArrayList<String>()
            channelPath.addAll(subChannels.reversed())
            channelPath.removeFirst()

            var pid = channelData.substringAfter("pid=").substringBefore(" ")
            while (channelPath.isNotEmpty()) {
                fun getChannelName(cid: String) =
                    channelList.first { it.contains("cid=$cid(\\s+|$)".toRegex()) }
                        .substringAfter("channel_name=").substringBefore(' ')

                fun checkPid(pid: String, name: String) = getChannelName(pid) == encode(name)
                if (checkPid(pid, channelPath.first())) {
                    pid = getChannelList().first { it.contains("cid=$pid(\\s+|$)".toRegex()) }.substringAfter("pid=").substringBefore(' ')
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
     * @return returns true if successful
     */
    override suspend fun joinChannel(channelName: String, channelPassword: String): Boolean {
        println("Attempting to join a channel at \"$channelName\"" + if (channelPassword.isNotEmpty()) " with the password \"$channelPassword\"" else "")

        val currentChannelName = getCurrentChannelName()
        return if (currentChannelName == "not connected") {
            println("Connection failed! Trying again...")
            restartClient()
            false
        } else {
            if (channelName != currentChannelName) {
                if (channelPassword.isNotEmpty()) {
                    clientQuery("disconnect")
                    delay(500)
                    clientQuery(
                        "connect address=${botSettings.serverAddress} password=${encode(botSettings.serverPassword)} " +
                                "nickname=${encode(botSettings.nickname)} " +
                                "channel=${encode(channelName)} channel_pw=${encode(channelPassword)}"
                    )
                } else {
                    val channelId = getChannelId(channelName)
                    clientQuery("clientmove cid=$channelId clid=${getCurrentUserId()}")
                }
                updateChannelFile()
                channelName == getCurrentChannelName()
            } else true
        }
    }

    /**
     * get then name of the current channel
     * @return returns the name of the current channel
     */
    private fun getCurrentChannelName(): String {
        val connectInfo = clientQuery("channelconnectinfo").lines()
        return when {
            connectInfo.any { it.startsWith("path=") } -> decode(
                    connectInfo.first { it.startsWith("path=") }
                        .substringAfter("path=").substringBefore(' ')
                    )
            connectInfo.any { it.contains("msg=not\\sconnected") } -> decode(
                connectInfo.first { it.contains("msg=not\\sconnected") }
                    .substringAfter("msg=")
            )
            else -> ""
        }
    }

    /**
     * Start the TeamSpeak Client and connect to server
     * @return returns true if starting teamspeak is successful, false if requirements aren't met
     */
    suspend fun startTeamSpeak(): Boolean {
        botSettings.apiKey = botSettings.apiKey.ifEmpty { getApiKey() }

        if (botSettings.serverAddress.isNotEmpty()) {
            /**
             * get the current TeamSpeak server's name
             * @return returns current server name when connected and empty string if not
             */
            fun getVirtualServerName(): String {
                val query = clientQuery("servervariable virtualserver_name").lines()
                return if (query.any { it.startsWith("virtualserver_name=") }) {
                    decode(query.first { it.startsWith("virtualserver_name=") }.substringAfter("virtualserver_name="))
                } else ""
            }


            //start teamspeak
            commandRunner.runCommand(
                ignoreOutput = true,
                command = "xvfb-run -a teamspeak3 -nosingleinstance" +
                        " \"" +
                        (if (botSettings.serverAddress.isNotEmpty()) "ts3server://${botSettings.serverAddress}" else "") +
                        "?port=${botSettings.serverPort}" +
                        "&nickname=${
                            withContext(IO) {
                                URLEncoder.encode(botSettings.nickname, Charsets.UTF_8.toString())
                            }.replace("+", "%20")
                        }" +
                        (if ((botSettings.serverPassword.isNotEmpty()))
                            "&password=${
                                withContext(IO) {
                                    URLEncoder.encode(
                                        botSettings.serverPassword,
                                        Charsets.UTF_8.toString().replace("+", "%20")
                                    )
                                }
                            }"
                        else "") +
                        "\" &",
                inheritIO = false
            )
            delay(1000)
            //wait for teamspeak to start
            println()
            var dotsAmount = 0
            while (!commandRunner.runCommand("ps aux | grep ts3client | grep -v grep", printOutput = false)
                    .outputText.contains("ts3client_linux".toRegex())
            ) {
                print("\rWaiting for TeamSpeak's process to start" + ".".repeat(dotsAmount) + "    ")
                delay(1000)
                if (dotsAmount < 3)
                    dotsAmount++
                else
                    dotsAmount = 0
            }
            println()
            dotsAmount = 0
            while (getVirtualServerName().isEmpty()) {
                print("\rWaiting for TeamSpeak to connect to server" + ".".repeat(dotsAmount) + "    ")
                delay(1000)
                if (dotsAmount < 3)
                    dotsAmount++
                else
                    dotsAmount = 0
            }
            println("\nGetting server name...")
            serverName = getVirtualServerName()
            println("Server name: $serverName")
            audioSetup()
            delay(1000)
            return true
        } else {
            return false
        }
    }

    private suspend fun getApiKey(): String {
        println("Getting ClientQuery api key from $tsClientDirPath/clientquery.ini")
        val clientQueryIni = File("$tsClientDirPath/clientquery.ini")
        suspend fun generateFile() {
            println("Trying to generate file.")
            killTeamSpeak()
            delay(500)
            val logFile = File("/tmp/tsOutput.log")
            if (!logFile.exists()) {
                logFile.createNewFile()
            } else {
                logFile.delete()
                logFile.createNewFile()
            }
            commandRunner.runCommand(
                "xvfb-run -a teamspeak3 1> /tmp/tsOutput.log &",
                printCommand = true,
                inheritIO = true
            )
            println()
            var dotsAmount = 0
            while (!logFile.readLines().any { it.contains("Addon installed: ClientQuery") }) {
                print("\rWaiting for TeamSpeak to install ClientQuery addon" + ".".repeat(dotsAmount) + "    ")
                delay(1000)
                if (dotsAmount < 3)
                    dotsAmount++
                else
                    dotsAmount = 0
            }
            println("\nDone.")
            logFile.delete()
            delay(2000)
            killTeamSpeak()
        }

        while (!clientQueryIni.exists()) {
            println("File doesn't exist!")
            generateFile()
        }
        return clientQueryIni.readLines().first { it.startsWith("api_key=") }.substringAfter("=")
    }

    suspend fun exportTeamSpeakSettings() {
        val tsClientDir = File(tsClientDirPath)
        while (!tsClientDir.isDirectory)
            tsClientDir.mkdir()

        suspend fun exportFile(filePath: String) {
            println("Exporting $filePath to $tsClientDirPath")
            val data = javaClass.classLoader.getResource(filePath)
            if (data != null) {
                val outputFile = File("$tsClientDirPath/$filePath")
                withContext(IO) {
                    val outputStream = FileOutputStream(outputFile)
                    val inputData = data.readBytes()
                    outputStream.write(inputData)
                    outputStream.close()
                }
            }
        }

        File("$tsClientDirPath/cache").mkdir()
        File("$tsClientDirPath/plugins").mkdir()
        File("$tsClientDirPath/plugins/clientquery_plugin").mkdir()


        exportFile("cache/license_5_en.html")
        println(
            "By continuing to use the Official TeamSpeak Client,\n" +
                    "you must accept the license located at $tsClientDirPath/cache/license_5_en.html\n"
        )
        var licenseAccepted by Delegates.notNull<Boolean>()
        while (true) {
            when (System.console().readLine("Do you accept the license? [y/n]: ").lowercase()) {
                "y", "yes" -> {
                    licenseAccepted = true
                    break
                }

                "n", "no" -> {
                    licenseAccepted = false
                    break
                }
            }
        }
        if (!licenseAccepted) {
            println("License not Accepted! Exiting.")
            exitProcess(1)
        }

        exportFile("addons.ini")
        exportFile("settings.db")

        exportFile("plugins/clientquery_plugin/auth.txt")
        exportFile("plugins/clientquery_plugin/banadd.txt")
        exportFile("plugins/clientquery_plugin/banclient.txt")
        exportFile("plugins/clientquery_plugin/bandel.txt")
        exportFile("plugins/clientquery_plugin/bandelall.txt")
        exportFile("plugins/clientquery_plugin/banlist.txt")
        exportFile("plugins/clientquery_plugin/channeladdperm.txt")
        exportFile("plugins/clientquery_plugin/channelclientaddperm.txt")
        exportFile("plugins/clientquery_plugin/channelclientdelperm.txt")
        exportFile("plugins/clientquery_plugin/channelclientlist.txt")
        exportFile("plugins/clientquery_plugin/channelclientpermlist.txt")
        exportFile("plugins/clientquery_plugin/channelconnectinfo.txt")
        exportFile("plugins/clientquery_plugin/channelcreate.txt")
        exportFile("plugins/clientquery_plugin/channeldelete.txt")
        exportFile("plugins/clientquery_plugin/channeldelperm.txt")
        exportFile("plugins/clientquery_plugin/channeledit.txt")
        exportFile("plugins/clientquery_plugin/channelgroupadd.txt")
        exportFile("plugins/clientquery_plugin/channelgroupaddperm.txt")
        exportFile("plugins/clientquery_plugin/channelgroupclientlist.txt")
        exportFile("plugins/clientquery_plugin/channelgroupdel.txt")
        exportFile("plugins/clientquery_plugin/channelgroupdelperm.txt")
        exportFile("plugins/clientquery_plugin/channelgrouplist.txt")
        exportFile("plugins/clientquery_plugin/channelgrouppermlist.txt")
        exportFile("plugins/clientquery_plugin/channellist.txt")
        exportFile("plugins/clientquery_plugin/channelmove.txt")
        exportFile("plugins/clientquery_plugin/channelpermlist.txt")
        exportFile("plugins/clientquery_plugin/channelvariable.txt")
        exportFile("plugins/clientquery_plugin/clientaddperm.txt")
        exportFile("plugins/clientquery_plugin/clientdbdelete.txt")
        exportFile("plugins/clientquery_plugin/clientdbedit.txt")
        exportFile("plugins/clientquery_plugin/clientdblist.txt")
        exportFile("plugins/clientquery_plugin/clientdelperm.txt")
        exportFile("plugins/clientquery_plugin/clientgetdbidfromuid.txt")
        exportFile("plugins/clientquery_plugin/clientgetids.txt")
        exportFile("plugins/clientquery_plugin/clientgetnamefromdbid.txt")
        exportFile("plugins/clientquery_plugin/clientgetnamefromuid.txt")
        exportFile("plugins/clientquery_plugin/clientgetuidfromclid.txt")
        exportFile("plugins/clientquery_plugin/clientkick.txt")
        exportFile("plugins/clientquery_plugin/clientlist.txt")
        exportFile("plugins/clientquery_plugin/clientmove.txt")
        exportFile("plugins/clientquery_plugin/clientmute.txt")
        exportFile("plugins/clientquery_plugin/clientnotifyregister.txt")
        exportFile("plugins/clientquery_plugin/clientnotifyunregister.txt")
        exportFile("plugins/clientquery_plugin/clientpermlist.txt")
        exportFile("plugins/clientquery_plugin/clientpoke.txt")
        exportFile("plugins/clientquery_plugin/clientunmute.txt")
        exportFile("plugins/clientquery_plugin/clientupdate.txt")
        exportFile("plugins/clientquery_plugin/clientvariable.txt")
        exportFile("plugins/clientquery_plugin/complainadd.txt")
        exportFile("plugins/clientquery_plugin/complaindel.txt")
        exportFile("plugins/clientquery_plugin/complaindelall.txt")
        exportFile("plugins/clientquery_plugin/complainlist.txt")
        exportFile("plugins/clientquery_plugin/connect.txt")
        exportFile("plugins/clientquery_plugin/currentschandlerid.txt")
        exportFile("plugins/clientquery_plugin/disconnect.txt")
        exportFile("plugins/clientquery_plugin/ftcreatedir.txt")
        exportFile("plugins/clientquery_plugin/ftdeletefile.txt")
        exportFile("plugins/clientquery_plugin/ftgetfileinfo.txt")
        exportFile("plugins/clientquery_plugin/ftgetfilelist.txt")
        exportFile("plugins/clientquery_plugin/ftinitdownload.txt")
        exportFile("plugins/clientquery_plugin/ftinitupload.txt")
        exportFile("plugins/clientquery_plugin/ftlist.txt")
        exportFile("plugins/clientquery_plugin/ftrenamefile.txt")
        exportFile("plugins/clientquery_plugin/ftstop.txt")
        exportFile("plugins/clientquery_plugin/hashpassword.txt")
        exportFile("plugins/clientquery_plugin/help.txt")
        exportFile("plugins/clientquery_plugin/messageadd.txt")
        exportFile("plugins/clientquery_plugin/messagedel.txt")
        exportFile("plugins/clientquery_plugin/messageget.txt")
        exportFile("plugins/clientquery_plugin/messagelist.txt")
        exportFile("plugins/clientquery_plugin/messageupdateflag.txt")
        exportFile("plugins/clientquery_plugin/permoverview.txt")
        exportFile("plugins/clientquery_plugin/quit.txt")
        exportFile("plugins/clientquery_plugin/README.txt")
        exportFile("plugins/clientquery_plugin/sendtextmessage.txt")
        exportFile("plugins/clientquery_plugin/serverconnectinfo.txt")
        exportFile("plugins/clientquery_plugin/serverconnectionhandlerlist.txt")
        exportFile("plugins/clientquery_plugin/servergroupadd.txt")
        exportFile("plugins/clientquery_plugin/servergroupaddclient.txt")
        exportFile("plugins/clientquery_plugin/servergroupaddperm.txt")
        exportFile("plugins/clientquery_plugin/servergroupclientlist.txt")
        exportFile("plugins/clientquery_plugin/servergroupdel.txt")
        exportFile("plugins/clientquery_plugin/servergroupdelclient.txt")
        exportFile("plugins/clientquery_plugin/servergroupdelperm.txt")
        exportFile("plugins/clientquery_plugin/servergrouplist.txt")
        exportFile("plugins/clientquery_plugin/servergrouppermlist.txt")
        exportFile("plugins/clientquery_plugin/servergroupsbyclientid.txt")
        exportFile("plugins/clientquery_plugin/servervariable.txt")
        exportFile("plugins/clientquery_plugin/setclientchannelgroup.txt")
        exportFile("plugins/clientquery_plugin/tokenadd.txt")
        exportFile("plugins/clientquery_plugin/tokendelete.txt")
        exportFile("plugins/clientquery_plugin/tokenlist.txt")
        exportFile("plugins/clientquery_plugin/tokenuse.txt")
        exportFile("plugins/clientquery_plugin/use.txt")
        exportFile("plugins/clientquery_plugin/verifychannelpassword.txt")
        exportFile("plugins/clientquery_plugin/verifyserverpassword.txt")
        exportFile("plugins/clientquery_plugin/whoami.txt")
        exportFile("plugins/libclientquery_plugin_linux_amd64.so")
    }

    /**
     * First disconnects from the server and then closes the client
     */
    suspend fun stopTeamSpeak() {
        clientQuery("disconnect")
        delay(500)
        killTeamSpeak()
    }

    /**
     * Kills the ts3client_linux process
     */
    private suspend fun killTeamSpeak() {
        repeat(4) {
            commandRunner.runCommand(
                "pkill -9 ts3client_linux",
                ignoreOutput = true,
                printCommand = false,
                printOutput = false
            )
            delay(200)
        }
    }

    /**
     * restart teamspeak client and reconnect to current server
     * @return returns true if starting teamspeak is successful
     */
    suspend fun restartClient(): Boolean {
        stopTeamSpeak()
        return if (startTeamSpeak()) {
            joinChannel()
            true
        } else false
    }

    /**
     * Updates the channelFile variable. It is used to read the chat from the current channel
     * This should be called always when switching channels or making a new connection
     */
    private fun updateChannelFile() {
        var file = File("")
        if (botSettings.channelFilePath.isEmpty()) {
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
            file = File(botSettings.channelFilePath)
        }

        channelFile = file
    }

    /**
     * Sends a message to the current Channel
     * @param message The message to send
     */
    override fun sendMsgToChannel(message: String) {
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

    /**
     * Check if TeamSpeak is listed in pulseaudio's sink inputs.
     * If not, this means that you won't be able to hear any audio.
     * @return Returns true if TeamSpeak can be found in pulseaudio's sink inputs,
     *         which means audio is probably working.
     */
    fun audioIsWorking(): Boolean {
        return runPactlCommand("list sink-inputs").any {
            it as JSONObject
            it.getJSONObject("properties").getString("application.name").contains("TeamSpeak3?".toRegex())
        }
    }

    /**
     * Runs the given pactl command and returns the output as a JSONArray.
     * @param command The pactl command to run.
     * @return Returns the command's output as a JSONArray.
     */
    private fun runPactlCommand(command: String): JSONArray {
        /**
         * Converts a pactl output string to a JSONArray.
         * This is required because pactl has a built-in json formatter only from version 16.0 onwards.
         * @param stringToConvert The string you want to convert
         * @return Returns a JSONArray which was parsed from stringToConvert
         */
        fun convertToJSON(stringToConvert: String): JSONArray {
            /**
             * Parse a line
             * @param line The line to parse
             * @return Returns a Pair containing the amount of indentation and the cleaned up line
             */
            fun indentations(line: String): Pair<Int, String> {
                var newLine = line
                var indentation = 0
                while (newLine.startsWith("\t")) {
                    newLine = newLine.substringAfter("\t")
                    indentation += 1
                }
                return Pair(indentation, newLine)
            }

            val pactlOutput = stringToConvert.replace("\\\"", "")
            val pactlMap = emptyMap<String, MutableMap<String, Any>>().toMutableMap()
            var index = ""
            var objectName = ""
            for (rawLine in pactlOutput.lines()) {
                if (rawLine == "") continue
                val (indentation, line) = indentations(rawLine)
                when (indentation) {
                    0 -> { //get the object's index
                        index = line.substringAfter("#")
                        pactlMap[index] = mapOf(Pair("index", index)).toMutableMap()
                    }

                    1 -> { //get object's data
                        if (line.startsWith("        ")) {
                            val trimmed = line.trim()
                            pactlMap[index]?.set(
                                trimmed.substringBefore(" "),
                                trimmed.substringAfter(" ")
                            )
                        } else {
                            objectName = line.substringBefore(":").trim().lowercase()
                            val value = line.substringAfter(":").trim()
                            pactlMap[index]?.set(objectName, value)
                        }
                    }

                    2 -> { //get sink object's properties
                        if (pactlMap[index]?.get(objectName) == "")
                            pactlMap[index]?.set(objectName, emptyMap<String, String>().toMutableMap())
                        var (propName, value) = line.split("=").let { Pair(it.first().trim(), it.last().trim()) }
                        if (value.first() == '"' && value.last() == '"')
                            value = value.replace("(^\"|\"$)".toRegex(), "")
                        val props = pactlMap[index]?.get(objectName).let {
                            it as Map<*, *>
                            it
                        }.toMutableMap()
                        props[propName] = value
                        pactlMap[index]?.set(objectName, props)
                    }
                }
            }
            val pactlJSON = JSONObject(pactlMap)
            return pactlJSON.toJSONArray(pactlJSON.names())
        }

        val pactlVersion = commandRunner.runCommand("pactl --version", printOutput = false, printCommand = false)
                .outputText.lines().first().replace("^pactl\\s+".toRegex(), "")
                .substringBefore(".").toInt() 
        return if (pactlVersion < 16) {
            val output = commandRunner.runCommand(
                "pactl $command",
                printOutput = false, printCommand = false
            ).outputText
            if (output.isNotEmpty())
                convertToJSON(output)
            else
                JSONArray("[]")
        } else {
            JSONArray(
                commandRunner.runCommand(
                    "pactl -f json $command",
                    printOutput = false, printCommand = false
                ).outputText
            )
        }
    }

    private suspend fun audioSetup() {
        println("Setting up audio.")
        //check if pulseaudio exists, then start it if not already running
        commandRunner.runCommand(
            "command -v pulseaudio && (pulseaudio --check || pulseaudio --start && sleep 2)",
            printOutput = false
        )

        if (audioIsWorking()) {
            //Mute teamspeak output
            val sinkInputs = runPactlCommand("list sink-inputs")
            val tsSinkInputId = sinkInputs.first {
                it as JSONObject
                it.getJSONObject("properties").getString("application.name").contains("TeamSpeak3?".toRegex())
            }.let {
                it as JSONObject
                it.getInt("index")
            }
            commandRunner.runCommand(
                "pactl set-sink-input-mute $tsSinkInputId 1", printCommand = false, printOutput = false
            )

            //set teamspeak to monitor output from default sink
            val tsSourceOutputs = runPactlCommand("list source-outputs")
            val tsSourceOutputId = tsSourceOutputs.first {
                it as JSONObject
                it.getJSONObject("properties").getString("application.name").contains("TeamSpeak3?".toRegex())
            }.let {
                it as JSONObject
                it.getInt("index")
            }

            val defaultSinkName = commandRunner.runCommand(
                "pacmd list-sinks | grep -e 'index:' -e 'name:' | grep -A 1 -E '\\s+\\*\\s+index:' | grep 'name'",
                printCommand = false, printOutput = false
            ).outputText.replace("(^\\s*name:\\s*<|>.*$)".toRegex(), "")
            commandRunner.runCommand(
                "pactl move-source-output $tsSourceOutputId $defaultSinkName.monitor",
                printCommand = false, printOutput = false
            )
            println("Audio setup done.")
        } else {
            println("TeamSpeak's audio is broken, restarting client.")
            restartClient()
        }
    }
}

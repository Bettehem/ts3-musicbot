package ts3_musicbot

import javafx.application.Application
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import javafx.stage.Stage
import ts3_musicbot.chat.ChatReader
import ts3_musicbot.chat.ChatUpdate
import ts3_musicbot.chat.ChatUpdateListener
import ts3_musicbot.chat.CommandListener
import ts3_musicbot.util.BotSettings
import ts3_musicbot.util.CommandRunner
import ts3_musicbot.util.CommandList.commandList
import ts3_musicbot.util.Console
import ts3_musicbot.util.ConsoleUpdateListener
import java.io.File
import java.io.PrintWriter
import java.lang.Exception
import kotlin.system.exitProcess

var inputFilePath = ""
private lateinit var window: Stage
private var statusTextView = TextArea()
private val commandRunner = CommandRunner()
private var spotifyPlayer = "spotify"

class Main : Application(), EventHandler<ActionEvent>, ChatUpdateListener, CommandListener {
    private lateinit var scene: Scene

    private var layout: VBox = VBox()
    private var scrollPane: ScrollPane = ScrollPane()
    private var statusScrollPane: ScrollPane = ScrollPane()
    private var enterApiKeyTextView = Label()
    private var enterServerAddressTextView = Label()
    private var enterChannelNameTextView = Label()
    private var enterNicknameTextView = Label()
    private var enterServerPortTextView = Label()
    private var enterServerPasswordTextView = Label()
    private var enterChannelFilePathTextView = Label()
    private var enterMarketTextView = Label()

    private var apiKeyEditText = TextField()
    private var serverAddressEditText = TextField()
    private var channelNameEditText = TextField()
    private var nicknameEditText = TextField()
    private var serverPortEditText = TextField()
    private var serverPasswordEditText = PasswordField()
    private var serverPasswordVisibleEditText = TextField()
    private var channelFilePathEditText = TextField()
    private var marketEditText = TextField()

    private var advancedSettingsRadioGroup = ToggleGroup()
    private var hideAdvancedSettingsRadioButton = RadioButton()
    private var showAdvancedSettingsRadioButton = RadioButton()

    private var showPasswordCheckBox = CheckBox()
    private var browseChannelFileButton = Button()
    private var selectSpotifyPlayerTextView = Label()
    private var spotifyPlayerRadioGroup = ToggleGroup()
    private var ncspotRadioButton = RadioButton()
    private var spotifyRadioButton = RadioButton()
    private var saveSettingsButton = Button()
    private var loadSettingsButton = Button()
    private var startBotButton = Button()
    private var stopBotButton = Button()

    private lateinit var chatReader: ChatReader

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {

            //check if command line arguments are provided
            if (args.isNotEmpty()) {
                var apiKey = ""
                var serverAddress = ""
                var serverPort = ""
                var nickname = ""
                var serverPassword = ""
                var channelName = ""
                var channelFilename = ""
                var market = ""
                var configFile = ""

                val helpMessage = "\n" +
                        "TS3 Music Bot help message\n" +
                        "Available options:\n" +
                        "-h, --help             Show this help message\n" +
                        "-a, --apikey           ClientQuery api key\n" +
                        "-s, --serveraddress    Server address to connect to\n" +
                        "-p, --serverport       Server's port. Usually 9987\n" +
                        "-P, --serverpassword   Server's password\n" +
                        "-c, --channelname      The channel's name the bot should connect to after connecting to the server.\n" +
                        "-C, --channelfile      Provide a path to a channel.html or channel.txt file. You also need to provide the channel name with -c option\n" +
                        "-n, --nickname         The nickname of the bot\n" +
                        "-m, --market           Specify a market/country for Spotify.\n" +
                        "--ncspot               Use ncspot as the Spotify player (Requires Spotify Premium).\n" +
                        "                       Before starting the bot, you also need to export your terminal app of preference as \$TERMINAL.\n" +
                        "                       Example: export TERMINAL=xfce4-terminal\n" +
                        "--config               provide a config file where to read arguments from\n"

                //go through given arguments and save them
                for (argPos in args.indices) {
                    when (args[argPos]) {
                        "-h", "--help" -> {
                            print(helpMessage)
                            exitProcess(0)
                        }
                        "-a", "--apikey" -> {
                            //"if (args.size >= argPos +1)" is a safe check in case a user provides an a flag, but no argument
                            if (args.size >= argPos + 1)
                                apiKey = args[argPos + 1]
                        }
                        "-s", "--serveraddress" -> {
                            if (args.size >= argPos + 1)
                                serverAddress = args[argPos + 1]
                        }
                        "-p", "--serverport" -> {
                            if (args.size >= argPos + 1)
                                serverPort = args[argPos + 1]
                        }
                        "-P", "--serverpassword" -> {
                            if (args.size >= argPos + 1)
                                serverPassword = args[argPos + 1]
                        }
                        "-c", "--channelname" -> {
                            if (args.size >= argPos + 1)
                                channelName = args[argPos + 1]
                        }
                        "-C", "--channelfile" -> {
                            if (args.size >= argPos + 1)
                                channelFilename = args[argPos + 1]
                        }
                        "-n", "--nickname" -> {
                            if (args.size >= argPos + 1)
                                nickname = args[argPos + 1]
                        }
                        "-m", "--market" -> {
                            if (args.size >= argPos + 1)
                                market = args[argPos + 1]
                        }
                        "--ncspot" -> {
                            if (args.size >= argPos + 1)
                                spotifyPlayer = "ncspot"
                        }
                        "--config" -> {
                            if (args.size >= argPos + 1)
                                configFile = args[argPos + 1]
                        }
                    }
                }

                if (configFile.isNotEmpty()) {
                    val settings: BotSettings = loadSettings(File(configFile))
                    apiKey = settings.apiKey
                    serverAddress = settings.serverAddress
                    serverPort = settings.serverPort
                    nickname = settings.nickname
                    serverPassword = settings.serverPassword
                    channelName = settings.channelName
                    channelFilename = settings.channelFilePath
                    market = settings.market
                    spotifyPlayer = settings.spotifyPlayer
                }

                //connect to desired server and channel, after which find the server's channel file and start listening for commands
                if (apiKey.isNotEmpty() && serverAddress.isNotEmpty() && nickname.isNotEmpty()) {
                    println("Starting TeamSpeak 3...")
                    println(
                        "Connecting to server at: $serverAddress, port ${if (serverPort.isNotEmpty()) {
                            serverPort
                        } else {
                            "9987"
                        }}."
                    )
                    println("Using $nickname as the bot\'s nickname.")

                    commandRunner.runCommand(
                        ignoreOutput = true,
                        command = "teamspeak3 -nosingleinstance \"ts3server://$serverAddress?port=${if (serverPort.isNotEmpty()) {
                            serverPort
                        } else {
                            "9987"
                        }}&nickname=${nickname.replace(" ", "%20")}&${if ((serverPassword.isNotEmpty())) {
                            "password=$serverPassword"
                        } else {
                            ""
                        }}&${if (channelName.isNotEmpty()) {
                            "channel=${channelName.replace(" ", "%20")}"
                        } else {
                            ""
                        }} &\""
                    )
                    Thread.sleep(1000)
                    while (!commandRunner.runCommand("ps aux | grep ts3client | grep -v grep", printOutput = false)
                            .first.outputText.contains("ts3client_linux".toRegex())
                    ) {
                        //do nothing
                    }
                } else {
                    println("Error!\nOptions -a, -s and -n are required. See -h or --help for more information")
                    exitProcess(0)
                }
                Thread.sleep(5000)
                //get the server's name
                val virtualserver_name = commandRunner.runCommand(
                    "(echo auth apikey=$apiKey; echo \"servervariable virtualserver_name\"; echo quit) | nc localhost 25639",
                    printOutput = false
                ).first.outputText.lines()
                var serverName = ""
                for (line in virtualserver_name) {
                    if (line.contains("virtualserver_name") && line.contains("=")) {
                        serverName = line.split("=".toRegex())[1].replace("\\s", " ")
                        println("Server name: $serverName")
                    }
                }

                //get a path to the channel.txt file
                println("\nGetting path to channel.txt file...")
                val chatDir = File("${System.getProperty("user.home")}/.ts3client/chats")
                println("Looking in \"$chatDir\" for chat files.")
                var channelFile = File("")
                if (chatDir.exists()) {
                    for (dir in chatDir.list()!!) {
                        println("Checking in $dir")
                        val serverFile = File("${System.getProperty("user.home")}/.ts3client/chats/$dir/server.html")
                        val lines = serverFile.readLines()
                        for (line in lines) {
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
                                    channelFile = if (channelFilename.isNotEmpty()) {
                                        File(channelFilename)
                                    } else {
                                        File("${System.getProperty("user.home")}/.ts3client/chats/$dir/channel.txt")
                                    }
                                    println("Using channel file at \"$channelFile\"\n\n")
                                    break
                                }
                            }
                        }
                    }
                }

                if (channelFile.exists()) {
                    val chatReader = ChatReader(channelFile, object : ChatUpdateListener {
                        override fun onChatUpdated(update: ChatUpdate) {
                            if (update.message.startsWith("%"))
                                print("\nUser ${update.userName} issued command \"${update.message}\"\nCommand: ")
                        }
                    }, object : CommandListener {
                        override fun onCommandExecuted(command: String, output: String, extra: Any?) {
                            print("\nCommand \"$command\" has been executed.\nOutput:\n$output\n\nCommand: ")
                        }
                    }, apiKey, market, spotifyPlayer, channelName, nickname)
                    chatReader.startReading()
                    println("Bot $nickname started listening to the chat in channel $channelName.")

                    val console = Console(object : ConsoleUpdateListener {
                        override fun onCommandIssued(command: String) {
                            if (command.startsWith("%"))
                                chatReader.parseLine("__console__", command)
                            else {
                                when (command) {
                                    "save-settings" -> {
                                        val settings = BotSettings(
                                            apiKey,
                                            serverAddress,
                                            serverPort,
                                            serverPassword,
                                            channelName,
                                            channelFilename,
                                            nickname,
                                            market
                                        )
                                        saveSettings(settings, showGui = false)
                                    }
                                }
                            }
                        }
                    })
                    console.startConsole()
                }
            } else {
                //launch graphical window
                launch(Main::class.java, *args)
            }

        }

        private fun saveSettings(botSettings: BotSettings = BotSettings(), showGui: Boolean) {
            //save settings to a config file
            val file: File?

            if (showGui) {
                val fileChooser = FileChooser()
                fileChooser.title = "Select where to save the config file."
                fileChooser.initialDirectory = File(System.getProperty("user.dir"))
                fileChooser.initialFileName = "ts3-musicbot.config"
                fileChooser.selectedExtensionFilter =
                    FileChooser.ExtensionFilter("Configuration file", listOf("*.config"))
                file = fileChooser.showSaveDialog(window)
            } else {
                val console = System.console()
                val configPath =
                    console.readLine("Enter path where to save config (Default location is your current directory): ")
                file = if (configPath.isNotEmpty()) {
                    if (File(configPath).isDirectory)
                        File("$configPath/ts3-musicbot.config")
                    else
                        File(configPath)
                } else {
                    File("${System.getProperty("user.dir")}/ts3-musicbot.config")
                }
            }
            if (file != null) {
                val fileWriter = PrintWriter(file)
                fileWriter.println("API_KEY=${botSettings.apiKey}")
                fileWriter.println("SERVER_ADDRESS=${botSettings.serverAddress}")
                fileWriter.println("SERVER_PORT=${botSettings.serverPort}")
                fileWriter.println("SERVER_PASSWORD=${botSettings.serverPassword}")
                fileWriter.println("CHANNEL_NAME=${botSettings.channelName}")
                fileWriter.println("CHANNEL_FILE_PATH=${botSettings.channelFilePath}")
                fileWriter.println("NICKNAME=${botSettings.nickname}")
                fileWriter.println("MARKET=${botSettings.market}")
                fileWriter.println("SPOTIFY_PLAYER=${botSettings.spotifyPlayer}")
                fileWriter.close()
            }
        }

        private fun loadSettings(settingsFile: File): BotSettings {
            val settings = BotSettings()

            if (settingsFile.exists()) {
                for (line in settingsFile.readLines()) {
                    when (line.substringBefore("=")) {
                        "API_KEY" -> settings.apiKey = line.substringAfter("=")
                        "SERVER_ADDRESS" -> settings.serverAddress = line.substringAfter("=")
                        "SERVER_PORT" -> settings.serverPort = line.substringAfter("=")
                        "SERVER_PASSWORD" -> settings.serverPassword = line.substringAfter("=")
                        "CHANNEL_NAME" -> settings.channelName = line.substringAfter("=")
                        "CHANNEL_FILE_PATH" -> settings.channelFilePath = line.substringAfter("=")
                        "NICKNAME" -> settings.nickname = line.substringAfter("=")
                        "MARKET" -> settings.market = line.substringAfter("=")
                        "SPOTIFY_PLAYER" -> settings.spotifyPlayer = line.substringAfter("=")
                    }
                }
            }

            println("Loaded settings from ${settingsFile.absolutePath}")
            return settings
        }

        /**
         * @param settings settings that are used to start teamspeak and connect to desired server
         * @return returns true if starting teamspeak is successful, false if it fails
         */
        private fun startTeamSpeak(settings: BotSettings): Boolean {
            if (settings.apiKey.isNotEmpty() && settings.serverAddress.isNotEmpty() && settings.nickname.isNotEmpty()) {
                //start teamspeak
                commandRunner.runCommand(
                    ignoreOutput = true,
                    command = "teamspeak3 -nosingleinstance \"ts3server://${settings.serverAddress}?port=${if (settings.serverPort.isNotEmpty()) {
                        settings.serverPort
                    } else {
                        "9987"
                    }}&nickname=${settings.nickname.replace(" ", "%20")}&${if ((settings.serverPassword.isNotEmpty())) {
                        "password=${settings.serverPassword}"
                    } else {
                        ""
                    }}&${if (settings.channelName.isNotEmpty()) {
                        "channel=${settings.channelName.replace(" ", "%20")}"
                    } else {
                        ""
                    }} &\""
                )
                Thread.sleep(1000)
                //wait for teamspeak to start
                while (!commandRunner.runCommand("ps aux | grep ts3client | grep -v grep", printOutput = false)
                        .first.outputText.contains("ts3client_linux".toRegex())
                ) {
                    //do nothing
                }
                Thread.sleep(5000)
                return true
            } else {
                statusTextView.text =
                    "Error!\nYou need to provide at least the api key, server address and a nickname for the bot."
                println("Error!\nOptions -a, -s and -n are required. See -h or --help for more information")
                return false
            }
        }

        private fun getChannelFile(settings: BotSettings): File {
            var channelFile = File("")
            if (settings.channelFilePath.isEmpty()) {
                //get the server's name
                val virtualserverName = commandRunner.runCommand(
                    "(echo auth apikey=${settings.apiKey}; echo \"servervariable virtualserver_name\"; echo quit) | nc localhost 25639",
                    printOutput = false
                ).first.outputText.lines()
                var serverName = ""
                println("Getting server name...")
                for (line in virtualserverName) {
                    if (line.contains("virtualserver_name") && line.contains("=")) {
                        serverName = line.split("=".toRegex())[1].replace("\\s", " ")
                        println("Server name: $serverName")
                    }
                }

                //get a path to the channel.txt file
                println("\nGetting path to channel.txt file...")
                val chatDir = File("${System.getProperty("user.home")}/.ts3client/chats")
                println("Looking in \"$chatDir\" for chat files.")
                if (chatDir.exists()) {
                    for (dir in chatDir.list()!!) {
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
                                    channelFile =
                                        File("${System.getProperty("user.home")}/.ts3client/chats/$dir/channel.txt")
                                    println("Using channel file at \"$channelFile\"\n\n")
                                    break
                                }
                            }
                        }
                    }
                }
                // setting channelFile if you have set it in the options
            } else {
                channelFile = File(settings.channelFilePath)
            }

            return channelFile
        }
    }


    //everything below this comment is for the gui stuff
    override fun start(p0: Stage?) {
        try {
            window = p0!!
            variables()
            ui()
            window.show()
        } catch (e: Exception) {
            println(e.stackTrace)
        }
    }

    private fun variables() {
        enterApiKeyTextView.text = "Enter ClientQuery API key"
        enterServerAddressTextView.text = "Enter server address"
        enterChannelNameTextView.text = "Enter channel name"
        enterNicknameTextView.text = "Enter nickname"
        enterServerPortTextView.text = "Enter server port(optional)"
        enterServerPasswordTextView.text = "Enter server password(optional)"
        enterMarketTextView.text = "Enter ISO 3166 standard country code(RECOMMENDED!)"
        enterChannelFilePathTextView.text = "Enter Channel file path(optional)  [channel.html or channel.txt]"

        showAdvancedOptions(false)

        hideAdvancedSettingsRadioButton.text = "Hide advanced settings"
        hideAdvancedSettingsRadioButton.toggleGroup = advancedSettingsRadioGroup
        hideAdvancedSettingsRadioButton.isSelected = true

        showAdvancedSettingsRadioButton.text = "Show advanced settings"
        showAdvancedSettingsRadioButton.toggleGroup = advancedSettingsRadioGroup
        showAdvancedSettingsRadioButton.isSelected = false

        advancedSettingsRadioGroup.selectedToggleProperty().addListener { _, _, _ ->
            when (advancedSettingsRadioGroup.selectedToggle) {
                hideAdvancedSettingsRadioButton -> {
                    hideAdvancedSettingsRadioButton.isSelected = true
                    showAdvancedSettingsRadioButton.isSelected = false

                    showAdvancedOptions(false)
                    if (showPasswordCheckBox.isSelected) {
                        showPasswordCheckBox.isSelected = false
                        serverPasswordVisibleEditText.isManaged = false
                        serverPasswordVisibleEditText.isVisible = false
                        serverPasswordEditText.text = serverPasswordVisibleEditText.text
                    }
                }
                showAdvancedSettingsRadioButton -> {
                    showAdvancedSettingsRadioButton.isSelected = true
                    hideAdvancedSettingsRadioButton.isSelected = false

                    showAdvancedOptions(true)
                }
            }
        }

        serverPasswordVisibleEditText.isManaged = false
        serverPasswordVisibleEditText.isVisible = false
        showPasswordCheckBox.text = "Show password"
        showPasswordCheckBox.onAction = EventHandler {
            if (showPasswordCheckBox.isSelected) {
                serverPasswordEditText.isManaged = false
                serverPasswordEditText.isVisible = false
                serverPasswordVisibleEditText.text = serverPasswordEditText.text
                serverPasswordVisibleEditText.isManaged = true
                serverPasswordVisibleEditText.isVisible = true
            } else {
                serverPasswordVisibleEditText.isManaged = false
                serverPasswordVisibleEditText.isVisible = false
                serverPasswordEditText.text = serverPasswordVisibleEditText.text
                serverPasswordEditText.isManaged = true
                serverPasswordEditText.isVisible = true
            }
        }

        enterMarketTextView.isManaged = false
        enterMarketTextView.isVisible = false
        marketEditText.isManaged = false
        marketEditText.isVisible = false

        browseChannelFileButton.text = "Browse channel file"

        selectSpotifyPlayerTextView.text = "Select Spotify player (ncspot requires Spotify Premium)"
        spotifyRadioButton.text = "Spotify"
        spotifyRadioButton.toggleGroup = spotifyPlayerRadioGroup
        spotifyRadioButton.isSelected = true
        ncspotRadioButton.text = "ncspot"
        ncspotRadioButton.toggleGroup = spotifyPlayerRadioGroup
        ncspotRadioButton.isSelected = false
        spotifyPlayerRadioGroup.selectedToggleProperty().addListener { _, _, _ ->
            when (spotifyPlayerRadioGroup.selectedToggle) {
                spotifyRadioButton -> {
                    spotifyPlayer = "spotify"
                }
                ncspotRadioButton -> {
                    spotifyPlayer = "ncspot"
                }
            }
        }


        saveSettingsButton.text = "Save settings"
        loadSettingsButton.text = "Load settings"
        startBotButton.text = "Start Bot"
        stopBotButton.text = "Stop Bot"
        stopBotButton.isManaged = false
        stopBotButton.isVisible = false


        browseChannelFileButton.onAction = this
        saveSettingsButton.onAction = this
        loadSettingsButton.onAction = this
        startBotButton.onAction = this
        stopBotButton.onAction = this


        statusTextView.text = "Status: Bot not active."
        statusTextView.isEditable = false
    }

    private fun showAdvancedOptions(showAdvanced: Boolean) {
        enterServerPortTextView.isManaged = showAdvanced
        enterServerPortTextView.isVisible = showAdvanced
        enterServerPasswordTextView.isManaged = showAdvanced
        enterServerPasswordTextView.isVisible = showAdvanced
        enterChannelFilePathTextView.isManaged = showAdvanced
        enterChannelFilePathTextView.isVisible = showAdvanced
        serverPortEditText.isManaged = showAdvanced
        serverPortEditText.isVisible = showAdvanced
        serverPasswordEditText.isManaged = showAdvanced
        serverPasswordEditText.isVisible = showAdvanced
        channelFilePathEditText.isManaged = showAdvanced
        channelFilePathEditText.isVisible = showAdvanced
        showPasswordCheckBox.isManaged = showAdvanced
        showPasswordCheckBox.isVisible = showAdvanced
        browseChannelFileButton.isManaged = showAdvanced
        browseChannelFileButton.isVisible = showAdvanced
        enterMarketTextView.isManaged = showAdvanced
        enterMarketTextView.isVisible = showAdvanced
        marketEditText.isManaged = showAdvanced
        marketEditText.isVisible = showAdvanced
        selectSpotifyPlayerTextView.isVisible = showAdvanced
        spotifyRadioButton.isVisible = showAdvanced
        ncspotRadioButton.isVisible = showAdvanced
    }

    private fun ui() {
        //prepare layout
        val statusLayout = VBox()
        layout.spacing = 5.0
        layout.setPrefSize(640.0, 700.0)
        layout.children.addAll(
            scrollPane,
            saveSettingsButton,
            loadSettingsButton,
            startBotButton,
            stopBotButton,
            statusLayout
        )

        //build scene
        scene = Scene(layout, 640.0, 700.0)

        //setup window
        window.title = "TeamSpeak 3 Music Bot"
        window.scene = scene

        VBox.setVgrow(scrollPane, Priority.ALWAYS)

        val itemLayout = VBox()
        itemLayout.spacing = 10.0
        itemLayout.children.addAll(
            enterApiKeyTextView,
            apiKeyEditText,
            enterServerAddressTextView,
            serverAddressEditText,
            enterChannelNameTextView,
            channelNameEditText,
            enterNicknameTextView,
            nicknameEditText,
            hideAdvancedSettingsRadioButton,
            showAdvancedSettingsRadioButton,
            enterServerPortTextView,
            serverPortEditText,
            enterServerPasswordTextView,
            serverPasswordEditText,
            serverPasswordVisibleEditText,
            showPasswordCheckBox,
            enterMarketTextView,
            marketEditText,
            enterChannelFilePathTextView,
            channelFilePathEditText,
            browseChannelFileButton,
            selectSpotifyPlayerTextView,
            spotifyRadioButton,
            ncspotRadioButton
        )
        scrollPane.content = itemLayout
        scrollPane.minHeight = 250.0
        scrollPane.isFitToWidth = true
        scrollPane.isFitToHeight = true
        scrollPane.heightProperty().addListener { _ -> scrollPane.vvalue = 0.0 }


        statusLayout.children.addAll(statusTextView)
        statusLayout.minHeight = 75.0
    }

    override fun handle(p0: ActionEvent?) {
        when (p0?.source) {
            browseChannelFileButton -> {
                val fileChooser = FileChooser()
                fileChooser.title = "Select TeamSpeak chat file. (channel.html or channel.txt)"
                fileChooser.initialDirectory = File("${System.getProperty("user.home")}/.ts3client/chats")
                fileChooser.selectedExtensionFilter =
                    FileChooser.ExtensionFilter("Chat File", listOf("*.html", "*.txt"))

                val file = fileChooser.showOpenDialog(window)
                inputFilePath = file.absolutePath
                channelFilePathEditText.text = inputFilePath
            }

            saveSettingsButton -> {
                val apiKey = apiKeyEditText.text
                val serverAddress = serverAddressEditText.text
                val serverPort = serverPortEditText.text
                val serverPassword = if (showPasswordCheckBox.isSelected) {
                    serverPasswordVisibleEditText.text
                } else {
                    serverPasswordEditText.text
                }
                val channelName = channelNameEditText.text
                val channelFilePath = channelFilePathEditText.text
                val nickname = nicknameEditText.text
                val market = marketEditText.text

                val settings = BotSettings(
                    apiKey,
                    serverAddress,
                    serverPort,
                    serverPassword,
                    channelName,
                    channelFilePath,
                    nickname,
                    market,
                    spotifyPlayer
                )
                saveSettings(settings, true)
            }

            loadSettingsButton -> {
                val fileChooser = FileChooser()
                fileChooser.title = "Select config file which contains bot settings"
                fileChooser.initialDirectory = File(System.getProperty("user.dir"))
                fileChooser.selectedExtensionFilter =
                    FileChooser.ExtensionFilter("Configuration File", listOf("*.config", "*.conf"))
                val file: File? = fileChooser.showOpenDialog(window)
                if (file != null && file.exists()) {
                    val settings = loadSettings(file)
                    apiKeyEditText.text = settings.apiKey
                    serverAddressEditText.text = settings.serverAddress
                    channelNameEditText.text = settings.channelName
                    nicknameEditText.text = settings.nickname
                    serverPortEditText.text = settings.serverPort
                    serverPasswordEditText.text = settings.serverPassword
                    channelFilePathEditText.text = settings.channelFilePath
                    marketEditText.text = settings.market
                    when (settings.spotifyPlayer) {
                        "spotify" -> {
                            spotifyRadioButton.isSelected = true
                            ncspotRadioButton.isSelected = false
                        }
                        "ncspot" -> {
                            ncspotRadioButton.isSelected = true
                            spotifyRadioButton.isSelected = false
                        }
                    }
                } else {
                    println("Could not load file!")
                }
            }

            startBotButton -> {
                val settings = getSettings()
                //start teamspeak
                if (startTeamSpeak(settings)) {
                    //start reading chat
                    chatReader = ChatReader(
                        getChannelFile(settings),
                        this, this,
                        settings.apiKey,
                        market = settings.market,
                        spotifyPlayer = settings.spotifyPlayer,
                        channelName = settings.channelName,
                        botName = settings.nickname
                    )
                    if (chatReader.startReading()) {
                        statusTextView.text = "Status: Connected."
                        startBotButton.isManaged = false
                        startBotButton.isVisible = false
                        stopBotButton.isManaged = true
                        stopBotButton.isVisible = true
                    }
                }
            }

            stopBotButton -> {
                commandRunner.runCommand(
                    "xdotool search \"Teamspeak 3\" windowactivate --sync key --window 0 --clearmodifiers alt+F4",
                    ignoreOutput = true
                )
                chatReader.stopReading()
                statusTextView.text = "Status: Bot not active."
                startBotButton.isManaged = true
                startBotButton.isVisible = true
                stopBotButton.isManaged = false
                stopBotButton.isVisible = false
            }
        }
    }

    //gets settings from text fields in gui
    private fun getSettings(): BotSettings = BotSettings(
        apiKeyEditText.text,
        serverAddressEditText.text,
        serverPortEditText.text,
        serverPasswordEditText.text,
        channelNameEditText.text,
        channelFilePathEditText.text,
        nicknameEditText.text,
        marketEditText.text,
        when (spotifyPlayerRadioGroup.selectedToggle) {
            spotifyRadioButton -> "spotify"
            ncspotRadioButton -> "ncspot"
            else -> "spotify" //default to spotify
        }
    )


    override fun onChatUpdated(update: ChatUpdate) {
        if (commandList.any { update.message.startsWith(it) }) {
            statusTextView.appendText("\nUser: ${update.userName}\nCommand: ${update.message}\n")
            statusScrollPane.layout()
            statusScrollPane.vvalue = 1.0
        }
    }

    override fun onCommandExecuted(command: String, output: String, extra: Any?) {
        //TODO Not yet implemented
    }
}

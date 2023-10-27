package ts3_musicbot

import javafx.application.Application
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import ts3_musicbot.chat.ChatReader
import ts3_musicbot.chat.ChatUpdate
import ts3_musicbot.chat.ChatUpdateListener
import ts3_musicbot.chat.CommandListener
import ts3_musicbot.client.TeamSpeak
import ts3_musicbot.util.CommandList
import java.io.File
import java.io.PrintWriter
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import ts3_musicbot.client.OfficialTSClient
import ts3_musicbot.util.*

var inputFilePath = ""
private lateinit var window: Stage
private var statusTextView = TextArea()
private val commandRunner = CommandRunner()
private var commandList = CommandList()
private lateinit var teamSpeak: TeamSpeak
private lateinit var officialTSClient: OfficialTSClient
private lateinit var chatReader: ChatReader

class Main : Application(), EventHandler<ActionEvent>, ChatUpdateListener, CommandListener {
    private lateinit var scene: Scene

    private var layout: VBox = VBox()
    private var scrollPane: ScrollPane = ScrollPane()
    private var statusScrollPane: ScrollPane = ScrollPane()
    private var enterApiKeyTextView = Label()
    private var enterServerAddressTextView = Label()
    private var enterChannelNameTextView = Label()
    private var enterChannelPasswordTextView = Label()
    private var enterNicknameTextView = Label()
    private var enterServerPortTextView = Label()
    private var enterServerPasswordTextView = Label()
    private var enterChannelFilePathTextView = Label()
    private var enterMarketTextView = Label()

    private var apiKeyEditText = TextField()
    private var serverAddressEditText = TextField()
    private var channelNameEditText = TextField()
    private var channelPasswordEditText = PasswordField()
    private var channelPasswordVisibleEditText = TextField()
    private var nicknameEditText = TextField()
    private var serverPortEditText = TextField()
    private var serverPasswordEditText = PasswordField()
    private var serverPasswordVisibleEditText = TextField()
    private var channelFilePathEditText = TextField()
    private var marketEditText = TextField()

    private var advancedSettingsRadioGroup = ToggleGroup()
    private var hideAdvancedSettingsRadioButton = RadioButton()
    private var showAdvancedSettingsRadioButton = RadioButton()

    private var showServerPasswordCheckBox = CheckBox()
    private var showChannelPasswordCheckBox = CheckBox()
    private var showSpPasswordCheckBox = CheckBox()
    private var browseChannelFileButton = Button()
    private var selectSpotifyPlayerTextView = Label()
    private var spotifyPlayerRadioGroup = ToggleGroup()
    private var spotifyRadioButton = RadioButton()
    private var ncspotRadioButton = RadioButton()
    private var spotifydRadioButton = RadioButton()
    private var enterSpUsernameTextView = Label()
    private var spUsernameEditText = TextField()
    private var enterSpPasswordTextView = Label()
    private var spPasswordEditText = PasswordField()
    private var spPasswordVisibleEditText = TextField()
    private var selectTsClientTextView = Label()
    private var tsClientRadioGroup = ToggleGroup()
    private var tsClientInternalRadioButton = RadioButton()
    private var tsClientOfficialRadioButton = RadioButton()
    private var scVolumeTextView = Label()
    private var scVolumeEditText = TextField()
    private var ytVolumeTextView = Label()
    private var ytVolumeEditText = TextField()
    private var loadCustomCommandsButton = Button()
    private var saveSettingsButton = Button()
    private var loadSettingsButton = Button()
    private var startBotButton = Button()
    private var stopBotButton = Button()

    private var settings = BotSettings()


    companion object : ChatUpdateListener, CommandListener {
        @JvmStatic
        fun main(args: Array<String>) {

            //check if command line arguments are provided
            if (args.isNotEmpty()) {
                val defaultSettings = BotSettings()
                var apiKey = ""
                var serverAddress = ""
                var serverPort = defaultSettings.serverPort
                var nickname = defaultSettings.nickname
                var serverPassword = ""
                var channelName = ""
                var channelPassword = ""
                var channelFilePath = ""
                var market = ""
                var configFile = ""
                var commandConfig = ""
                var spotifyPlayer = defaultSettings.spotifyPlayer
                var spotifyUsername = defaultSettings.spotifyUsername
                var spotifyPassword = defaultSettings.spotifyPassword
                var scVolume = defaultSettings.scVolume
                var ytVolume = defaultSettings.ytVolume
                var useOfficialTsClient = true

                val helpMessage = "\n" +
                        "TS3 Music Bot help message\n" +
                        "Available options:\n" +
                        "-h, --help               Show this help message\n" +
                        "-a, --apikey             ClientQuery api key\n" +
                        "-s, --serveraddress      Server address to connect to\n" +
                        "-p, --serverport         Server's port. Usually 9987\n" +
                        "-P, --serverpassword     Server's password\n" +
                        "-c, --channelname        The channel's name the bot should connect to after connecting to the server.\n" +
                        "--channelpassword        The channel's password the bot should connect to.\n" +
                        "-C, --channelfile        Provide a path to a channel.txt file. You also need to provide the channel name with -c option.\n" +
                        "-n, --nickname           The nickname of the bot.\n" +
                        "-m, --market             Specify a market/country for Spotify.\n" +
                        "--spotify <client>       Specify a spotify client to use. Can be spotify, ncspot or spotifyd.\n" +
                        "--sp-user <username>     Specify the username to use in the official spotify client.\n" +
                        "--sp-pass <password>     Specify the password to use in the official spotify client.\n" +
                        "--config                 Provide a config file where to read the bot's settings from\n" +
                        "--command-config         Provide a config file where to read custom commands from\n" +
                        "--use-internal-tsclient  Use the internal TeamSpeak client instead of the official one.(WIP!)\n" +
                        "--sc-volume <volume>     Set the volume for MPV media player when playing SoundCloud content.\n" +
                        "--yt-volume <volume>     Set the volume for MPV media player when playing YouTube content.\n"

                //go through given arguments and save them
                for (argPos in args.indices) {
                    when (args[argPos]) {
                        "-h", "--help" -> {
                            print(helpMessage)
                            exitProcess(0)
                        }

                        "-a", "--apikey" -> {
                            //"if (args.size >= argPos +1)" is a safe check
                            //in case a user provides a flag, but no argument
                            if (args.size >= argPos + 1)
                                apiKey = args[argPos + 1]
                        }

                        "-s", "--serveraddress" -> {
                            if (args.size >= argPos + 1)
                                serverAddress = args[argPos + 1]
                        }

                        "-p", "--serverport" -> {
                            if (args.size >= argPos + 1)
                                serverPort = args[argPos + 1].toInt()
                        }

                        "-P", "--serverpassword" -> {
                            if (args.size >= argPos + 1)
                                serverPassword = args[argPos + 1]
                        }

                        "-c", "--channelname" -> {
                            if (args.size >= argPos + 1)
                                channelName = args[argPos + 1]
                        }

                        "--channelpassword" -> {
                            if (args.size >= argPos + 1)
                                channelPassword = args[argPos + 1]
                        }

                        "-C", "--channelfile" -> {
                            if (args.size >= argPos + 1)
                                channelFilePath = args[argPos + 1]
                        }

                        "-n", "--nickname" -> {
                            if (args.size >= argPos + 1)
                                nickname = args[argPos + 1]
                        }

                        "-m", "--market" -> {
                            if (args.size >= argPos + 1)
                                market = args[argPos + 1]
                        }

                        "--spotify" -> {
                            if (args.size >= argPos + 1)
                                spotifyPlayer = args[argPos + 1]
                        }

                        "--sp-user" -> {
                            if (args.size >= argPos + 1)
                                spotifyUsername = args[argPos + 1]
                        }

                        "--sp-pass" -> {
                            if (args.size >= argPos + 1)
                                spotifyPassword = args[argPos + 1]
                        }

                        "--config" -> {
                            if (args.size >= argPos + 1)
                                configFile = args[argPos + 1]
                        }

                        "--command-config" -> {
                            if (args.size >= argPos + 1)
                                commandConfig = args[argPos + 1]
                        }

                        "--use-internal-tsclient" -> {
                            if (args.size >= argPos + 1)
                                useOfficialTsClient = false
                        }

                        "--sc-volume" -> {
                            if (args.size >= argPos + 1)
                                scVolume = args[argPos + 1].toInt()
                        }

                        "--yt-volume" -> {
                            if (args.size >= argPos + 1)
                                ytVolume = args[argPos + 1].toInt()
                        }
                    }
                }

                val botSettings: BotSettings =
                    if (configFile.isNotEmpty()) {
                        loadSettings(File(configFile))
                    } else {
                        BotSettings(
                            apiKey,
                            serverAddress,
                            serverPort,
                            serverPassword,
                            channelName,
                            channelPassword,
                            channelFilePath,
                            nickname,
                            market,
                            spotifyPlayer,
                            spotifyUsername,
                            spotifyPassword,
                            useOfficialTsClient,
                            scVolume,
                            ytVolume
                        )
                    }

                if (commandConfig.isNotEmpty()) {
                    commandList = loadCommands(File(commandConfig))
                }
                if (botSettings.useOfficialTsClient) {
                    //connect to desired server and channel,
                    //after which find the server's channel file and start listening for commands
                    println("Starting TeamSpeak 3...")
                    println("Connecting to server at: ${botSettings.serverAddress}, port ${botSettings.serverPort}.")
                    println("Using ${botSettings.nickname} as the bot\'s nickname.")
                    officialTSClient = OfficialTSClient(botSettings)
                    CoroutineScope(IO).launch {
                        if (!File("${officialTSClient.tsClientDirPath}/settings.db").exists())
                            officialTSClient.exportTeamSpeakSettings()

                        if (officialTSClient.startTeamSpeak()) {
                            officialTSClient.joinChannel()
                            chatReader = ChatReader(
                                officialTSClient,
                                botSettings,
                                this@Companion,
                                this@Companion,
                                commandList
                            )
                            if (chatReader.startReading()) {
                                println("Bot ${botSettings.nickname} started listening to the chat in channel ${botSettings.channelName}.")
                                val console = Console(commandList, object : ConsoleUpdateListener {
                                    override fun onCommandIssued(command: String) {
                                        if (command.startsWith(commandList.commandPrefix)) {
                                            chatReader.latestMsgUsername = "__console__"
                                            chatReader.parseLine(command)
                                        } else {
                                            when (command) {
                                                "save-settings" -> {
                                                    saveSettings(botSettings, showGui = false)
                                                }
                                            }
                                        }
                                    }
                                }, officialTSClient)
                                console.startConsole()
                            } else print("Error: Bot wasn't able start reading the chat!")
                        } else {
                            println("Error!\nOption -s is required. See -h or --help for more information")
                            exitProcess(0)
                        }
                    }
                } else {
                    //use built-in teamspeak client
                    if (botSettings.serverAddress.isNotEmpty()) {
                        teamSpeak = TeamSpeak(botSettings)
                        teamSpeak.connect()
                        runBlocking {
                            teamSpeak.joinChannel()
                        }
                        chatReader = ChatReader(
                            teamSpeak,
                            botSettings,
                            this@Companion,
                            this@Companion,
                            commandList
                        )
                        if (chatReader.startReading()) {
                            println("Bot ${botSettings.nickname} started listening to the chat in channel ${botSettings.channelName}.")
                            val console = Console(commandList, object : ConsoleUpdateListener {
                                override fun onCommandIssued(command: String) {
                                    if (command.startsWith(commandList.commandPrefix)) {
                                        chatReader.latestMsgUsername = "__console__"
                                        chatReader.parseLine(command)
                                    } else {
                                        when (command) {
                                            "save-settings" -> {
                                                saveSettings(botSettings, showGui = false)
                                            }
                                        }
                                    }
                                }
                            }, teamSpeak)
                            console.startConsole()
                        } else print("Error: Bot wasn't able to start reading the chat!")
                    } else {
                        println("Error!\nOption -s is required. See -h or --help for more information")
                        exitProcess(0)
                    }
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
                fileWriter.println("CHANNEL_PASSWORD=${botSettings.channelPassword}")
                fileWriter.println("CHANNEL_FILE_PATH=${botSettings.channelFilePath}")
                fileWriter.println("NICKNAME=${botSettings.nickname}")
                fileWriter.println("MARKET=${botSettings.market}")
                fileWriter.println("SPOTIFY_PLAYER=${botSettings.spotifyPlayer}")
                fileWriter.println("SPOTIFY_USERNAME=${botSettings.spotifyUsername}")
                fileWriter.println("SPOTIFY_PASSWORD=${botSettings.spotifyPassword}")
                fileWriter.println("USE_OFFICIAL_TSCLIENT=${botSettings.useOfficialTsClient}")
                fileWriter.println("SC_VOLUME=${botSettings.scVolume}")
                fileWriter.println("YT_VOLUME=${botSettings.ytVolume}")
                fileWriter.println()
                fileWriter.close()
            }
        }

        private fun loadSettings(settingsFile: File): BotSettings {
            val settings = BotSettings()

            if (settingsFile.exists()) {
                for (line in settingsFile.readLines()) {
                    when (line.substringBefore("=")) {
                        "API_KEY" -> settings.apiKey = line.substringAfter("=").replace(" ", "")
                        "SERVER_ADDRESS" -> settings.serverAddress = line.substringAfter("=").replace(" ", "")
                        "SERVER_PORT" -> {
                            val port = line.substringAfter("=").replace(" ", "")
                            settings.serverPort = if (port.isNotEmpty()) port.toInt() else settings.serverPort
                        }

                        "SERVER_PASSWORD" -> settings.serverPassword = line.substringAfter("=")
                        "CHANNEL_NAME" -> settings.channelName = line.substringAfter("=")
                        "CHANNEL_PASSWORD" -> settings.channelPassword = line.substringAfter("=")
                        "CHANNEL_FILE_PATH" -> settings.channelFilePath =
                            line.substringAfter("=").substringBeforeLast(" ")

                        "NICKNAME" -> settings.nickname = line.substringAfter("=").replace("\\s+$".toRegex(), "")
                        "MARKET" -> settings.market = line.substringAfter("=").replace(" ", "")
                        "SPOTIFY_PLAYER" -> settings.spotifyPlayer = line.substringAfter("=").replace(" ", "")
                        "SPOTIFY_USERNAME" -> settings.spotifyUsername =
                            line.substringAfter("=").replace("\\s+".toRegex(), "")

                        "SPOTIFY_PASSWORD" -> settings.spotifyPassword = line.substringAfter("=")
                        "USE_OFFICIAL_TSCLIENT" -> settings.useOfficialTsClient =
                            line.substringAfter("=").replace(" ", "").toBoolean()

                        "SC_VOLUME" -> settings.scVolume = line.substringAfter("=").replace(" ", "").toInt()
                        "YT_VOLUME" -> settings.ytVolume = line.substringAfter("=").replace(" ", "").toInt()
                    }
                }
            }

            println("Loaded settings from ${settingsFile.absolutePath}")
            return settings
        }

        private fun loadCommands(commandsFile: File): CommandList {
            var prefix = commandList.commandPrefix
            val commands = mutableMapOf<String, String>()
            if (commandsFile.exists()) {
                commandsFile.readLines().forEach { line ->
                    if (line.contains("^[A-Z_]+\\s*=\\S*".toRegex())) {
                        val (key, value) = line.lowercase().replaceFirst('_', '-').split("=")
                        if (key == "command-prefix")
                            prefix = value
                        else
                            commands[key] = value.substringBefore(" ")
                    }
                }
            }
            commandList.applyCustomCommands(prefix, commands)
            println("Loaded custom commands from ${commandsFile.absolutePath}")
            return commandList
        }

        override fun onChatUpdated(update: ChatUpdate) {
            if (commandList.commandList.any { update.message.startsWith(it.value) }) {
                print("\nUser ${update.userName} issued command \"${update.message}\"\nCommand: ")
            } else {
                println("\nUser ${update.userName} said:\n${update.message}")
            }
        }

        override fun onCommandExecuted(command: String, output: String, extra: Any?) {
            print("\nCommand \"${command.substringBefore(" ")}\" has been executed.\nOutput:\n$output\n\nCommand: ")
        }

        override fun onCommandProgress(command: String, output: String, extra: Any?) {
            print("\n\"${command.substringBefore(" ")}\" command progress update:\nOutput:\n$output\n\nCommand: ")
        }
    }


    //everything below this comment is for the gui stuff
    override fun start(p0: Stage?) {
        try {
            window = (p0 ?: return)
            variables()
            ui()
            window.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun variables() {
        enterApiKeyTextView.text = "Enter ClientQuery API key(optional)"
        enterServerAddressTextView.text = "Enter server address"
        enterChannelNameTextView.text = "Enter channel name"
        enterChannelPasswordTextView.text = "Enter channel password(optional)"
        enterNicknameTextView.text = "Enter nickname"
        enterServerPortTextView.text = "Enter server port(optional)"
        enterServerPasswordTextView.text = "Enter server password(optional)"
        enterMarketTextView.text = "Enter ISO 3166 standard country code(HIGHLY RECOMMENDED!)"
        enterChannelFilePathTextView.text = "Enter Channel file path(optional)  [channel.txt or channel.html]"
        enterSpUsernameTextView.text = "Enter Spotify username"
        enterSpPasswordTextView.text = "Enter Spotify password"

        showAdvancedOptions(false)

        fun CheckBox.handlePasswordVisibility(pwField: PasswordField, textField: TextField) {
            val showPwText = "Show password"
            textField.isManaged = false
            textField.isVisible = false
            this.text = showPwText
            this.onAction = EventHandler {
                if (this.isSelected) {
                    pwField.isManaged = false
                    pwField.isVisible = false
                    textField.text = pwField.text
                    textField.isManaged = true
                    textField.isVisible = true
                } else {
                    textField.isManaged = false
                    textField.isVisible = false
                    pwField.text = textField.text
                    pwField.isManaged = true
                    pwField.isVisible = true
                }
            }
        }

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
                    if (showServerPasswordCheckBox.isSelected) {
                        showServerPasswordCheckBox.isSelected = false
                        serverPasswordVisibleEditText.isManaged = false
                        serverPasswordVisibleEditText.isVisible = false
                        serverPasswordEditText.text = serverPasswordVisibleEditText.text
                    }

                    if (showSpPasswordCheckBox.isSelected) {
                        showSpPasswordCheckBox.isSelected = false
                        spPasswordVisibleEditText.isManaged = false
                        spPasswordVisibleEditText.isVisible = false
                        spPasswordEditText.text = spPasswordVisibleEditText.text
                    }
                }

                showAdvancedSettingsRadioButton -> {
                    showAdvancedSettingsRadioButton.isSelected = true
                    hideAdvancedSettingsRadioButton.isSelected = false

                    showAdvancedOptions(true)
                }
            }
        }

        showChannelPasswordCheckBox.handlePasswordVisibility(channelPasswordEditText, channelPasswordVisibleEditText)
        showServerPasswordCheckBox.handlePasswordVisibility(serverPasswordEditText, serverPasswordVisibleEditText)

        enterMarketTextView.isManaged = false
        enterMarketTextView.isVisible = false
        marketEditText.isManaged = false
        marketEditText.isVisible = false

        browseChannelFileButton.text = "Browse channel file"

        selectSpotifyPlayerTextView.text = "Select Spotify player (ncspot and spotifyd require Spotify Premium)"
        spotifyRadioButton.text = "Spotify"
        spotifyRadioButton.toggleGroup = spotifyPlayerRadioGroup
        spotifyRadioButton.isSelected = true
        ncspotRadioButton.text = "ncspot"
        ncspotRadioButton.toggleGroup = spotifyPlayerRadioGroup
        ncspotRadioButton.isSelected = false
        spotifydRadioButton.text = "Spotifyd"
        spotifydRadioButton.toggleGroup = spotifyPlayerRadioGroup
        spotifydRadioButton.isSelected = false
        spotifyPlayerRadioGroup.selectedToggleProperty().addListener { _, _, _ ->
            when (spotifyPlayerRadioGroup.selectedToggle) {
                spotifyRadioButton -> settings.spotifyPlayer = "spotify"
                ncspotRadioButton -> settings.spotifyPlayer = "ncspot"
                spotifydRadioButton -> settings.spotifyPlayer = "spotifyd"
            }
            if (spotifyPlayerRadioGroup.selectedToggle == spotifyRadioButton) {
                enterSpUsernameTextView.isVisible = true
                enterSpUsernameTextView.isManaged = true
                spUsernameEditText.isVisible = true
                spUsernameEditText.isManaged = true
                enterSpPasswordTextView.isVisible = true
                enterSpPasswordTextView.isManaged = true
                if (showSpPasswordCheckBox.isSelected) {
                    spPasswordEditText.isVisible = false
                    spPasswordEditText.isManaged = false
                    spPasswordVisibleEditText.isVisible = true
                    spPasswordVisibleEditText.isManaged = true
                } else {
                    spPasswordEditText.isVisible = true
                    spPasswordEditText.isManaged = true
                    spPasswordVisibleEditText.isVisible = false
                    spPasswordVisibleEditText.isManaged = false
                }
                showSpPasswordCheckBox.isVisible = true
                showSpPasswordCheckBox.isManaged = true
            } else {
                enterSpUsernameTextView.isVisible = false
                enterSpUsernameTextView.isManaged = false
                spUsernameEditText.isVisible = false
                spUsernameEditText.isManaged = false
                enterSpPasswordTextView.isVisible = false
                enterSpPasswordTextView.isManaged = false
                spPasswordEditText.isVisible = false
                spPasswordEditText.isManaged = false
                spPasswordVisibleEditText.isVisible = false
                spPasswordVisibleEditText.isManaged = false
                showSpPasswordCheckBox.isVisible = false
                showSpPasswordCheckBox.isManaged = false
            }
        }

        showSpPasswordCheckBox.handlePasswordVisibility(spPasswordEditText, spPasswordVisibleEditText)

        selectTsClientTextView.text = "Select TeamSpeak client to use"
        tsClientInternalRadioButton.text = "Internal/Built-in client(WIP!)"
        tsClientInternalRadioButton.toggleGroup = tsClientRadioGroup
        tsClientInternalRadioButton.isSelected = !settings.useOfficialTsClient
        tsClientOfficialRadioButton.text = "Official client"
        tsClientOfficialRadioButton.toggleGroup = tsClientRadioGroup
        tsClientOfficialRadioButton.isSelected = settings.useOfficialTsClient

        scVolumeTextView.text =
            "Set MPV Media Player volume for SoundCloud playback. Defaults to ${BotSettings().scVolume}"
        scVolumeTextView.isVisible = false
        scVolumeEditText.isVisible = false
        ytVolumeTextView.text =
            "Set MPV Media Player volume for SoundCloud playback. Defaults to ${BotSettings().ytVolume}"
        ytVolumeTextView.isVisible = false
        ytVolumeEditText.isVisible = false

        loadCustomCommandsButton.text = "Load custom commands"
        loadCustomCommandsButton.isVisible = false

        saveSettingsButton.text = "Save settings"
        loadSettingsButton.text = "Load settings"
        startBotButton.text = "Start Bot"
        stopBotButton.text = "Stop Bot"
        stopBotButton.isManaged = false
        stopBotButton.isVisible = false


        browseChannelFileButton.onAction = this
        loadCustomCommandsButton.onAction = this
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
        showServerPasswordCheckBox.isManaged = showAdvanced
        showServerPasswordCheckBox.isVisible = showAdvanced
        browseChannelFileButton.isManaged = showAdvanced
        browseChannelFileButton.isVisible = showAdvanced
        enterMarketTextView.isManaged = showAdvanced
        enterMarketTextView.isVisible = showAdvanced
        marketEditText.isManaged = showAdvanced
        marketEditText.isVisible = showAdvanced
        selectSpotifyPlayerTextView.isVisible = showAdvanced
        spotifyRadioButton.isVisible = showAdvanced
        ncspotRadioButton.isVisible = showAdvanced
        spotifydRadioButton.isVisible = showAdvanced
        selectSpotifyPlayerTextView.isManaged = showAdvanced
        spotifyRadioButton.isManaged = showAdvanced
        ncspotRadioButton.isManaged = showAdvanced
        spotifydRadioButton.isManaged = showAdvanced
        enterSpUsernameTextView.isVisible = showAdvanced
        enterSpPasswordTextView.isManaged = showAdvanced
        spUsernameEditText.isVisible = showAdvanced
        spUsernameEditText.isManaged = showAdvanced
        enterSpPasswordTextView.isManaged = showAdvanced
        enterSpPasswordTextView.isVisible = showAdvanced
        spPasswordEditText.isVisible = showAdvanced
        spPasswordEditText.isManaged = showAdvanced
        showSpPasswordCheckBox.isManaged = showAdvanced
        showSpPasswordCheckBox.isVisible = showAdvanced
        selectTsClientTextView.isVisible = showAdvanced
        tsClientInternalRadioButton.isVisible = showAdvanced
        tsClientOfficialRadioButton.isVisible = showAdvanced
        scVolumeTextView.isVisible = showAdvanced
        scVolumeEditText.isVisible = showAdvanced
        ytVolumeTextView.isVisible = showAdvanced
        ytVolumeEditText.isVisible = showAdvanced
        loadCustomCommandsButton.isVisible = showAdvanced
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
            enterChannelPasswordTextView,
            channelPasswordEditText,
            channelPasswordVisibleEditText,
            showChannelPasswordCheckBox,
            enterNicknameTextView,
            nicknameEditText,
            hideAdvancedSettingsRadioButton,
            showAdvancedSettingsRadioButton,
            enterServerPortTextView,
            serverPortEditText,
            enterServerPasswordTextView,
            serverPasswordEditText,
            serverPasswordVisibleEditText,
            showServerPasswordCheckBox,
            enterMarketTextView,
            marketEditText,
            enterChannelFilePathTextView,
            channelFilePathEditText,
            browseChannelFileButton,
            selectSpotifyPlayerTextView,
            spotifyRadioButton,
            ncspotRadioButton,
            spotifydRadioButton,
            enterSpUsernameTextView,
            spUsernameEditText,
            enterSpPasswordTextView,
            spPasswordEditText,
            spPasswordVisibleEditText,
            showSpPasswordCheckBox,
            selectTsClientTextView,
            tsClientInternalRadioButton,
            tsClientOfficialRadioButton,
            scVolumeTextView,
            scVolumeEditText,
            ytVolumeTextView,
            ytVolumeEditText,
            loadCustomCommandsButton
        )
        scrollPane.content = itemLayout
        scrollPane.minHeight = 250.0
        scrollPane.isFitToWidth = true
        scrollPane.isFitToHeight = true
        scrollPane.heightProperty().addListener { _ -> scrollPane.vvalue = 0.0 }


        statusLayout.children.addAll(statusTextView)
        statusLayout.minHeight = 75.0
    }

    private fun updateStatus(message: String) {
        statusTextView.appendText(message)
        statusScrollPane.layout()
        statusScrollPane.vvalue = 1.0
    }

    override fun handle(p0: ActionEvent?) {
        when (p0?.source) {
            browseChannelFileButton -> {
                val fileChooser = FileChooser()
                fileChooser.title = "Select TeamSpeak chat file. (channel.txt)"
                fileChooser.initialDirectory = File("${System.getProperty("user.home")}/.ts3client/chats")
                fileChooser.selectedExtensionFilter =
                    FileChooser.ExtensionFilter("Chat File", listOf("*.html", "*.txt"))

                val file = fileChooser.showOpenDialog(window)
                inputFilePath = file.absolutePath
                channelFilePathEditText.text = inputFilePath
            }

            loadCustomCommandsButton -> {
                val fileChooser = FileChooser()
                fileChooser.title = "Select custom commands config file."
                fileChooser.initialDirectory = File(System.getProperty("user.home"))
                fileChooser.selectedExtensionFilter =
                    FileChooser.ExtensionFilter("Custom Commands config", listOf("*.config", "*.conf"))

                val file = fileChooser.showOpenDialog(window)
                commandList = loadCommands(file)
            }

            saveSettingsButton -> saveSettings(getSettings(), true)

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
                    channelPasswordEditText.text = settings.channelPassword
                    nicknameEditText.text = settings.nickname
                    serverPortEditText.text = settings.serverPort.toString()
                    serverPasswordEditText.text = settings.serverPassword
                    channelFilePathEditText.text = settings.channelFilePath
                    marketEditText.text = settings.market
                    when (settings.spotifyPlayer) {
                        "spotify" -> {
                            spotifyRadioButton.isSelected = true
                            ncspotRadioButton.isSelected = false
                            spotifydRadioButton.isSelected = false
                        }

                        "ncspot" -> {
                            spotifyRadioButton.isSelected = false
                            ncspotRadioButton.isSelected = true
                            spotifydRadioButton.isSelected = false
                        }

                        "spotifyd" -> {
                            spotifyRadioButton.isSelected = false
                            ncspotRadioButton.isSelected = false
                            spotifydRadioButton.isSelected = true
                        }
                    }
                    spUsernameEditText.text = settings.spotifyUsername
                    spPasswordEditText.text = settings.spotifyPassword
                    if (settings.useOfficialTsClient) {
                        tsClientInternalRadioButton.isSelected = false
                        tsClientOfficialRadioButton.isSelected = true
                    } else {
                        tsClientInternalRadioButton.isSelected = true
                        tsClientOfficialRadioButton.isSelected = false
                    }
                    scVolumeEditText.text = settings.scVolume.toString()
                    ytVolumeEditText.text = settings.ytVolume.toString()
                } else {
                    println("Could not load file!")
                }
            }

            startBotButton -> {
                val settings = getSettings()
                //start teamspeak
                if (settings.useOfficialTsClient) {
                    officialTSClient = OfficialTSClient(settings)
                    CoroutineScope(IO).launch {
                        if (!File("${officialTSClient.tsClientDirPath}/settings.db").exists())
                            officialTSClient.exportTeamSpeakSettings()

                        if (officialTSClient.startTeamSpeak()) {
                            statusTextView.text = "Status: Connected."
                            startBotButton.isManaged = false
                            startBotButton.isVisible = false
                            stopBotButton.isManaged = true
                            stopBotButton.isVisible = true
                            officialTSClient.joinChannel()
                            chatReader = ChatReader(
                                officialTSClient,
                                settings,
                                this@Main,
                                this@Main,
                                commandList
                            )
                            if (chatReader.startReading()) {
                                println("Bot ${settings.nickname} started listening to the chat in channel ${settings.channelName}.")
                                val console = Console(commandList, object : ConsoleUpdateListener {
                                    override fun onCommandIssued(command: String) {
                                        if (command.startsWith(commandList.commandPrefix)) {
                                            chatReader.latestMsgUsername = "__console__"
                                            chatReader.parseLine(command)
                                        } else {
                                            when (command) {
                                                "save-settings" -> {
                                                    saveSettings(settings, showGui = false)
                                                }
                                            }
                                        }
                                    }
                                }, officialTSClient)
                                CoroutineScope(IO).launch {
                                    console.startConsole()
                                }
                            } else {
                                val errorText = "Error: Bot wasn't able start reading the chat!"
                                statusTextView.text = errorText
                                println(errorText)
                            }
                        } else {
                            statusTextView.text =
                                "Error!\nYou need to provide at least the server address where the bot should connect to."
                            println("Error!\nOptions -a, -s and -n are required. See -h or --help for more information")
                        }
                    }
                } else {
                    teamSpeak = TeamSpeak(settings)
                    if (teamSpeak.connect()) {
                        statusTextView.text = "Status: Connected."
                        startBotButton.isManaged = false
                        startBotButton.isVisible = false
                        stopBotButton.isManaged = true
                        stopBotButton.isVisible = true
                        runBlocking {
                            teamSpeak.joinChannel()
                        }
                        chatReader = ChatReader(
                            teamSpeak,
                            settings,
                            this@Main,
                            this@Main,
                            commandList
                        )
                        if (chatReader.startReading()) {
                            println("Bot ${settings.nickname} started listening to the chat in channel ${settings.channelName}.")
                            val console = Console(commandList, object : ConsoleUpdateListener {
                                override fun onCommandIssued(command: String) {
                                    if (command.startsWith(commandList.commandPrefix)) {
                                        chatReader.latestMsgUsername = "__console__"
                                        chatReader.parseLine(command)
                                    } else {
                                        when (command) {
                                            "save-settings" -> {
                                                saveSettings(settings, showGui = false)
                                            }
                                        }
                                    }
                                }
                            }, teamSpeak)
                            CoroutineScope(IO).launch {
                                console.startConsole()
                            }
                        } else {
                            val errorText = "Error: Bot wasn't able start reading the chat!"
                            statusTextView.text = errorText
                            println(errorText)
                        }
                    } else {
                        statusTextView.text = "Status: Connection failed."
                        startBotButton.isManaged = true
                        startBotButton.isVisible = true
                        stopBotButton.isManaged = false
                        stopBotButton.isVisible = false
                    }
                }
            }

            stopBotButton -> {
                chatReader.stopReading()
                if (settings.useOfficialTsClient) {
                    commandRunner.runCommand("wmctrl -c TeamSpeak; sleep 2; wmctrl -c TeamSpeak", ignoreOutput = true)
                    commandRunner.runCommand(
                        "pkill mpv; pkill ncspot; tmux kill-session -t ncspot",
                        ignoreOutput = true
                    )
                } else {
                    teamSpeak.disconnect()
                }
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
        if (serverPortEditText.text.isNotEmpty()) serverPortEditText.text.toInt() else BotSettings().serverPort,
        serverPasswordEditText.text,
        channelNameEditText.text,
        channelPasswordEditText.text,
        channelFilePathEditText.text,
        nicknameEditText.text,
        marketEditText.text,
        when (spotifyPlayerRadioGroup.selectedToggle) {
            spotifyRadioButton -> "spotify"
            ncspotRadioButton -> "ncspot"
            spotifydRadioButton -> "spotifyd"
            else -> BotSettings().spotifyPlayer
        },
        spUsernameEditText.text,
        spPasswordEditText.text,
        tsClientRadioGroup.selectedToggle == tsClientOfficialRadioButton,
        scVolumeEditText.text.ifEmpty { "${BotSettings().scVolume}" }.toInt(),
        ytVolumeEditText.text.ifEmpty { "${BotSettings().ytVolume}" }.toInt()
    )


    override fun onChatUpdated(update: ChatUpdate) {
        if (commandList.commandList.any { update.message.startsWith(it.value) }) {
            val text = "\nUser ${update.userName} issued command \"${update.message}\"\n"
            println(text)
            updateStatus(text)
        } else {
            val text = "\nUser ${update.userName} said:\n${update.message}"
            println(text)
            updateStatus(text)
        }
    }

    override fun onCommandExecuted(command: String, output: String, extra: Any?) {
        val text = "\nCommand \"${command.substringBefore(" ")}\" has been executed.\nOutput:\n$output\n"
        println(text)
        updateStatus(text)
    }

    override fun onCommandProgress(command: String, output: String, extra: Any?) {
        val text = "\n\"${command.substringBefore(" ")}\" command progress update:\nOutput:\n$output\n"
        print(text)
        updateStatus(text)
    }
}

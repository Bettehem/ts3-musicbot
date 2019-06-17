package src.main

import javafx.application.Application
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import src.main.chat.ChatReader
import src.main.chat.ChatUpdate
import src.main.chat.ChatUpdateListener
import src.main.util.Console
import src.main.util.ConsoleUpdateListener
import java.io.File
import java.lang.Exception
import kotlin.system.exitProcess

var inputFilePath = ""
var statusTextView = Label()

class Main : Application(), EventHandler<ActionEvent>, ChatUpdateListener {

    private lateinit var window: Stage
    private lateinit var scene: Scene
    private var layout: VBox = VBox()


    private var enterChatPathTextView: Label = Label()
    private var chatPathEditText: TextField

    private var browseButton: Button
    private var readyButton: Button

    init {
        statusTextView = Label()
        chatPathEditText = TextField()
        browseButton = Button()
        readyButton = Button()
    }

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
                        "-n, --nickname         The nickname of the bot\n"

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

                    }
                }

                //connect to desired server and channel, after which find the server's channel file and start listening for commands
                if (apiKey.isNotEmpty() && serverAddress.isNotEmpty() && nickname.isNotEmpty()) {
                    println("Starting teamspeak3...")
                    println("Connecting to server at: $serverAddress, port ${if (serverPort.isNotEmpty()){
                        serverPort
                    } else {
                        "9987"
                    }}.")
                    println("Using $nickname as the bot\'s nickname.")
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "teamspeak3 -nosingleinstance \"ts3server://$serverAddress?port=${if (serverPort.isNotEmpty()){
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
                    }} &\""))
                } else {
                    println("Error!\nOptions -a, -s and -n are required. See -h or --help for more information")
                    exitProcess(0)
                }

                Thread.sleep(3000)
                //get the server's name
                File("/tmp/virtualserver_name_cmd").printWriter().use { out ->
                    out.println("#!/bin/sh")
                    out.println("(echo auth apikey=$apiKey; echo \"servervariable virtualserver_name\"; echo quit) | nc localhost 25639 > /tmp/virtualserver_name")
                }
                Thread.sleep(500)
                println("\n\nGetting server name...")
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "chmod +x /tmp/virtualserver_name_cmd"))
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "bash /tmp/virtualserver_name_cmd")).waitFor()
                Thread.sleep(500)
                var serverName = ""
                if (File("/tmp/virtualserver_name").exists()) {
                    for (line in File("/tmp/virtualserver_name").readLines()) {
                        if (line.contains("virtualserver_name")){
                            serverName = line.split("=".toRegex())[1]
                            println("Server name: $serverName")
                        }
                    }
                }

                //get a path to the channel.txt file
                println("\nGetting path to channel.txt file...")
                val chatDir = File("${System.getProperty("user.home")}/.ts3client/chats")
                println("Looking in \"$chatDir\" for chat files.")
                var channelFile = File("")
                for (dir in chatDir.list()) {
                    println("Checking in $dir")
                    val serverFile = File("${System.getProperty("user.home")}/.ts3client/chats/$dir/server.html")
                    for (line in serverFile.readLines()) {
                        if (line.contains("TextMessage_Connected") && line.contains("channelid://0")) {
                            //compare serverName to the one in server.html
                            if (line.split("channelid://0\">&quot;".toRegex())[1].split("&quot;".toRegex())[0] == serverName) {
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

                if (channelFile.exists()) {
                    val chatReader = ChatReader(channelFile, object : ChatUpdateListener {
                        override fun onChatUpdated(update: ChatUpdate) {
                            if (update.message.startsWith("%"))
                                println("\nUser ${update.userName} issued command \"${update.message}\"")
                        }
                    }, apiKey)
                    chatReader.startReading()
                    println("Bot $nickname started listening to the chat in channel $channelName.")

                    val console = Console(object : ConsoleUpdateListener {
                        override fun onCommandIssued(command: String) {
                            if (command.startsWith("%"))
                                chatReader.parseLine("__console__", command)
                        }
                    })
                    console.startConsole()
                }
            } else {
                //launch graphical window
                launch(Main::class.java, *args)
            }

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

        }
    }

    private fun variables() {
        enterChatPathTextView.text = "Enter path to chat file (channel.html or channel.txt)"
        statusTextView.text = "Not Connected."

        browseButton.text = "Browse File"
        browseButton.onAction = this

        readyButton.text = "Ready"
        readyButton.onAction = this
    }

    private fun ui() {
        //prepare layout
        layout.spacing = 10.0
        layout.children.addAll(enterChatPathTextView, chatPathEditText, browseButton, readyButton, statusTextView)

        //build scene
        scene = Scene(layout, 640.0, 360.0)

        //setup window
        window.title = "TeamSpeak3 Music bot"
        window.scene = scene
    }

    override fun handle(p0: ActionEvent?) {
        when (p0?.source) {
            browseButton -> {
                val fileChooser = FileChooser()
                fileChooser.title = "Select TeamSpeak chat file. (channel.html or channel.txt)"
                fileChooser.initialDirectory = File("${System.getProperty("user.home")}/.ts3client/chats")
                fileChooser.selectedExtensionFilter = FileChooser.ExtensionFilter("Chat File", listOf("*.html", "*.txt"))

                val file = fileChooser.showOpenDialog(window)
                inputFilePath = file.absolutePath
                chatPathEditText.text = inputFilePath
            }

            readyButton -> {
                val chatReader = ChatReader(File(inputFilePath), this)
                chatReader.startReading()
                statusTextView.text = "Connected."
            }
        }
    }

    override fun onChatUpdated(update: ChatUpdate) {
        //statusTextView.text = statusTextView.text.split(" ".toRegex())[0] + "\n\n\n\nStatus:\n\nTime: ${update.time}\nUser: ${update.userName}\nMessage: ${update.message}"
    }
}

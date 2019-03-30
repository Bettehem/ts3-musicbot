package main

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
import main.chat.ChatReader
import main.chat.ChatUpdate
import main.chat.ChatUpdateListener
import java.io.File
import java.lang.Exception
import kotlin.system.exitProcess

var inputFilePath = ""
var statusTextView = Label()

class Main : Application(), EventHandler<ActionEvent>, ChatUpdateListener {

    lateinit var window: Stage
    lateinit var scene: Scene
    var layout: VBox


    var enterChatPathTextView: Label
    var chatPathEditText: TextField

    var browseButton: Button
    var readyButton: Button

    init {
        layout = VBox()
        enterChatPathTextView = Label()
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

                val helpMessage = "\n" +
                        "TS3 Music Bot help message\n" +
                        "Available options:\n" +
                        "-h, --help             Show this help message\n" +
                        "-a, --apikey           ClientQuery api key\n" +
                        "-s, --serveraddress    Server address to connect to\n" +
                        "-p, --serverport       Server's port. Usually 9987\n" +
                        "-P, --serverpassword   Server's password\n" +
                        "-c, --channelname      The channel's name the bot should connect to after connecting to the server\n" +
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
                        "-n", "--nickname" -> {
                            if (args.size >= argPos + 1)
                                nickname = args[argPos + 1]
                        }

                    }
                }

                //connect to desired server and channel, after which find the server's channel file and start listening for commands
                if (apiKey.isNotEmpty() && serverAddress.isNotEmpty() && serverPort.isNotEmpty() && nickname.isNotEmpty()) {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "teamspeak3 \"ts3server://$serverAddress?port=$serverPort&nickname=${nickname.replace(" ", "%20")}&${if ((serverPassword.isNotEmpty())) {
                        "password=$serverPassword"
                    } else {
                        ""
                    }}&${if (channelName.isNotEmpty()) {
                        "channel=${channelName.replace(" ", "%20")}"
                    } else {
                        ""
                    }}\""))
                } else {
                    println("Error!\nOptions -a, -s, -P and -n are required. See -h or --help for more information")
                }
                Thread.sleep(5000)
                File("/tmp/virtualserver_name_cmd").printWriter().use { out ->
                    out.println("#!/bin/sh")
                    out.println("(echo auth apikey=$apiKey; echo \"servervariable virtualserver_name\"; echo quit) | nc localhost 25639 > /tmp/virtualserver_name")
                }
                //get the server's name
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "chmod +x /tmp/virtualserver_name_cmd"))
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "bash /tmp/virtualserver_name_cmd"))
                var serverName = ""
                for (line in File("/tmp/virtualserver_name").readLines()) {
                    if (line.startsWith("virtualserver_name"))
                        serverName = line.split("=".toRegex())[1]
                }

                //get a path to the channel.html file
                val chatDir = File("${System.getProperty("user.home")}/.ts3client/chats")
                var channelFile = File("")
                for (dir in chatDir.list()) {
                    val serverFile = File("${System.getProperty("user.home")}/.ts3client/chats/$dir/server.html")
                    for (line in serverFile.readLines()) {
                        if (line.contains("TextMessage_Connected") && line.contains("channelid://0")) {
                            //compare serverName to the one in server.html
                            if (line.split("channelid://0\">&quot;".toRegex())[1].split("&quot;".toRegex())[0] == serverName){
                                channelFile = File("${System.getProperty("user.home")}/.ts3client/chats/$dir/channel.html")
                                break
                            }
                        }
                    }
                }

                if (channelFile.exists()) {
                    val chatReader = ChatReader(channelFile, object : ChatUpdateListener {
                        override fun onChatUpdated(update: ChatUpdate) {
                            //TODO do something when chat is updated
                        }
                    }, apiKey)
                    chatReader.startReading()
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

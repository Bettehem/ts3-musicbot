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

            //check if input file is provided
            if (args.size == 4) {
                //check first argument
                when (args[0]) {
                    //if an input file path is provided
                    "-i" -> {
                        inputFilePath = args[1]
                    }

                }

            } else {
                //launch graphical window
                launch(Main::class.java, *args)
            }

        }
    }


    override fun start(p0: Stage?) {
        try {
            window = p0!!
            variables()
            ui()
            window.show()
        } catch (e: Exception) {

        }
    }

    fun variables() {
        enterChatPathTextView.text = "Enter path to chat file (channel.html)"
        statusTextView.text = "Not Connected."

        browseButton.text = "Browse File"
        browseButton.onAction = this

        readyButton.text = "Ready"
        readyButton.onAction = this
    }

    fun ui() {
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
                fileChooser.title = "Select TeamSpeak chat file. (channel.html)"
                fileChooser.initialDirectory = File("${System.getProperty("user.home")}/.ts3client/chats")
                fileChooser.selectedExtensionFilter = FileChooser.ExtensionFilter("Chat File", listOf("*.html"))

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

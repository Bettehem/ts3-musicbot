package src.main.util

import java.io.BufferedReader
import java.io.InputStreamReader

fun runCommand(command: String, ignoreOutput: Boolean = false, printOutput: Boolean = true, printErrors: Boolean = true, inheritIO: Boolean = false): String{
    val commandOutput = StringBuilder()

    val commands = listOf("sh", "-c", command)
    val processBuilder = ProcessBuilder(commands)
    val process: Process
    if (inheritIO) {
        process = processBuilder.inheritIO().start()
        process.waitFor()
    }else{
        process = processBuilder.start()
    }


    if (!ignoreOutput){
        val stdOut = BufferedReader(InputStreamReader(process.inputStream))
        val stdErr = BufferedReader(InputStreamReader(process.errorStream))

        var output = stdOut.readLine()
        while (output != null){
            if (printOutput){
                println(output)
                commandOutput.append("$output\n")
            }
            output = stdOut.readLine()
        }

        output = stdErr.readLine()
        while (output != null){
            if (printErrors){
                println(output)
                commandOutput.append("$output\n")
            }
            output = stdErr.readLine()
        }
    }
    return commandOutput.toString().substringBeforeLast("\n")
}
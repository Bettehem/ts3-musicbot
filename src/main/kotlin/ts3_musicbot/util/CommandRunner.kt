package ts3_musicbot.util

import java.io.BufferedReader
import java.io.InputStreamReader

data class Output(val outputText: String) {
    override fun toString() = outputText
}

data class Error(val errorText: String) {
    override fun toString() = errorText
}

class CommandRunner {
    /**
     * Run a command in the system's shell.
     * @param command The command you want to run
     * @param ignoreOutput Ignore all output from the command
     * @param printOutput Print output of the command in to stdout
     * @param printErrors Print errors of the command in to stdout
     * @param inheritIO InheritIO lets you properly use interactive commands
     * @return Returns a Pair which contains an Output and an Error.
     */
    fun runCommand(
        command: String,
        ignoreOutput: Boolean = false,
        printOutput: Boolean = true,
        printErrors: Boolean = true,
        inheritIO: Boolean = false,
        printCommand: Boolean = false,
    ): Pair<Output, Error> {
        val commandOutput = StringBuilder()
        val errorOutput = StringBuilder()

        val commands = listOf("sh", "-c", command)
        val processBuilder = ProcessBuilder(commands)

        if (printCommand)
            println("Running command \"$command\"")
        val process: Process
        if (inheritIO) {
            process = processBuilder.inheritIO().start()
            process.waitFor()
        } else {
            process = processBuilder.start()
        }


        if (!ignoreOutput) {
            val stdOut = BufferedReader(InputStreamReader(process.inputStream))
            val stdErr = BufferedReader(InputStreamReader(process.errorStream))

            var output = stdOut.readLine()
            while (output != null) {
                commandOutput.append("$output\n")
                if (printOutput) {
                    println(output)
                }
                output = stdOut.readLine()
            }

            var error = stdErr.readLine()
            while (error != null) {
                errorOutput.append("$error\n")
                if (printErrors) {
                    println(error)
                }
                error = stdErr.readLine()
            }
        }
        return Pair(
            Output(commandOutput.toString().substringBeforeLast("\n")),
            Error(errorOutput.toString().substringBeforeLast("\n"))
        )
    }
}

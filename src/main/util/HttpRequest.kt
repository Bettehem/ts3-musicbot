package src.main.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

fun sendHttpRequest(
    url: URL,
    requestMethod: String,
    requestProperties: Array<String>,
    postData: Array<String> = emptyArray()
): String {
    val readyOutput = StringBuilder()

    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = requestMethod
    for (property in requestProperties) {
        val p = property.split(": ".toRegex())
        connection.setRequestProperty(p[0], p[1])
    }
    if (connection.requestMethod == "POST") {
        connection.doOutput = true
        val outputStream = connection.outputStream
        for (data in postData) {
            outputStream.write(data.toByteArray())
        }
        outputStream.flush()
        outputStream.close()
    }
    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        val bufferedReader = BufferedReader(InputStreamReader(connection.inputStream))
        var output = bufferedReader.readLine()
        while (output != null) {
            readyOutput.append(output)
            output = bufferedReader.readLine()
        }
    } else {
        println("\n\n\nError! Http request failed at\n$url\n")
        println("Bad response! Code ${connection.responseCode}")
        println("Response message = ${connection.responseMessage}")
    }

    return readyOutput.toString()
}
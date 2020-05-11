package src.main.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Send http request
 * @param url target URL of the request
 * @param requestMethod Request method for the http request. Can be GET or POST
 * @param requestProperties Properties of the request.
 * @param postData Data of the request.
 * @return returns a result of the http request. First part of the pair contains the request's response code and
 * the second part contains possible data that was returned with the request.
 */
fun sendHttpRequest(
    url: URL,
    requestMethod: String,
    requestProperties: Array<String>,
    postData: Array<String> = emptyArray()
): Pair<Int, String> {
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
    return Pair(
        connection.responseCode, when (connection.responseCode) {
            HttpURLConnection.HTTP_OK -> {
                val bufferedReader = BufferedReader(InputStreamReader(connection.inputStream))
                var output = bufferedReader.readLine()
                val readyOutput = StringBuilder()
                while (output != null) {
                    readyOutput.append(output)
                    output = bufferedReader.readLine()
                }
                readyOutput.toString()
            }
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                "Unauthorized"
            }
            else -> {
                println("\n\n\nError! Http request failed at\n$url\n")
                println("Bad response! Code ${connection.responseCode}")
                println("Response message = ${connection.responseMessage}")
                ""
            }
        }
    )
}
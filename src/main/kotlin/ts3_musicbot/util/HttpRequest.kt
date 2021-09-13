package ts3_musicbot.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class RequestMethod(val method: String)
data class DefaultProperties(val properties: List<String>)
data class ExtraProperties(val properties: List<String>)
data class PostData(val data: List<String>)
data class ResponseCode(val code: Int)
data class ResponseData(val data: String)
data class Response(val code: ResponseCode, val data: ResponseData, val url: URL)

const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Send http request
 * @param url target URL of the request
 * @param requestMethod Request method for the http request. Can be GET or POST
 * @param defaultProperties Default Properties of the request.
 * @param extraProperties Extra properties of the request.
 * @param postData Data of the request.
 * @return returns a result of the http request. First part of the pair contains the request's response code and
 * the second part contains possible data that was returned with the request.
 */
fun sendHttpRequest(
    url: URL,
    requestMethod: RequestMethod,
    extraProperties: ExtraProperties = ExtraProperties(emptyList()),
    postData: PostData = PostData(emptyList()),
    defaultProperties: DefaultProperties = DefaultProperties(
        listOf(
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
    )
): Response{
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = requestMethod.method
    for (property in defaultProperties.properties) {
        val p = property.split(": ".toRegex())
        connection.setRequestProperty(p[0], p[1])
    }
    for (property in extraProperties.properties) {
        val p = property.split(":".toRegex())
        connection.setRequestProperty(p[0], p[1])
    }
    if (connection.requestMethod == "POST") {
        connection.doOutput = true
        val outputStream = connection.outputStream
        for (data in postData.data) {
            outputStream.write(data.toByteArray())
        }
        outputStream.flush()
        outputStream.close()
    }
    return Response(
        ResponseCode(connection.responseCode), ResponseData(
            when (connection.responseCode) {
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

                //Code 429 stands for TOO_MANY_REQUESTS
                HTTP_TOO_MANY_REQUESTS -> {
                    connection.getHeaderField("Retry-After")
                }
                else -> {
                    println("\n\n\nError! Http request failed at\n$url\n")
                    println("Bad response! Code ${connection.responseCode}")
                    println("Response message = ${connection.responseMessage}")
                    ""
                }
            }
        ),
        url
    )

}


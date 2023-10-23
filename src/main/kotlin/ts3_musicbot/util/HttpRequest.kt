package ts3_musicbot.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

enum class RequestMethod {
    POST,
    GET
}
data class DefaultProperties(val properties: List<String>)
data class ExtraProperties(val properties: List<String>)
data class PostData(val data: List<String>)
data class ResponseCode(val code: Int) {
    override fun toString() = "$code"
}
data class ResponseData(val data: String)
data class Response(val code: ResponseCode, val data: ResponseData, val link: Link)

const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Send http request
 * @param link target URL of the request
 * @param requestMethod Request method for the http request. Can be GET or POST
 * @param extraProperties Extra properties of the request.
 * @param postData Data of the request.
 * @param defaultProperties Default Properties of the request.
 * @param followRedirects Should follow url redirects.
 * @return returns a Response.
 */
fun sendHttpRequest(
    link: Link,
    requestMethod: RequestMethod = RequestMethod.GET,
    extraProperties: ExtraProperties = ExtraProperties(emptyList()),
    postData: PostData = PostData(emptyList()),
    defaultProperties: DefaultProperties = DefaultProperties(
        listOf(
            "Content-Type: application/x-www-form-urlencoded",
            "User-Agent: Mozilla/5.0"
        )
    ),
    followRedirects: Boolean = true,
): Response {
    val connection = URL(link.link).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = followRedirects
    connection.requestMethod = requestMethod.name
    var responseData = ResponseData("")
    var responseCode = ResponseCode(0)
    val responseLink = if (followRedirects) {
        for (property in defaultProperties.properties) {
            val p = property.split(": ".toRegex())
            connection.setRequestProperty(p[0], p[1])
        }
        for (property in extraProperties.properties) {
            val p = property.split(":".toRegex())
            connection.setRequestProperty(p[0], p[1])
        }
        if (connection.requestMethod == RequestMethod.POST.name) {
            connection.doOutput = true
            try {
                val outputStream = connection.outputStream
                for (data in postData.data) {
                    outputStream.write(data.toByteArray())
                }
                outputStream.flush()
                outputStream.close()
            } catch (e: java.net.SocketException) {
                println("HTTP request failed due to exception!")
                e.printStackTrace()
                responseCode = ResponseCode(400)
            }
        }
        responseData = ResponseData(
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val bufferedReader = BufferedReader(InputStreamReader(connection.inputStream))
                    var output = bufferedReader.readLine()
                    val readyOutput = StringBuilder()
                    while (output != null) {
                        readyOutput.appendLine(output)
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
                    println("\n\n\nHTTP $requestMethod request to $link")
                    println("Response Code: ${connection.responseCode}")
                    println("Response message: ${connection.responseMessage}")
                    ""
                }
            }
        )
        Link(connection.url.toString())
    } else {
        Link(connection.getHeaderField("Location"))
    }
    if (responseCode.code == 0)
        responseCode = ResponseCode(connection.responseCode)
    println("\nRequest URL: $link\nRequest Method: $requestMethod\nResponse URL: $responseLink\nResponse Code: $responseCode\n")
    connection.disconnect()
    return Response(responseCode, responseData, responseLink)
}


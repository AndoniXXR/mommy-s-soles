package com.e621.client.data.api

import java.io.IOException

/**
 * Custom exceptions for API error handling
 */

/**
 * Thrown when CloudFlare blocks the request (403, 503 with CF headers)
 */
class CloudFlareException(
    message: String = "Request blocked by CloudFlare",
    val httpCode: Int = 403
) : IOException(message)

/**
 * Thrown when the server is down or returns empty data
 */
class ServerDownException(
    message: String = "Server did not return any data"
) : IOException(message)

/**
 * Thrown when authentication fails (401)
 */
class AuthenticationException(
    message: String = "Authentication failed",
    val httpCode: Int = 401
) : IOException(message)

/**
 * Thrown for general network errors with descriptive messages
 */
class NetworkException(
    message: String,
    val type: NetworkErrorType,
    cause: Throwable? = null
) : IOException(message, cause)

enum class NetworkErrorType {
    TIMEOUT,
    NO_INTERNET,
    DNS_ERROR,
    CONNECTION_REFUSED,
    UNKNOWN
}

/**
 * Helper object to create appropriate exceptions from HTTP response codes
 */
object ApiErrorHandler {
    
    fun getExceptionForHttpCode(code: Int, message: String? = null): Exception {
        return when (code) {
            401 -> AuthenticationException(message ?: "Invalid credentials or API key")
            403 -> CloudFlareException(message ?: "Access denied - CloudFlare protection", code)
            503 -> CloudFlareException(message ?: "Service unavailable - CloudFlare protection", code)
            500 -> ServerDownException(message ?: "Internal server error")
            502 -> ServerDownException(message ?: "Bad gateway - server may be down")
            504 -> ServerDownException(message ?: "Gateway timeout - server not responding")
            else -> IOException(message ?: "HTTP Error $code")
        }
    }
    
    fun getExceptionForThrowable(throwable: Throwable): NetworkException {
        return when (throwable) {
            is java.net.SocketTimeoutException -> NetworkException(
                "Connection timed out. Please check your internet connection.",
                NetworkErrorType.TIMEOUT,
                throwable
            )
            is java.net.UnknownHostException -> NetworkException(
                "Cannot reach server. Please check your internet connection.",
                NetworkErrorType.NO_INTERNET,
                throwable
            )
            is java.net.ConnectException -> NetworkException(
                "Connection refused. The server may be down.",
                NetworkErrorType.CONNECTION_REFUSED,
                throwable
            )
            is javax.net.ssl.SSLException -> NetworkException(
                "Secure connection failed. Please try again.",
                NetworkErrorType.UNKNOWN,
                throwable
            )
            else -> NetworkException(
                throwable.message ?: "Network error occurred",
                NetworkErrorType.UNKNOWN,
                throwable
            )
        }
    }
    
    /**
     * Get user-friendly error message for display
     */
    fun getUserFriendlyMessage(exception: Throwable): String {
        return when (exception) {
            is CloudFlareException -> "Access blocked by CloudFlare protection. Please try again later or check if the website is accessible in a browser."
            is ServerDownException -> "The server is not responding. Please check if e621.net is accessible and try again."
            is AuthenticationException -> "Authentication failed. Please check your username and API key in Settings."
            is NetworkException -> exception.message ?: "Network error occurred"
            is java.net.SocketTimeoutException -> "Connection timed out. Please check your internet connection."
            is java.net.UnknownHostException -> "Cannot reach server. Please check your internet connection."
            else -> exception.message ?: "An error occurred"
        }
    }
}

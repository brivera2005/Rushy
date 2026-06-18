package com.streamvault.data.remote.xtream

import java.io.IOException

sealed class XtreamApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class XtreamNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

class XtreamAuthenticationException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : XtreamApiException(message, cause)

class XtreamParsingException(message: String, cause: Throwable? = null) : XtreamApiException(message, cause)

class XtreamRequestException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : XtreamApiException(message, cause)

class XtreamResponseTooLargeException(
    val hint: String,
    val observedBytes: Long,
    val maxAllowedBytes: Long,
    cause: Throwable? = null
) : XtreamApiException(
    "Response from $hint exceeded safe in-memory budget (${observedBytes}B > ${maxAllowedBytes}B)",
    cause
)

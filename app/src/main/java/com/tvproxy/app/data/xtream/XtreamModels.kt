package com.tvproxy.app.data.xtream

import com.squareup.moshi.JsonDataException
import java.io.IOException

/** Connection parameters for one Xtream Codes portal. */
data class XtreamCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
)

/**
 * Non-throwing result of an Xtream API call. Repositories map the failures to
 * `ImportProgress.Failed` (imports keep previous data by design).
 */
sealed interface XtreamResponse<out T> {
    data class Ok<T>(val value: T) : XtreamResponse<T>
    data class Http(val code: Int, val message: String) : XtreamResponse<Nothing>
    data class Network(val cause: IOException) : XtreamResponse<Nothing>
    data class Malformed(val cause: JsonDataException) : XtreamResponse<Nothing>
}

// Xtream panels are notoriously type-loose: ids and counters arrive either as JSON
// numbers or as strings ("stream_id": 42 vs "stream_id": "42"). DTOs declare those
// fields as Any? and convert through these helpers at the repository boundary.
fun Any?.asLongOrNull(): Long? = when (this) {
    null -> null
    is Number -> toLong()
    is String -> trim().toLongOrNull()
    else -> null
}

fun Any?.asIntOrNull(): Int? = asLongOrNull()?.toInt()

fun Any?.asDoubleOrNull(): Double? = when (this) {
    null -> null
    is Number -> toDouble()
    is String -> trim().toDoubleOrNull()
    else -> null
}

fun Any?.asStringOrNull(): String? = when (this) {
    null -> null
    is String -> trim().ifBlank { null }
    else -> toString()
}

/** Xtream boolean flags arrive as 0/1 ints or "0"/"1" strings. */
fun Any?.asBooleanFlag(): Boolean = asLongOrNull()?.let { it != 0L } ?: false

package dk.betterlectio.android.core.lectio.model

import okhttp3.HttpUrl

data class LectioResponse(
    val body: String,
    val bytes: ByteArray,
    val finalUrl: HttpUrl,
    val statusCode: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LectioResponse) return false
        return body == other.body &&
            bytes.contentEquals(other.bytes) &&
            finalUrl == other.finalUrl &&
            statusCode == other.statusCode
    }

    override fun hashCode(): Int {
        var result = body.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + finalUrl.hashCode()
        result = 31 * result + statusCode
        return result
    }
}

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun main() {
    val padded = "SGVsbG8="
    val unpadded = "SGVsbG8"
    try {
        println("Padded: " + Base64.decode(padded).decodeToString())
    } catch (e: Exception) {
        println("Padded failed: " + e.message)
    }
    try {
        println("Unpadded: " + Base64.decode(unpadded).decodeToString())
    } catch (e: Exception) {
        println("Unpadded failed: " + e.message)
    }
}

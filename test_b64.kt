import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun main() {
    val b = Base64.encode(byteArrayOf(1,2,3))
    println(b)
}

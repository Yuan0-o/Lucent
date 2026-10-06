import okio.Buffer
import java.io.OutputStream
import java.io.InputStream

fun main() {
    val buf = Buffer()
    val out: OutputStream = buf.outputStream()
    val ins: InputStream = buf.inputStream()
    println("Works")
}

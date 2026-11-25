package com.example.cloud

import android.os.SystemClock
import java.io.File
import java.io.FileInputStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (bytesWritten: Long, totalBytes: Long, bytesPerSecond: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        val buffer = ByteArray(16 * 1024)
        var uploaded = 0L
        val startMs = SystemClock.elapsedRealtime()
        var lastEmitMs = 0L

        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read

                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastEmitMs >= 100L || uploaded == total) {
                    val elapsedSec = ((nowMs - startMs).coerceAtLeast(1L)) / 1000.0
                    val speed = (uploaded / elapsedSec).toLong()
                    onProgress(uploaded, total, speed)
                    lastEmitMs = nowMs
                }
            }
        }
    }
}

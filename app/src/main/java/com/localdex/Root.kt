package com.localdex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

object Root {

    data class Result(val code: Int, val output: String) {
        val ok: Boolean get() = code == 0
    }

    suspend fun exec(command: String, timeoutSeconds: Long = 10): Result =
        withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@withContext Result(-1, "Timed out: $command")
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                Result(process.exitValue(), output)
            } catch (e: IOException) {
                Result(-1, e.message ?: "su not available")
            }
        }

    suspend fun isAvailable(): Boolean {
        val result = exec("id -u")
        return result.ok && result.output == "0"
    }
}

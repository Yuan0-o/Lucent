package com.lucent.app.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

object WindowsHello {

    enum class Availability { AVAILABLE, UNAVAILABLE }

    enum class Result {
        VERIFIED,

        CANCELED,

        FAILED,

        UNAVAILABLE
    }

    @Volatile
    private var cached: Availability? = null

    private val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    suspend fun availability(): Availability {
        cached?.let { return it }
        val result = if (!isWindows) Availability.UNAVAILABLE else probeAvailability()
        cached = result
        return result
    }

    suspend fun refresh(): Availability {
        cached = null
        return availability()
    }

    suspend fun verify(reason: String): Result {
        if (!isWindows || availability() != Availability.AVAILABLE) return Result.UNAVAILABLE
        return runVerification(reason)
    }


    private suspend fun probeAvailability(): Availability = withContext(Dispatchers.IO) {
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            try {
              $WINRT_AWAIT_PRELUDE
              [void][Windows.Security.Credentials.UI.UserConsentVerifier,Windows.Security.Credentials.UI,ContentType=WindowsRuntime]
              ${'$'}availType = [Windows.Security.Credentials.UI.UserConsentVerifierAvailability]
              ${'$'}a = Await ([Windows.Security.Credentials.UI.UserConsentVerifier]::CheckAvailabilityAsync()) ${'$'}availType
              if (${'$'}a -eq [Windows.Security.Credentials.UI.UserConsentVerifierAvailability]::Available) {
                Write-Output 'AVAILABLE'
              } else {
                Write-Output 'UNAVAILABLE'
              }
            } catch { Write-Output 'UNAVAILABLE' }
        """.trimIndent()

        when (runPowerShell(script, timeoutSeconds = 20)?.trim()) {
            "AVAILABLE" -> Availability.AVAILABLE
            else -> Availability.UNAVAILABLE
        }
    }

    private suspend fun runVerification(reason: String): Result = withContext(Dispatchers.IO) {
        val safeReason = reason.replace("'", "''").replace("\r", " ").replace("\n", " ")
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            try {
              $WINRT_AWAIT_PRELUDE
              [void][Windows.Security.Credentials.UI.UserConsentVerifier,Windows.Security.Credentials.UI,ContentType=WindowsRuntime]
              ${'$'}resType = [Windows.Security.Credentials.UI.UserConsentVerificationResult]
              ${'$'}r = Await ([Windows.Security.Credentials.UI.UserConsentVerifier]::RequestVerificationAsync('$safeReason')) ${'$'}resType
              if (${'$'}r -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::Verified) {
                Write-Output 'VERIFIED'
              } elseif (${'$'}r -eq [Windows.Security.Credentials.UI.UserConsentVerificationResult]::Canceled) {
                Write-Output 'CANCELED'
              } else {
                Write-Output 'FAILED'
              }
            } catch { Write-Output 'FAILED' }
        """.trimIndent()

        when (runPowerShell(script, timeoutSeconds = 120)?.trim()) {
            "VERIFIED" -> Result.VERIFIED
            "CANCELED" -> Result.CANCELED
            "FAILED" -> Result.FAILED
            else -> Result.UNAVAILABLE
        }
    }

    private fun runPowerShell(script: String, timeoutSeconds: Long): String? {
        return try {
            val encoded = Base64.getEncoder()
                .encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encoded
            ).redirectErrorStream(false).start()

            val collected = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { r ->
                        collected.append(r.readText())
                    }
                } catch (_: Throwable) {
                }
            }.apply { isDaemon = true; start() }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(2000)
                return null
            }
            reader.join(2000)
            if (process.exitValue() != 0) null else collected.toString()
        } catch (t: Throwable) {
            null
        }
    }

    private const val WINRT_AWAIT_PRELUDE = """
              Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
              ${'$'}asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
              } | Select-Object -First 1)
              function Await(${'$'}op, ${'$'}resultType) {
                ${'$'}m = ${'$'}asTaskGeneric.MakeGenericMethod(${'$'}resultType)
                ${'$'}t = ${'$'}m.Invoke(${'$'}null, @(${'$'}op))
                ${'$'}t.Wait(-1) | Out-Null
                return ${'$'}t.Result
              }
    """
}

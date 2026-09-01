package com.gitofy.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.gitofy.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Production-safe runtime integrity layer.
 *
 * This intentionally uses documented Android/JVM APIs and fails closed by
 * disabling sensitive operations rather than corrupting memory, crashing a
 * decompiler, or killing the process.
 */
object IntegritySecurityEngine {
    enum class Threat {
        NONE,
        DEBUGGER,
        TRACER,
        HOOK_RUNTIME,
        ROOTED_ENVIRONMENT,
        EMULATOR,
        SIGNATURE_MISMATCH,
        DEX_MISMATCH,
        PACKAGE_IDENTITY_MISMATCH,
        APP_LABEL_MISMATCH,
        ICON_REFERENCE_MISMATCH,
        RESOURCE_MISMATCH,
        MANIFEST_MISMATCH,
        RESOURCES_ARSC_MISMATCH
    }

    data class Status(
        val trusted: Boolean,
        val threats: Set<Threat>,
        val certificateSha256: String,
        val dexSha256: String,
        val manifestSha256: String = "",
        val resourcesArscSha256: String = ""
    )

    private val current = AtomicReference(
        Status(true, emptySet(), "", "")
    )
    private var watchdogJob: Job? = null

    private val suspiciousMapTokens = listOf(
        "frida-agent", "frida-gadget", "libfrida", "xposed", "lsposed",
        "edxposed", "substrate", "zygisk", "riru"
    )

    fun start(context: Context, scope: CoroutineScope): Job {
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                current.set(check(context))
                delay(5_000)
            }
        }
        return watchdogJob!!
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun status(): Status = current.get()

    fun isTrusted(): Boolean = current.get().trusted

    /** Sensitive write operations are blocked only for direct tampering/debug/hook
     * indicators. Root/emulator status is reported but does not block normal use. */
    fun sensitiveOperationsAllowed(): Boolean = current.get().threats.none {
        it == Threat.DEBUGGER ||
            it == Threat.TRACER ||
            it == Threat.HOOK_RUNTIME ||
            it == Threat.SIGNATURE_MISMATCH ||
            it == Threat.DEX_MISMATCH ||
            it == Threat.PACKAGE_IDENTITY_MISMATCH ||
            it == Threat.APP_LABEL_MISMATCH ||
            it == Threat.ICON_REFERENCE_MISMATCH ||
            it == Threat.RESOURCE_MISMATCH ||
            it == Threat.MANIFEST_MISMATCH ||
            it == Threat.RESOURCES_ARSC_MISMATCH
    }

    fun check(context: Context): Status {
        val threats = linkedSetOf<Threat>()

        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            threats += Threat.DEBUGGER
        }
        if (readTracerPid() > 0) {
            threats += Threat.TRACER
        }
        if (mapsContainSuspiciousRuntime() || NativeSecurity.environmentFlags() != 0) {
            threats += Threat.HOOK_RUNTIME
        }
        if (looksRooted()) {
            threats += Threat.ROOTED_ENVIRONMENT
        }
        if (looksEmulated()) {
            threats += Threat.EMULATOR
        }

        val cert = signingCertificateSha256(context)
        val expectedCert = BuildConfig.SECURITY_EXPECTED_CERT_SHA256.trim().uppercase(Locale.US)
        if (BuildConfig.SECURITY_ENFORCE_RELEASE && expectedCert.isNotEmpty() && cert != expectedCert) {
            threats += Threat.SIGNATURE_MISMATCH
        }

        val dex = sha256ApkEntry(context, "classes.dex")
        val expectedDex = BuildConfig.SECURITY_EXPECTED_DEX_SHA256.trim().uppercase(Locale.US)
        if (BuildConfig.SECURITY_ENFORCE_RELEASE && expectedDex.isNotEmpty() && dex != expectedDex) {
            threats += Threat.DEX_MISMATCH
        }

        val resources = ResourceIntegrityEngine.check(context)
        if (!resources.packageOk) threats += Threat.PACKAGE_IDENTITY_MISMATCH
        if (!resources.labelOk) threats += Threat.APP_LABEL_MISMATCH
        if (!resources.iconReferenceOk) threats += Threat.ICON_REFERENCE_MISMATCH
        if (resources.resourceFailures.isNotEmpty()) threats += Threat.RESOURCE_MISMATCH
        if (BuildConfig.SECURITY_ENFORCE_RELEASE &&
            BuildConfig.SECURITY_EXPECTED_MANIFEST_SHA256.isNotBlank() &&
            resources.manifestSha256 != BuildConfig.SECURITY_EXPECTED_MANIFEST_SHA256.trim().uppercase(Locale.US)) {
            threats += Threat.MANIFEST_MISMATCH
        }
        if (BuildConfig.SECURITY_ENFORCE_RELEASE &&
            BuildConfig.SECURITY_EXPECTED_RESOURCES_ARSC_SHA256.isNotBlank() &&
            resources.resourcesArscSha256 != BuildConfig.SECURITY_EXPECTED_RESOURCES_ARSC_SHA256.trim().uppercase(Locale.US)) {
            threats += Threat.RESOURCES_ARSC_MISMATCH
        }

        return Status(
            trusted = threats.isEmpty(),
            threats = threats,
            certificateSha256 = cert,
            dexSha256 = dex,
            manifestSha256 = resources.manifestSha256,
            resourcesArscSha256 = resources.resourcesArscSha256
        )
    }

    private fun readTracerPid(): Int {
        return runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("TracerPid:") }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
            }
        }.getOrDefault(0)
    }

    private fun mapsContainSuspiciousRuntime(): Boolean {
        return runCatching {
            File("/proc/self/maps").useLines { lines ->
                lines.any { line ->
                    val lower = line.lowercase(Locale.US)
                    suspiciousMapTokens.any(lower::contains)
                }
            }
        }.getOrDefault(false)
    }

    private fun looksRooted(): Boolean {
        val binaries = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/bin/magisk", "/system/xbin/ksu", "/system/bin/ksud",
            "/data/adb/magisk", "/data/adb/ksud"
        )
        if (binaries.any { File(it).exists() }) return true
        return Build.TAGS?.contains("test-keys", ignoreCase = true) == true
    }

    private fun looksEmulated(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            product.contains("sdk")
    }

    private fun signingCertificateSha256(context: Context): String {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.toList().orEmpty()
            }
            signatures.firstOrNull()?.toByteArray()?.sha256Hex().orEmpty()
        }.getOrDefault("")
    }

    private fun sha256ApkEntry(context: Context, entryName: String): String {
        return runCatching {
            context.assets.openFd(entryName).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    MessageDigest.getInstance("SHA-256").digestStream(input).toHex()
                }
            }
        }.getOrElse {
            runCatching {
                java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { zip ->
                    zip.getInputStream(zip.getEntry(entryName)).use { input ->
                        MessageDigest.getInstance("SHA-256").digestStream(input).toHex()
                    }
                }
            }.getOrDefault("")
        }
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toHex()

    private fun MessageDigest.digestStream(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) update(buffer, 0, read)
        }
        return digest()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}

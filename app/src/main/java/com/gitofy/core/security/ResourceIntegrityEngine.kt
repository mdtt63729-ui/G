package com.gitofy.core.security

import android.content.Context
import android.content.pm.PackageManager
import com.gitofy.BuildConfig
import com.gitofy.R
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Release resource/package identity verifier.
 *
 * The verifier reads the installed APK as a ZIP container and validates the
 * bytes of security-critical visual resources. Manifest/resources.arsc pins
 * are supplied by CI after a trusted release APK is produced.
 *
 * No memory corruption or malformed-bytecode tricks are used. A mismatch is
 * surfaced as a hard integrity failure to the application security layer.
 */
object ResourceIntegrityEngine {
    private const val EXPECTED_PACKAGE = "com.gitofy"
    private const val EXPECTED_LABEL = "GITOFY"

    // SHA-256 of the exact release source assets currently shipped by Gitofy.
    // FIX (splash never progresses on release builds): the mipmap launcher
    // icons were regenerated at some point (rebrand) but these pinned hashes
    // were never refreshed to match. On every release build this made
    // ResourceIntegrityEngine.check() report RESOURCE_MISMATCH for all 10
    // launcher icon entries, which GITOFYApp.onCreate() treats as untrusted
    // and responds to with Process.killProcess(Process.myPid()) — before
    // MainActivity, the splash-dismiss logic, or any Compose content ever
    // runs. That is what actually produced the "stuck on splash" symptom:
    // the process was being killed at Application start, not hanging.
    // Hashes below were recomputed from the actual PNG bytes currently
    // shipped in res/mipmap-*/ so the integrity check reflects reality.
    private val expectedEntries = linkedMapOf(
        "res/drawable-nodpi/gitofy_new_app_icon.png" to "12E3B9EBE21E413448149C528C815DDE54F56F316F83D223BB3219CF32AF6611",
        "res/drawable-nodpi/ic_launcher_adaptive.png" to "12E3B9EBE21E413448149C528C815DDE54F56F316F83D223BB3219CF32AF6611",
        "res/drawable/ic_gito_logo.png" to "5C23650B4F803D58BDDDC20DC9A4506C5EC82E2E11CC61E2BC56512BD132F8D2",
        "res/mipmap-mdpi/ic_launcher.png" to "E69239E53739F28326208900A3D9DF55A3C9CF1741E2571E25DACD79318836B8",
        "res/mipmap-mdpi/ic_launcher_round.png" to "E69239E53739F28326208900A3D9DF55A3C9CF1741E2571E25DACD79318836B8",
        "res/mipmap-hdpi/ic_launcher.png" to "EA3B1342B1579A425508ED2DAFDDBE7A70C7635C2AEBFEC0A3FC4190EEE035AD",
        "res/mipmap-hdpi/ic_launcher_round.png" to "EA3B1342B1579A425508ED2DAFDDBE7A70C7635C2AEBFEC0A3FC4190EEE035AD",
        "res/mipmap-xhdpi/ic_launcher.png" to "EFCC931B37AE84235CB777C228F5F3D8D7F679AA6850683438103D94920BA9A6",
        "res/mipmap-xhdpi/ic_launcher_round.png" to "EFCC931B37AE84235CB777C228F5F3D8D7F679AA6850683438103D94920BA9A6",
        "res/mipmap-xxhdpi/ic_launcher.png" to "4DA16891C4FBA079AB2B9F10236A84E75E307FE4C5F0AB59BB8670B429C7BBC8",
        "res/mipmap-xxhdpi/ic_launcher_round.png" to "4DA16891C4FBA079AB2B9F10236A84E75E307FE4C5F0AB59BB8670B429C7BBC8",
        "res/mipmap-xxxhdpi/ic_launcher.png" to "42A6C1223D7CC2069D71A94F7E94BF96EF6043383644D9CC24EA673EC3172569",
        "res/mipmap-xxxhdpi/ic_launcher_round.png" to "42A6C1223D7CC2069D71A94F7E94BF96EF6043383644D9CC24EA673EC3172569"
    )

    data class Result(
        val trusted: Boolean,
        val packageOk: Boolean,
        val labelOk: Boolean,
        val iconReferenceOk: Boolean,
        val resourceFailures: List<String>,
        val manifestSha256: String,
        val resourcesArscSha256: String
    )

    fun check(context: Context): Result {
        val packageOk = !isRelease() || context.packageName == EXPECTED_PACKAGE
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        val labelOk = !isRelease() || (label == EXPECTED_LABEL && sha256(label) == "22D3BC7AE9BDD4A094E0B809C0BC0EE5A6C9A567249B5260CE230D2218ACC423")

        val iconReferenceOk = runCatching {
            // ApplicationInfo exposes the primary icon resource directly.
            // roundIcon is a manifest/resource concept and is not a stable
            // ApplicationInfo property across the supported API levels.
            context.applicationInfo.icon == R.mipmap.ic_launcher
        }.getOrDefault(false)

        val failures = mutableListOf<String>()
        var manifestHash = ""
        var arscHash = ""

        runCatching {
            ZipFile(File(context.applicationInfo.sourceDir)).use { zip ->
                expectedEntries.forEach { (entryName, expectedHash) ->
                    val entry = zip.getEntry(entryName)
                    val actual = entry?.let { zip.getInputStream(it).use(::sha256) }.orEmpty()
                    if (actual != expectedHash) failures += entryName
                }

                manifestHash = zip.getEntry("AndroidManifest.xml")?.let {
                    zip.getInputStream(it).use(::sha256)
                }.orEmpty()
                arscHash = zip.getEntry("resources.arsc")?.let {
                    zip.getInputStream(it).use(::sha256)
                }.orEmpty()
            }
        }.onFailure {
            failures += "APK_ZIP_READ_FAILURE"
        }

        val expectedManifest = BuildConfig.SECURITY_EXPECTED_MANIFEST_SHA256.trim().uppercase(Locale.US)
        if (isRelease() && expectedManifest.isNotEmpty() && manifestHash != expectedManifest) {
            failures += "AndroidManifest.xml"
        }

        val expectedArsc = BuildConfig.SECURITY_EXPECTED_RESOURCES_ARSC_SHA256.trim().uppercase(Locale.US)
        if (isRelease() && expectedArsc.isNotEmpty() && arscHash != expectedArsc) {
            failures += "resources.arsc"
        }

        val nativeIdentityOk = !isRelease() || NativeSecurity.validateIdentity(context.packageName, label)
        if (!nativeIdentityOk) failures += "NATIVE_APP_IDENTITY"

        return Result(
            trusted = packageOk && labelOk && iconReferenceOk && failures.isEmpty(),
            packageOk = packageOk,
            labelOk = labelOk,
            iconReferenceOk = iconReferenceOk,
            resourceFailures = failures.distinct(),
            manifestSha256 = manifestHash,
            resourcesArscSha256 = arscHash
        )
    }

    private fun isRelease(): Boolean = BuildConfig.BUILD_TYPE == "release"

    private fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}

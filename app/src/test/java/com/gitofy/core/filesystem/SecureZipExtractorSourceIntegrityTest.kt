package com.gitofy.core.filesystem

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PRD — Repository Update Failure Fix, §16 "Critical Regression Test".
 *
 * Reproduces the exact bug: a valid, non-empty source ZIP must remain
 * non-empty and byte-for-byte intact after it passes through
 * SecureZipExtractor#validateZip and SecureZipExtractor#extractZip — the two
 * stages the corrupted "Update Failed → ZIP file is empty" bug touched.
 *
 * Historically, RepositorySyncEngine.updateRepository() re-opened
 * operationDir/source.zip as an OutputStream while a stream over that same
 * file was still being read as input, truncating it to 0 bytes before the
 * copy/extraction could complete. This test asserts that never happens:
 * source.zip's length and content must be unchanged after the pipeline
 * begins.
 */
class SecureZipExtractorSourceIntegrityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val extractor = SecureZipExtractor()

    private fun buildValidZip(target: File): ByteArray {
        ZipOutputStream(target.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("project/build.gradle.kts"))
            zos.write("// sample project file".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("project/settings.gradle.kts"))
            zos.write("rootProject.name = \"sample\"".toByteArray())
            zos.closeEntry()
        }
        return target.readBytes()
    }

    @Test
    fun `source zip is untouched by validateZip`() {
        val sourceZip = File(tempFolder.root, "source.zip")
        val originalBytes = buildValidZip(sourceZip)
        val originalSize = sourceZip.length()
        assertTrue("precondition: source zip must be non-empty", originalSize > 0L)

        val validation = extractor.validateZip(sourceZip)

        assertTrue(validation.isValid)
        // Stage 1/2 assertion (PRD §16): size never drops to zero.
        assertEquals(originalSize, sourceZip.length())
        assertArrayEquals(originalBytes, sourceZip.readBytes())
    }

    @Test
    fun `source zip remains non-empty and intact after extraction`() {
        val sourceZip = File(tempFolder.root, "source.zip")
        val originalBytes = buildValidZip(sourceZip)
        val originalSize = sourceZip.length()

        val extractDir = File(tempFolder.root, "extracted")
        val result = extractor.extractZip(sourceZip, extractDir)

        assertTrue("extraction should succeed for a valid zip", result.isSuccess)

        // The critical regression assertion: after the update pipeline
        // begins (validate -> extract), source.zip must still be present,
        // non-empty, and byte-identical. Extraction must never write back
        // into source.zip's own path.
        assertTrue(sourceZip.isFile)
        assertEquals(originalSize, sourceZip.length())
        assertArrayEquals(originalBytes, sourceZip.readBytes())

        // Sanity: extraction actually produced the expected separate files,
        // in a directory distinct from source.zip.
        val extractedFile = File(extractDir, "project/settings.gradle.kts")
        assertTrue(extractedFile.isFile)
        assertEquals("rootProject.name = \"sample\"", extractedFile.readText())
    }

    @Test
    fun `zero-byte zip is rejected before extraction is attempted`() {
        val sourceZip = tempFolder.newFile("source.zip")
        assertEquals(0L, sourceZip.length())

        val validation = extractor.validateZip(sourceZip)

        assertTrue(!validation.isValid)
        assertEquals("ZIP file is empty", validation.error)
    }
}

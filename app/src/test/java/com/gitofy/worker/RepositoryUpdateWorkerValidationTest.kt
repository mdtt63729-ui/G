package com.gitofy.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * PRD — Repository Update Failure Fix, §5 / §13 / §16.
 *
 * Regression coverage for RepositoryUpdateWorker's Stage 1 pre-flight check
 * on the source ZIP. This is the guard that now runs BEFORE the ZIP is ever
 * handed to RepositorySyncEngine, replacing the old generic
 * "ZIP file is empty" error with a specific diagnostic per condition.
 *
 * [validateSourceZip] is pure (File metadata only) so it is exercised here
 * directly, without needing WorkManager/Hilt/Android infrastructure.
 */
class RepositoryUpdateWorkerValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `missing source zip is reported as missing`() {
        val missing = File(tempFolder.root, "does-not-exist.zip")

        val diagnostic = RepositoryUpdateWorker.validateSourceZip(missing)

        assertEquals("Source ZIP is missing", diagnostic)
    }

    @Test
    fun `zero-byte source zip is reported as empty`() {
        val zeroByte = tempFolder.newFile("source.zip")
        // TemporaryFolder#newFile creates a 0-byte file by default.
        assertEquals(0L, zeroByte.length())

        val diagnostic = RepositoryUpdateWorker.validateSourceZip(zeroByte)

        assertEquals("Source ZIP is empty", diagnostic)
    }

    @Test
    fun `non-empty source zip passes the pre-flight check`() {
        val nonEmpty = tempFolder.newFile("source.zip")
        nonEmpty.writeBytes(byteArrayOf(1, 2, 3, 4))

        val diagnostic = RepositoryUpdateWorker.validateSourceZip(nonEmpty)

        assertNull(diagnostic)
    }

    @Test
    fun `a directory at the source zip path is reported as missing`() {
        // Defensive case: isFile is false for directories too, so this must
        // not be mistaken for a valid ZIP.
        val asDirectory = tempFolder.newFolder("source.zip")

        val diagnostic = RepositoryUpdateWorker.validateSourceZip(asDirectory)

        assertEquals("Source ZIP is missing", diagnostic)
    }
}

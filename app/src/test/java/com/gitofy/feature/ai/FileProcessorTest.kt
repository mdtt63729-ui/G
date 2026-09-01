package com.gitofy.feature.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §63: Unit tests for FileProcessor (FileSizeFormatter).
 */
class FileProcessorTest {

    @Test
    fun `formatSize formats bytes correctly`() {
        assertEquals("0 B", FileProcessor.formatSize(0))
        assertEquals("512 B", FileProcessor.formatSize(512))
    }

    @Test
    fun `formatSize formats kilobytes correctly`() {
        assertEquals("1.0 KB", FileProcessor.formatSize(1024))
        assertEquals("1.5 KB", FileProcessor.formatSize(1536))
    }

    @Test
    fun `formatSize formats megabytes correctly`() {
        assertEquals("1.00 MB", FileProcessor.formatSize(1048576))
        assertEquals("1.84 MB", FileProcessor.formatSize(1929379))
    }

    @Test
    fun `formatSize formats gigabytes correctly`() {
        assertEquals("1.00 GB", FileProcessor.formatSize(1073741824))
    }

    @Test
    fun `MAX_INLINE_SIZE_BYTES is 4 MB`() {
        assertEquals(4L * 1024 * 1024, FileProcessor.MAX_INLINE_SIZE_BYTES)
    }

    @Test
    fun `MAX_TEXT_LENGTH is 100000`() {
        assertEquals(100_000, FileProcessor.MAX_TEXT_LENGTH)
    }
}

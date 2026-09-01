package com.gitofy.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §63: Unit tests for CommandParser.
 */
class CommandParserTest {

    private val parser = CommandParser()

    @Test
    fun `parse returns non-null ParsedCommand`() {
        val result = parser.parse("R-TUBE repository te logo change koro")
        assertNotNull(result)
        assertEquals("R-TUBE repository te logo change koro", result.rawCommand)
    }

    @Test
    fun `parse detects build requirement`() {
        val result = parser.parse("build error fix koro")
        assertTrue(result.buildRequired)
    }

    @Test
    fun `parse detects workflow requirement`() {
        val result = parser.parse("workflow run koro")
        assertTrue(result.workflowRequired)
    }

    @Test
    fun `parse extracts modification type`() {
        val result = parser.parse("logo change koro")
        assertEquals("Change", result.requestedModification)
    }

    @Test
    fun `parse extracts fix modification`() {
        val result = parser.parse("build error fix koro")
        assertEquals("Fix", result.requestedModification)
    }

    @Test
    fun `parse has toolName field for real tool dispatch`() {
        val result = parser.parse("R-TUBE er logo change kore dao")
        assertNotNull(result.toolName) // may be null if not mapped, but field exists
    }
}

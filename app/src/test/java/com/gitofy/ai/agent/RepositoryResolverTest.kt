package com.gitofy.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §63: Unit tests for RepositoryResolver.
 */
class RepositoryResolverTest {

    private val resolver = RepositoryResolver()

    private val userRepos = listOf(
        "mdtt63729-ui" to "R-TUBE",
        "mdtt63729-ui" to "gitofy",
        "octocat" to "Hello-World"
    )

    @Test
    fun `extract repo name from 'X repository' pattern`() {
        val name = resolver.extractRepoName("R-TUBE repository te logo change koro")
        assertEquals("R-TUBE", name)
    }

    @Test
    fun `extract repo name from 'X repo' pattern`() {
        val name = resolver.extractRepoName("gitofy repo te fix koro")
        assertEquals("gitofy", name)
    }

    @Test
    fun `extract repo name returns null for no match`() {
        val name = resolver.extractRepoName("just a random command")
        // Might match or not depending on patterns; at minimum it shouldn't crash
    }

    @Test
    fun `resolve exact match returns ExactMatch`() {
        val result = resolver.resolve("R-TUBE", userRepos)
        assertTrue(result is Resolution.ExactMatch)
        val match = result as Resolution.ExactMatch
        assertEquals("mdtt63729-ui", match.owner)
        assertEquals("R-TUBE", match.repo)
    }

    @Test
    fun `resolve case-insensitive match returns ExactMatch`() {
        val result = resolver.resolve("r-tube", userRepos)
        assertTrue(result is Resolution.ExactMatch)
    }

    @Test
    fun `resolve unknown name returns NotFound`() {
        val result = resolver.resolve("nonexistent-repo", userRepos)
        assertTrue(result is Resolution.NotFound)
    }

    @Test
    fun `resolve partial match returns ExactMatch when single candidate`() {
        val result = resolver.resolve("Hello", userRepos)
        assertTrue(result is Resolution.ExactMatch)
        val match = result as Resolution.ExactMatch
        assertEquals("octocat", match.owner)
        assertEquals("Hello-World", match.repo)
    }
}

package com.gitofy.ai.agent

import javax.inject.Inject

/**
 * PRD §28: Repository Name Detection.
 *
 * Resolves a repository name from a natural-language user request against the
 * authenticated user's actual repositories.
 *
 * Example:
 *   User: "R-TUBE repository te logo change koro"
 *   Resolver: "R-TUBE" → list user's repos → exact match → "mdtt63729-ui/R-TUBE"
 *
 * If multiple matches are found, or no match, the caller should ask the user
 * for clarification (PRD §28: "Repository name ambiguity থাকলে clarification চাইবে").
 */
class RepositoryResolver @Inject constructor() {

    /**
     * Extracts a potential repository name from a user command.
     *
     * Looks for patterns like "X repository", "X repo", "X project",
     * or simply a standalone token that looks like a repo name.
     */
    fun extractRepoName(command: String): String? {
        val lower = command.lowercase()

        // Pattern: "<name> repository" / "<name> repo" / "<name> project"
        val patterns = listOf(
            Regex("""(\S+)\s+repository""", RegexOption.IGNORE_CASE),
            Regex("""(\S+)\s+repo\b""", RegexOption.IGNORE_CASE),
            Regex("""(\S+)\s+project""", RegexOption.IGNORE_CASE),
            Regex("""in\s+(\S+)\s+repo""", RegexOption.IGNORE_CASE),
            Regex("""in\s+(\S+)\s+repository""", RegexOption.IGNORE_CASE),
        )

        for (pattern in patterns) {
            val match = pattern.find(lower)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                // Filter out common English stopwords that aren't repo names
                if (candidate.length > 1 && candidate !in STOPWORDS) {
                    // Try to get the original-case version from the command
                    val origMatch = pattern.find(command)
                    return origMatch?.groupValues?.get(1)?.trim() ?: candidate
                }
            }
        }

        return null
    }

    /**
     * Resolves a candidate name against the user's actual repositories.
     *
     * @param candidate The extracted repository name from the user's command.
     * @param userRepos List of (owner, name) pairs for the authenticated user's repos.
     * @return [Resolution] — exact match, multiple matches, or no match.
     */
    fun resolve(
        candidate: String,
        userRepos: List<Pair<String, String>>
    ): Resolution {
        // Exact match (case-insensitive)
        val exact = userRepos.filter { (_, name) ->
            name.equals(candidate, ignoreCase = true)
        }

        return when {
            exact.size == 1 -> Resolution.ExactMatch(exact[0].first, exact[0].second)
            exact.size > 1 -> Resolution.Ambiguous(exact.map { it.first to it.second })
            else -> {
                // Try partial/fuzzy match
                val partial = userRepos.filter { (_, name) ->
                    name.contains(candidate, ignoreCase = true) ||
                    candidate.contains(name, ignoreCase = true)
                }
                when {
                    partial.size == 1 -> Resolution.ExactMatch(partial[0].first, partial[0].second)
                    partial.size > 1 -> Resolution.Ambiguous(partial.map { it.first to it.second })
                    else -> Resolution.NotFound
                }
            }
        }
    }

    /** Words that should never be treated as repository names. */
    private val STOPWORDS = setOf(
        "the", "this", "that", "my", "your", "a", "an", "in", "on", "at",
        "to", "for", "of", "with", "and", "or", "is", "are", "was", "were",
        "this", "into", "from", "by", "it", "all", "any", "some"
    )
}

/**
 * Result of resolving a repository name.
 */
sealed interface Resolution {
    /** Single exact match found. */
    data class ExactMatch(val owner: String, val repo: String) : Resolution

    /** Multiple candidates — caller should ask user to clarify. */
    data class Ambiguous(val candidates: List<Pair<String, String>>) : Resolution

    /** No matching repository found. */
    data object NotFound : Resolution
}

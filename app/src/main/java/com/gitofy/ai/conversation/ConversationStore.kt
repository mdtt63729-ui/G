package com.gitofy.ai.conversation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §27: AI Conversation History persistence.
 *
 * In-memory implementation of the conversation store. The data model mirrors the
 * shape that will later be backed by a Room database (entities and DAOs already
 * exist under `com.gitofy.data.local`), so the public surface is stable and can
 * be re-wired to the Room layer without touching the callers.
 */
@Singleton
class ConversationStore @Inject constructor() {

    /**
     * The role a [ChatMessage] was authored by.
     */
    enum class MessageRole {
        USER,
        ASSISTANT,
        SYSTEM
    }

    /**
     * A single message inside a [Conversation].
     *
     * @property id        unique identifier for the message.
     * @property role      who authored the message.
     * @property content   the textual payload of the message.
     * @property timestamp epoch millis at which the message was persisted.
     */
    data class ChatMessage(
        val id: String = generateId(),
        val role: MessageRole,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * A conversation thread composed of an ordered list of [ChatMessage]s.
     *
     * @property id        unique identifier for the conversation.
     * @property title     human-readable title shown in the history list.
     * @property createdAt epoch millis at which the conversation was created.
     * @property updatedAt epoch millis of the last mutating operation.
     * @property messages  ordered list of messages (oldest first).
     */
    data class Conversation(
        val id: String = generateId(),
        val title: String,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val messages: List<ChatMessage> = emptyList()
    )

    private val store: MutableMap<String, Conversation> = ConcurrentHashMap()

    /**
     * Creates and persists a new conversation with the given [title].
     *
     * @param title the initial title for the conversation.
     * @return the newly created [Conversation].
     */
    fun createConversation(title: String): Conversation {
        val conversation = Conversation(title = title)
        store[conversation.id] = conversation
        return conversation
    }

    /**
     * Appends a [ChatMessage] to the conversation identified by [conversationId].
     *
     * The message is added to the end of the existing message list and the
     * conversation's [Conversation.updatedAt] timestamp is refreshed. If no
     * conversation exists for [conversationId] the call is a no-op.
     *
     * @param conversationId the target conversation id.
     * @param message        the message to append.
     * @return the updated [Conversation], or `null` if the id is unknown.
     */
    fun addMessage(conversationId: String, message: ChatMessage): Conversation? {
        val updated = store.computeIfPresent(conversationId) { _, current ->
            current.copy(
                messages = current.messages + message,
                updatedAt = System.currentTimeMillis()
            )
        }
        return updated
    }

    /**
     * Returns every conversation ordered by most-recently-updated first.
     *
     * @return a defensively-copied list of all conversations.
     */
    fun getConversations(): List<Conversation> =
        store.values.sortedByDescending { it.updatedAt }.toList()

    /**
     * Returns the conversation for [id], or `null` if it does not exist.
     *
     * @param id the conversation id.
     * @return the matching [Conversation], or `null`.
     */
    fun getConversation(id: String): Conversation? = store[id]

    /**
     * Deletes the conversation identified by [id] if it exists.
     *
     * @param id the conversation id to delete.
     * @return `true` if a conversation was removed, `false` otherwise.
     */
    fun deleteConversation(id: String): Boolean = store.remove(id) != null

    /**
     * Updates the title of an existing conversation.
     *
     * The [Conversation.updatedAt] timestamp is refreshed. If no conversation
     * exists for [id] the call is a no-op.
     *
     * @param id    the target conversation id.
     * @param title  the new title.
     * @return the updated [Conversation], or `null` if the id is unknown.
     */
    fun updateTitle(id: String, title: String): Conversation? {
        val updated = store.computeIfPresent(id) { _, current ->
            current.copy(
                title = title,
                updatedAt = System.currentTimeMillis()
            )
        }
        return updated
    }

    companion object {
        /** Generates a unique identifier suitable for conversations and messages. */
        fun generateId(): String = UUID.randomUUID().toString()
    }
}

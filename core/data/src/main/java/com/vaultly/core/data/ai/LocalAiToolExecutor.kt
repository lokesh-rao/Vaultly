package com.vaultly.core.data.ai

import com.vaultly.core.domain.ai.AiConfirmationRequirement
import com.vaultly.core.domain.ai.AiPermission
import com.vaultly.core.domain.ai.AiToolDefinition
import com.vaultly.core.domain.ai.AiToolExecutor
import com.vaultly.core.domain.ai.AiToolResult
import com.vaultly.core.domain.model.NoteCategory
import com.vaultly.core.domain.model.PasswordCategory
import com.vaultly.core.domain.model.PasswordEntry
import com.vaultly.core.domain.model.PasswordGeneratorConfig
import com.vaultly.core.domain.model.SecureNote
import com.vaultly.core.domain.model.VaultSearchResult
import com.vaultly.core.domain.password.PasswordGenerator
import com.vaultly.core.domain.password.PasswordStrengthEvaluator
import com.vaultly.core.domain.repository.VaultRepository
import com.vaultly.core.domain.util.getOrNull
import kotlinx.coroutines.flow.first
import java.util.UUID

class LocalAiToolExecutor(
    private val vaultRepository: VaultRepository
) : AiToolExecutor {

    override val definitions: List<AiToolDefinition> = listOf(
        AiToolDefinition("searchVault", "Search passwords and notes", AiPermission.READ, AiConfirmationRequirement.NONE),
        AiToolDefinition("getVaultHealth", "Summarize vault health", AiPermission.READ, AiConfirmationRequirement.NONE),
        AiToolDefinition("listWeakPasswords", "List weak passwords", AiPermission.READ, AiConfirmationRequirement.NONE),
        AiToolDefinition("generatePassword", "Generate a strong password", AiPermission.GENERATE, AiConfirmationRequirement.NONE),
        AiToolDefinition("checkPasswordStrength", "Evaluate password strength", AiPermission.GENERATE, AiConfirmationRequirement.NONE),
        AiToolDefinition("createPassword", "Create a password entry", AiPermission.WRITE, AiConfirmationRequirement.CONFIRM),
        AiToolDefinition("updateVaultItem", "Update a password entry", AiPermission.WRITE, AiConfirmationRequirement.CONFIRM),
        AiToolDefinition("createNote", "Create a secure note", AiPermission.WRITE, AiConfirmationRequirement.CONFIRM),
        AiToolDefinition("deleteVaultItem", "Delete a password", AiPermission.DESTRUCTIVE, AiConfirmationRequirement.CONFIRM_DESTRUCTIVE),
        AiToolDefinition("deleteNote", "Delete a note", AiPermission.DESTRUCTIVE, AiConfirmationRequirement.CONFIRM_DESTRUCTIVE)
    )

    override suspend fun execute(toolName: String, args: Map<String, String>): AiToolResult {
        return when (toolName) {
            "searchVault" -> search(args["query"].orEmpty())
            "getVaultHealth" -> health()
            "listWeakPasswords" -> weakPasswords()
            "generatePassword" -> {
                val password = PasswordGenerator.generate(
                    PasswordGeneratorConfig(length = args["length"]?.toIntOrNull() ?: 24)
                )
                AiToolResult.GeneratedPassword(password, args["title"])
            }
            "checkPasswordStrength" -> {
                val report = PasswordStrengthEvaluator.evaluate(args["password"].orEmpty())
                AiToolResult.Text("Strength: ${report.strength}. Issues: ${report.issues.joinToString().ifBlank { "none" }}")
            }
            "createPassword" -> AiToolResult.PendingConfirmation(
                toolName = toolName,
                summary = "Create password entry “${args["title"].orEmpty()}”",
                payload = args
            )
            "updateVaultItem" -> AiToolResult.PendingConfirmation(
                toolName = toolName,
                summary = "Update password for “${args["title"].orEmpty()}”",
                payload = args
            )
            "createNote" -> AiToolResult.PendingConfirmation(
                toolName = toolName,
                summary = "Create note “${args["title"].orEmpty()}”",
                payload = args
            )
            "deleteVaultItem", "deleteNote" -> AiToolResult.PendingConfirmation(
                toolName = toolName,
                summary = "Delete “${args["title"] ?: args["id"].orEmpty()}”. This cannot be undone.",
                payload = args
            )
            else -> AiToolResult.Error("Unknown tool: $toolName")
        }
    }

    override suspend fun confirm(toolName: String, payload: Map<String, String>): AiToolResult {
        val now = System.currentTimeMillis()
        return when (toolName) {
            "createPassword" -> {
                val entry = PasswordEntry(
                    id = UUID.randomUUID().toString(),
                    title = payload["title"].orEmpty().ifBlank { "Untitled" },
                    username = payload["username"].orEmpty(),
                    password = payload["password"].orEmpty(),
                    url = payload["url"].orEmpty(),
                    notes = payload["notes"].orEmpty(),
                    category = runCatching {
                        PasswordCategory.valueOf(payload["category"] ?: PasswordCategory.OTHER.name)
                    }.getOrDefault(PasswordCategory.OTHER),
                    tags = payload["tags"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    favorite = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                vaultRepository.upsertPassword(entry)
                AiToolResult.Text("Saved “${entry.title}”.")
            }
            "updateVaultItem" -> {
                val id = payload["id"].orEmpty()
                val existing = vaultRepository.getPassword(id).getOrNull()
                    ?: return AiToolResult.Error("Password not found.")
                val updated = existing.copy(
                    password = payload["password"] ?: existing.password,
                    username = payload["username"] ?: existing.username,
                    title = payload["title"] ?: existing.title,
                    updatedAtEpochMs = now
                )
                vaultRepository.upsertPassword(updated)
                AiToolResult.Text("Updated “${updated.title}”.")
            }
            "createNote" -> {
                val note = SecureNote(
                    id = UUID.randomUUID().toString(),
                    title = payload["title"].orEmpty().ifBlank { "Untitled note" },
                    content = payload["content"].orEmpty(),
                    category = NoteCategory.PERSONAL,
                    tags = emptyList(),
                    favorite = false,
                    pinned = false,
                    archived = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                vaultRepository.upsertNote(note)
                AiToolResult.Text("Created note “${note.title}”.")
            }
            "deleteVaultItem" -> {
                vaultRepository.deletePassword(payload["id"].orEmpty())
                AiToolResult.Text("Password deleted.")
            }
            "deleteNote" -> {
                vaultRepository.deleteNote(payload["id"].orEmpty())
                AiToolResult.Text("Note deleted.")
            }
            else -> AiToolResult.Error("Nothing to confirm for $toolName")
        }
    }

    private suspend fun search(query: String): AiToolResult {
        val results = vaultRepository.search(query).getOrNull().orEmpty()
        if (results.isEmpty()) return AiToolResult.Text("No matches for “$query”.")
        val first = results.first()
        return when (first) {
            is VaultSearchResult.Password -> AiToolResult.PasswordFound(
                id = first.entry.id,
                title = first.entry.title,
                username = first.entry.username,
                maskedPassword = "••••••••••••",
                url = first.entry.url
            )
            is VaultSearchResult.Note -> AiToolResult.Text(
                "Found note “${first.note.title}”. Open Notes to view content."
            )
        }
    }

    private suspend fun health(): AiToolResult {
        val health = vaultRepository.observeVaultHealth().first()
        val recs = health.recommendations.joinToString("\n") { "• ${it.message}" }
        return AiToolResult.HealthSummary(
            "${health.score}% — ${health.rating}\n" +
                "${health.totalPasswords} passwords · ${health.strongCount} strong · " +
                "${health.weakCount} weak · ${health.reusedCount} reused\n" +
                if (recs.isBlank()) "No recommendations." else "Recommendations:\n$recs"
        )
    }

    private suspend fun weakPasswords(): AiToolResult {
        val health = vaultRepository.observeVaultHealth().first()
        val weak = health.recommendations.find { it.id == "weak" }
        return if (weak == null || weak.itemIds.isEmpty()) {
            AiToolResult.Text("No weak passwords found.")
        } else {
            AiToolResult.Text("${weak.message}. Open Vault Health for details.")
        }
    }
}

class LocalAiOrchestrator(
    private val executor: AiToolExecutor
) {
    suspend fun handle(userMessage: String): AiToolResult {
        val text = userMessage.trim()
        val lower = text.lowercase()
        return when {
            lower.contains("weak") || lower.contains("attention") || lower.contains("health") ->
                executor.execute("getVaultHealth", emptyMap())
            lower.startsWith("generate") || lower.contains("generate a") || lower.contains("strong password") -> {
                val title = extractTitle(text)
                executor.execute(
                    "generatePassword",
                    mapOf("length" to "24", "title" to (title ?: ""))
                )
            }
            lower.startsWith("create a note") || lower.contains("create note") -> {
                val title = Regex("""called\s+(.+)$""", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.getOrNull(1)?.trim()
                    ?: "New note"
                executor.execute("createNote", mapOf("title" to title, "content" to ""))
            }
            lower.startsWith("find") || lower.contains("find my") || lower.contains("show me") ||
                lower.contains("search") || lower.contains("accounts") -> {
                val query = text
                    .replace(Regex("""(?i)find my|find|show me|search for|search|accounts"""), "")
                    .trim()
                    .ifBlank { text }
                executor.execute("searchVault", mapOf("query" to query))
            }
            lower.contains("update") && lower.contains("password") -> {
                val title = extractTitle(text) ?: "item"
                executor.execute(
                    "updateVaultItem",
                    mapOf("title" to title, "id" to "", "password" to PasswordGenerator.generate())
                )
            }
            else -> AiToolResult.Text(
                "I can search your vault, generate passwords, check vault health, and create notes. Try: “Find my GitHub account.”"
            )
        }
    }

    private fun extractTitle(text: String): String? {
        val forMatch = Regex("""(?i)for\s+([A-Za-z0-9 ._-]+)""").find(text)?.groupValues?.getOrNull(1)
        return forMatch?.trim()?.trimEnd('.', '?', '!')
    }
}

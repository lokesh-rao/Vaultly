package com.vaultly.core.domain.ai

enum class AiPermission {
    READ,
    GENERATE,
    WRITE,
    DESTRUCTIVE
}

enum class AiConfirmationRequirement {
    NONE,
    CONFIRM,
    CONFIRM_DESTRUCTIVE
}

data class AiToolDefinition(
    val name: String,
    val description: String,
    val permission: AiPermission,
    val confirmation: AiConfirmationRequirement
)

sealed interface AiToolResult {
    data class Text(val message: String) : AiToolResult
    data class PasswordFound(
        val id: String,
        val title: String,
        val username: String,
        val maskedPassword: String,
        val url: String
    ) : AiToolResult
    data class GeneratedPassword(val password: String, val suggestedTitle: String?) : AiToolResult
    data class HealthSummary(val message: String) : AiToolResult
    data class PendingConfirmation(
        val toolName: String,
        val summary: String,
        val payload: Map<String, String>
    ) : AiToolResult
    data class Error(val message: String) : AiToolResult
}

interface AiToolExecutor {
    val definitions: List<AiToolDefinition>
    suspend fun execute(toolName: String, args: Map<String, String>): AiToolResult
    suspend fun confirm(toolName: String, payload: Map<String, String>): AiToolResult
}

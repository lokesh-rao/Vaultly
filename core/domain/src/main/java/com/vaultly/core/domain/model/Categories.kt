package com.vaultly.core.domain.model

enum class PasswordCategory(val displayName: String) {
    WORK("Work"),
    DEVELOPMENT("Development"),
    BANKING("Banking"),
    SOCIAL("Social"),
    SHOPPING("Shopping"),
    EMAIL("Email"),
    OTHER("Other")
}

enum class NoteCategory(val displayName: String) {
    PERSONAL("Personal"),
    WORK("Work"),
    TRAVEL("Travel"),
    FINANCE("Finance"),
    IDEAS("Ideas"),
    OTHER("Other")
}

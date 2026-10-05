package com.vaultly.core.domain.util

sealed interface DataError : Error {
    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        CORRUPT,
        UNKNOWN
    }

    enum class Network : DataError {
        NO_INTERNET,
        UNKNOWN
    }
}

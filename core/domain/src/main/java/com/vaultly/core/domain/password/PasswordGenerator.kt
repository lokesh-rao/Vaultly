package com.vaultly.core.domain.password

import com.vaultly.core.domain.model.PasswordGeneratorConfig
import java.security.SecureRandom

object PasswordGenerator {
    private val uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val lowercase = "abcdefghijkmnopqrstuvwxyz"
    private val numbers = "23456789"
    private val symbols = "!@#$%^&*()-_=+[]{}?"
    private val ambiguous = "O0Il1|"

    private val uppercaseAll = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val lowercaseAll = "abcdefghijklmnopqrstuvwxyz"
    private val numbersAll = "0123456789"

    fun generate(config: PasswordGeneratorConfig = PasswordGeneratorConfig()): String {
        require(config.length in 8..64) { "Length must be 8..64" }
        val pools = buildList {
            if (config.uppercase) add(if (config.excludeAmbiguous) uppercase else uppercaseAll)
            if (config.lowercase) add(if (config.excludeAmbiguous) lowercase else lowercaseAll)
            if (config.numbers) add(if (config.excludeAmbiguous) numbers else numbersAll)
            if (config.symbols) add(symbols)
        }
        require(pools.isNotEmpty()) { "At least one character set required" }

        val random = SecureRandom()
        val required = pools.map { it[random.nextInt(it.length)] }
        val all = pools.joinToString("")
        val remaining = (config.length - required.size).coerceAtLeast(0)
        val chars = (required + List(remaining) { all[random.nextInt(all.length)] }).shuffled(random)
        return chars.joinToString("")
    }
}

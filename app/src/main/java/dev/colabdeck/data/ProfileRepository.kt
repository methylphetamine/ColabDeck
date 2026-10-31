package dev.colabdeck.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ProfileRepository(private val secure: SecureStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<SshProfile>> = _profiles.asStateFlow()

    fun get(id: String): SshProfile? = _profiles.value.firstOrNull { it.id == id }

    fun save(profile: SshProfile) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) current[index] = profile else current.add(profile)
        persist(current)
    }

    fun delete(id: String) {
        persist(_profiles.value.filterNot { it.id == id })
    }

    fun importFromText(text: String): SshProfile? {
        val parsed = parseSshText(text) ?: return null
        save(parsed)
        return parsed
    }

    private fun load(): List<SshProfile> {
        val raw = secure.get(KEY_PROFILES) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<SshProfile>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun persist(profiles: List<SshProfile>) {
        _profiles.value = profiles
        secure.put(KEY_PROFILES, json.encodeToString(profiles))
    }

    companion object {
        private const val KEY_PROFILES = "ssh_profiles"

        /**
         * Accepts `ssh user@host`, `ssh -p 2222 user@host`, `ssh://user@host:2222`
         * and the one-line address printed by tmate.
         */
        fun parseSshText(raw: String): SshProfile? {
            val text = raw.trim()
            if (text.isEmpty()) return null

            val line = text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("ssh ") || it.startsWith("ssh://") }
                ?: text.lineSequence().firstOrNull { it.contains("@") }
                ?: return null

            var rest = line
            if (rest.startsWith("ssh://")) rest = rest.removePrefix("ssh://")
            else rest = rest.removePrefix("ssh").trim()

            var port = 22
            var user = "root"
            var host: String? = null
            val tokens = rest.split(Regex("\\s+")).filter { it.isNotBlank() }

            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                when {
                    token == "-p" && i + 1 < tokens.size -> {
                        port = tokens[++i].toIntOrNull() ?: port
                    }
                    token.startsWith("-p") && token.length > 2 -> {
                        port = token.substring(2).toIntOrNull() ?: port
                    }
                    token.startsWith("-") -> Unit
                    token.contains("@") -> {
                        user = token.substringBefore("@").ifBlank { user }
                        host = token.substringAfter("@")
                    }
                    host == null -> host = token
                }
                i++
            }

            var hostname = host?.trim()?.trimEnd('/') ?: return null
            if (hostname.contains("@")) {
                user = hostname.substringBefore("@").ifBlank { user }
                hostname = hostname.substringAfter("@")
            }
            if (hostname.contains(":") && !hostname.startsWith("[")) {
                val maybePort = hostname.substringAfterLast(":").toIntOrNull()
                if (maybePort != null) {
                    port = maybePort
                    hostname = hostname.substringBeforeLast(":")
                }
            }
            hostname = hostname.trim('[', ']')
            if (hostname.isBlank()) return null

            return SshProfile(
                id = UUID.randomUUID().toString(),
                name = hostname,
                host = hostname,
                port = port,
                user = user,
                note = "Imported from clipboard"
            )
        }
    }
}

package dev.colabdeck.data

import kotlinx.serialization.Serializable

@Serializable
data class SessionMeta(
    val endpoint: String,
    val name: String = "",
    val createdAt: Long = 0L,
    val lastConnectedAt: Long = 0L
)

@Serializable
data class SshProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String = "root",
    val authType: String = AUTH_PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val note: String = ""
) {
    companion object {
        const val AUTH_PASSWORD = "password"
        const val AUTH_KEY = "key"
    }
}

data class ColabSession(
    val endpoint: String,
    val accelerator: String,
    val variant: String,
    val machineShape: String,
    val proxyUrl: String,
    val proxyToken: String,
    val tokenExpiresAt: Long,
    val name: String = "",
    val lastConnectedAt: Long = 0L
) {
    val hardwareLabel: String
        get() = when (accelerator.uppercase()) {
            "NONE" -> "CPU"
            "V5E1" -> "TPU v5e1"
            "V6E1" -> "TPU v6e1"
            else -> accelerator.uppercase()
        }

    val isTpu: Boolean get() = variant.equals("TPU", true)
    val isGpu: Boolean get() = variant.equals("GPU", true) && !isTpu
    val shapeLabel: String get() = if (machineShape.equals("HIGH_RAM", true)) "High-RAM" else "Standard"
    val variantLabel: String get() = variant.lowercase().replaceFirstChar { it.uppercase() }
    val displayName: String get() = name.ifBlank { hardwareLabel }
}

data class SshKeyMaterial(
    val privateKey: String,
    val publicKey: String
)

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresInSeconds: Long
)

enum class Accelerator(
    val label: String,
    val shortLabel: String,
    val variant: String,
    val accelerator: String,
    val description: String,
    val highMemoryOnly: Boolean = false
) {
    CPU("CPU runtime", "CPU", "DEFAULT", "NONE", "General purpose · always available"),
    T4("T4 GPU", "T4", "GPU", "T4", "16 GB VRAM · free tier"),
    L4("L4 GPU", "L4", "GPU", "L4", "24 GB VRAM · Pro"),
    G4("G4 GPU", "G4", "GPU", "G4", "Ada generation · Pro"),
    A100("A100 GPU", "A100", "GPU", "A100", "40 GB VRAM · Pro+", highMemoryOnly = true),
    H100("H100 GPU", "H100", "GPU", "H100", "80 GB VRAM · Pro+"),
    TPU_V5E1("TPU v5e1", "v5e1", "TPU", "V5E1", "TPU v5e · Pro", highMemoryOnly = true),
    TPU_V6E1("TPU v6e1", "v6e1", "TPU", "V6E1", "TPU v6e · Pro+", highMemoryOnly = true)
}

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(
        val email: String?,
        val name: String?,
        val expiresAt: Long,
        val canRefresh: Boolean
    ) : AuthState

    data class Error(val message: String) : AuthState
}

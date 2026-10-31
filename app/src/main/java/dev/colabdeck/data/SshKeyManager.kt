package dev.colabdeck.data

import android.util.Base64
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream

/**
 * The Colab SSH endpoint authenticates by trusting the public key sent in the
 * X-Colab-Ssh-Pubkey WebSocket header. Any key pair works as long as the same
 * private key completes the SSH handshake. RSA is rejected by the server, so
 * we generate ECDSA P-256, which every Android release we support can use.
 */
class SshKeyManager(private val secure: SecureStore) {

    private var cached: SshKeyMaterial? = null

    @Synchronized
    fun material(): SshKeyMaterial {
        cached?.let { return it }

        val existingPrivate = secure.get(KEY_PRIVATE)
        val existingPublic = secure.get(KEY_PUBLIC)
        if (!existingPrivate.isNullOrBlank() && !existingPublic.isNullOrBlank()) {
            return SshKeyMaterial(existingPrivate, existingPublic).also { cached = it }
        }

        val jsch = JSch()
        val pair = KeyPair.genKeyPair(jsch, KeyPair.ECDSA, 256)
        try {
            val output = ByteArrayOutputStream()
            pair.writePrivateKey(output)
            val privateKey = String(output.toByteArray(), Charsets.UTF_8)
            val publicKey = "ecdsa-sha2-nistp256 " +
                Base64.encodeToString(pair.publicKeyBlob, Base64.NO_WRAP)

            secure.put(KEY_PRIVATE, privateKey)
            secure.put(KEY_PUBLIC, publicKey)
            return SshKeyMaterial(privateKey, publicKey).also { cached = it }
        } finally {
            runCatching { pair.dispose() }
        }
    }

    fun jschWithAppKey(): JSch {
        val key = material()
        val jsch = JSch()
        jsch.addIdentity("colabdeck", key.privateKey.toByteArray(Charsets.UTF_8), null, null)
        return jsch
    }

    fun jschWithPrivateKey(privateKey: String, passphrase: String): JSch {
        val jsch = JSch()
        jsch.addIdentity(
            "profile",
            privateKey.toByteArray(Charsets.UTF_8),
            null,
            passphrase.ifBlank { null }?.toByteArray(Charsets.UTF_8)
        )
        return jsch
    }

    private companion object {
        const val KEY_PRIVATE = "ssh_private_key"
        const val KEY_PUBLIC = "ssh_public_key"
    }
}

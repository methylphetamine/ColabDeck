package dev.colabdeck

import android.content.Context
import dev.colabdeck.data.AuthRepository
import dev.colabdeck.data.ColabApi
import dev.colabdeck.data.ColabFilesApi
import dev.colabdeck.data.Prefs
import dev.colabdeck.data.ProfileRepository
import dev.colabdeck.data.SecureStore
import dev.colabdeck.data.SessionRepository
import dev.colabdeck.data.SshKeyManager
import dev.colabdeck.ssh.SshManager

class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val secure = SecureStore(context)
    val auth = AuthRepository(prefs, secure)
    val api = ColabApi(auth)
    val sessions = SessionRepository(api, prefs)
    val files = ColabFilesApi()
    val profiles = ProfileRepository(secure)
    val keys = SshKeyManager(secure)
    val ssh = SshManager(keys)
}

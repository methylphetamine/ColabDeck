# ColabDeck

A native Android client for Google Colab runtimes: list, create and stop sessions, open a real
SSH shell over Colab's official WebSocket transport, move files in and out of `/content`, and keep
extra terminal keys above the keyboard — no notebook cell, no cloudflared, no port forwarding.

ColabDeck is **not** a WebView wrapper. The only WebView in the app renders xterm.js; sessions,
authentication, SSH and file transfer are native Kotlin.

---

## Features

- **Session control** — lists your Colab assignments, allocates CPU / T4 / L4 / G4 / A100 / H100 /
  TPU v5e / TPU v6e runtimes, renames and stops them. Tokens are refreshed before every connection.
- **Official Colab SSH** — generates an ECDSA P-256 key on the device and opens
  `wss://<runtime>/colab/ssh` with `X-Colab-Ssh-Pubkey`, then bridges that socket into JSch for a
  full interactive PTY shell (`root@colab-runtime`).
- **Termux-style key bar** — always visible above the keyboard: `ESC`, `TAB`, sticky `CTRL` and
  `ALT`, arrow keys, `HOME`, `END`, `PGUP`, `PGDN`, `^C`, `^D`, `^Z`, `^L`, `^R`, and `| ~ - /`.
  Arm `CTRL`/`ALT`, then type a letter on the soft keyboard to send the modified key.
- **File browser** — browse `/content` on the runtime through the Jupyter Contents API, upload with
  Android's document picker, download to any local folder, create folders and delete entries.
- **Saved SSH profiles** — connect to tmate, ngrok, LAN machines or any reachable sshd using a
  password or private key.
- **Encrypted at rest** — Google tokens, OAuth client secret, SSH profiles and the app private key
  are stored with an AES/GCM key held in the Android Keystore.
- **Offline terminal assets** — xterm.js 5.3.0 and xterm-addon-fit 0.8.0 are bundled; no CDN.

## Requirements

- Android 8.0 (API 26) or newer.
- A Google account with Colab access.
- One of:
  - a **Desktop app** OAuth client ID from Google Cloud Console (recommended), or
  - a Google OAuth **access token** with the `https://www.googleapis.com/auth/colaboratory` scope.

## Build

The project uses Android SDK 35, AGP 8.7.3, Kotlin 2.0.21 and Gradle 8.9.

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Optional: bake in a default OAuth client so users do not have to paste it:

```bash
./gradlew assembleDebug \
  -PcolabOauthClientId="1234-abc.apps.googleusercontent.com" \
  -PcolabOauthClientSecret="GOCSPX-..."
```

Without those properties the client ID can be entered in **Settings → OAuth client** or on the
sign-in screen. Nothing is bundled by default.

## Google sign-in setup

### Option A — Desktop OAuth client (persistent refresh token)

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create/select a project.
2. Configure the **OAuth consent screen** (External is fine for personal use) and add yourself as a
   test user while the app is in testing.
3. Go to **Credentials → Create credentials → OAuth client ID → Desktop app**.
4. Copy the client ID (and secret if you want to supply it) into ColabDeck.
5. Tap **Continue with Google**. Chrome opens; the loopback redirect returns to the app and the
   refresh token is stored encrypted.

ColabDeck uses PKCE with a loopback redirect (`http://127.0.0.1:<random port>`), which is the
RFC 8252 flow Google supports for desktop clients. No SHA-1 fingerprint or redirect registration is
needed.

### Option B — Access token (quickest)

1. Open the [OAuth 2.0 Playground](https://developers.google.com/oauthplayground/).
2. Authorize `https://www.googleapis.com/auth/colaboratory`.
3. Exchange the authorization code and copy the **access token**.
4. Paste it into **Connect Colab → Access token**.

Access tokens last about an hour and cannot refresh; use Option A for day-to-day use.

## Using the terminal

1. Sign in and create a runtime (or open an existing assignment).
2. Tap **Terminal**. The app refreshes the runtime proxy token, opens the SSH WebSocket and starts a
   login shell in `/content`.
3. Tap the terminal to raise the keyboard. The key bar stays pinned above it.
4. Use the top-bar folder icon to open the file browser for that runtime.

Connection errors are mapped to actionable messages (expired proxy token, rejected key, SSH not
enabled, session limit, unhealthy runtime). **Reconnect** in the overflow menu retries with a fresh
token.

## File transfer

The folder icon opens `/content` through the runtime's Jupyter Contents API. Uploads and downloads
are base64 payloads over the runtime proxy, so they work anywhere the runtime is reachable. The
in-app limit is 100 MB per file. Files on a Colab VM are ephemeral and disappear when the runtime
stops.

## SSH profiles

Use **SSH profiles** for anything that is not a Colab runtime: tmate sessions, ngrok TCP tunnels, a
machine on your LAN, or any public sshd. Paste an address like `ssh -p 2222 root@host` or enter the
host, port, user and credentials manually. Cloudflare quick tunnels (`*.trycloudflare.com`) require
`cloudflared` on the client and are not supported.

## Security

- No backend, no analytics, no third-party relay. Requests go directly to Google.
- Tokens, client secret, SSH profiles and the ECDSA private key live in `EncryptedSharedPreferences`-
  style storage backed by a non-exportable Android Keystore AES/GCM key.
- Host-key checking is disabled because Colab presents an ephemeral host key and the channel is
  already authenticated by the runtime proxy token plus the app public key. Do not reuse the app key
  for unrelated hosts.
- ColabDeck is an independent client. Google Colab's terms still apply to your account.

## Project layout

```
app/src/main/java/dev/colabdeck/
  data/       auth, Colab control plane, Jupyter files API, secure storage, profiles
  ssh/        WebSocket ↔ Socket bridge, Colab SSH handshake, JSch session management
  ui/         Compose theme, screens, xterm WebView bridge
app/src/main/assets/terminal/   xterm.js + fit addon (offline)
```

## Limitations

- Colab's control-plane endpoints are the ones used by its official CLI and may change without
  notice; the app surfaces HTTP errors rather than hiding them.
- One interactive SSH session per runtime is typical; Colab may reject additional sessions (429).
- Only ECDSA/Ed25519 public keys are accepted by the Colab SSH endpoint, so the app generates ECDSA
  P-256. RSA is rejected server-side.
- Free-tier GPU/TPU availability depends on Colab quotas and capacity.

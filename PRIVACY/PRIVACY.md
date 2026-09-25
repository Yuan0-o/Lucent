# Lucent Privacy Policy

Lucent is a notes-and-tasks application for Android and Windows. This policy describes what the
app stores, what it sends off your device, and how you control both. It describes Lucent as it is
built in this repository.

**Last updated: 25 September 2026**

## 1. The short version

- Lucent has no account, no sign-up and no Lucent-operated server. There is no mechanism by which
  the developer receives your notes, tasks, files or usage.
- Notes, tasks, notebooks, checklists, version history, assistant conversations, attachments, API
  keys and settings are stored on your device and encrypted at rest.
- The app contains no analytics, no advertising and no telemetry.
- Every feature that uses the network is optional and off by default: the cloud assistant, web
  search, semantic search, cloud backup and automatic update checks.
- When you use the cloud assistant, your message goes directly from your device to the provider
  whose API key you supplied. It does not pass through Lucent.
- The optional on-device model answers entirely on your device and sends nothing anywhere.

The rest of this document explains each of those statements in detail, including the limits.

## 2. Who this policy comes from

Lucent is developed by Jessica Martinez. Questions about this policy, or about the app's handling
of data, can be sent to **yuan47578@gmail.com**.

Lucent is free software released under the MIT Licence. The developer operates no service that
collects or stores user data, and the application has no server component.

## 3. What Lucent stores on your device

Everything you create in Lucent lives in the application's own private storage on the device. That
storage area is not readable by other ordinary applications, and it is removed when you uninstall
the app or clear its data.

The material stored includes:

- notes, including their text, formatting, tags, colours, pin and archive state, checklists,
  doodles, links and version history;
- tasks, including subtasks, priorities, due dates, repeat rules, reminders, notes and version
  history;
- notebooks and the items filed under them;
- assistant conversations and messages, including any reasoning traces the model returned;
- attachments you add to a note or a task, and attachments you send to the assistant;
- imported fonts and imported local model files;
- settings, including your chosen provider, base URL, model name, API keys, backup password,
  app-lock credentials and cloud backup credentials;
- diagnostics, if you have switched diagnostic logging on.

### 3.1 Encryption at rest

The database is encrypted with SQLCipher. On Android the app uses Room over SQLCipher; on Windows
it uses SQLite through the bundled JDBC driver configured with the SQLCipher cipher and the same
kind of 256-bit key. Notes, tasks, conversations, history and settings are all inside that
database.

Attachments are encrypted separately. Each file is written in an authenticated AES-256-GCM
container, in 64 KiB chunks, before it reaches the disk. The filename on disk is a random
identifier rather than the name you see in the app.

Backups are always encrypted as well; see section 6.

### 3.2 Keys and secrets

The database key and the attachment key are random 256-bit values generated on your device. They
are not derived from anything you type and they are not written to disk in plain text. On Android
they are wrapped by a key held in the Android Keystore, which is hardware-backed where the device
provides it, with a machine-local fallback when the Keystore is unavailable. A recovery copy of
each key is kept so that a Keystore problem does not destroy your data.

API keys, WebDAV passwords, the backup password and your app-lock credentials are stored through
the same encrypted-secret path, not as plain text. Your app-lock password and security answer are
never stored at all: only a salted hash is kept, and the password itself cannot be recovered from
it.

### 3.3 What encryption can and cannot do

Encryption here protects data at rest: a copy of the database, an attachment file or a backup that
is taken off the device cannot be read without the key. It does not protect against someone who is
using your unlocked device with your operating-system account, and it does not protect data while
the app is open, because decrypted content necessarily exists in the device's memory while it is
being displayed or edited.

Lucent does not pretend about this. If the encryption library cannot be loaded, or a database
cannot be encrypted or unlocked, the app records the fact and reports it rather than claiming
success; Settings provides an encryption check that verifies values are sealed and open correctly.
If a database cannot be decrypted with the stored key, Lucent sets it aside rather than deleting
it and tells you so, so that you can restore from a backup.

Home-screen widgets on Android display note titles or task titles on your launcher. Anything a
widget shows is visible to whoever can see that screen. The widgets are optional and can be
removed.

## 4. What never leaves your device

By default, nothing does. Lucent does not require a network connection for notes, tasks, reminders,
history, attachments, local search or the on-device assistant.

Specifically, Lucent contains no analytics SDK, no advertising SDK, no crash-reporting service and
no tracking identifiers. There is no account system and no device fingerprint that is sent
anywhere. Crash Shield, when you turn it on, writes caught errors to a local log file on the device
and does not transmit them. Diagnostic logging works the same way: it is off by default, it writes
locally, and it is only ever sent anywhere if you export it yourself and choose to share the file.

Because there is no Lucent server, the developer has no way to read your data, and no way to
recover it for you.

## 5. When data does leave your device

Each of the following is optional, off by default, and under your control. When one of them is
active, data leaves the device directly from the app to the third party you configured; it does not
pass through any Lucent service, because none exists.

### 5.1 The cloud assistant (bring your own key)

The assistant in the cloud mode uses an API key that you supply and a provider that you choose.
Lucent speaks the request formats of OpenAI-compatible, Anthropic-compatible and Google-compatible
endpoints, and any OpenAI-compatible address works, including DeepSeek and self-hosted gateways.
You can keep several provider profiles and switch between them.

When you send a message, Lucent makes an HTTPS request from your device to the base URL you
configured. That request contains:

- your message text;
- the conversation history that the current memory setting includes;
- the system instructions that define the assistant's behaviour;
- any files you attached to the message;
- the note, task, notebook or search context that the assistant needs in order to answer, and the
  results of any tool the assistant was allowed to call.

Your API key is sent to that provider as the authentication header for the request. It is sent
nowhere else. Read the chosen provider's own privacy policy to understand what it does with the
text it receives; those terms are between you and that provider, and Lucent has no visibility into
or control over them.

The assistant cannot change your data silently. Before it writes anything, it shows you what it
intends to do, and that confirmation step is on by default. Tools are optional: the local model has
its own tool switch, and the cloud assistant's tool access is a setting you can turn off. Turning
off either means the model can chat but cannot read or modify your notes and tasks.

### 5.2 Web search

Web search is off by default. When you switch it on and the assistant decides a search is needed,
Lucent sends the search query to a public search engine. You choose which one, or leave it on
Automatic, which tries them in turn until one answers: Bing, DuckDuckGo, Google, Brave, Mojeek,
Yandex, Baidu, Sogou, 360 Search, and the Wikipedia search API. No search API key is used and no
account is created. The query text leaves your device; the results come back to the assistant and
are included in its answer. The engine that answers sees the query and your IP address. Web search
needs the agent mode and, on the local model, the tool switch; Blackout Mode blocks it entirely.

### 5.3 Semantic search

Lucent's semantic search follows whichever assistant you are using, so there is nothing extra to
choose. While the local model is the active assistant, indexing stays on the device and is not
implemented in the current build, so nothing leaves it. While the cloud model is active, the text
being indexed is sent to your configured AI provider's embeddings endpoint to produce each vector,
which means note text leaves your device. Switching between the two models switches the behaviour
with it.

### 5.4 Cloud backup

Cloud backup is off by default. If you turn it on, you supply the address, username and password of
a WebDAV-compatible service you already have an account with, such as Nutstore, Nextcloud or Koofr,
or any other WebDAV server. Lucent uploads your `.lcb` backup files to the folder you name on that
server, and can list and download them again for a restore. The files are encrypted before they
are uploaded; the service stores the encrypted file, and the credentials you enter are sent to that
service to authenticate. Automatic cloud upload is a separate switch and is also off by default.

### 5.5 Update checks

Automatic update checking is off by default. When you turn it on, or when you ask Lucent to check,
the app requests the latest release information from the public GitHub releases API for this
repository and compares version numbers. The request contains no personal data beyond what any
HTTPS request carries, such as your IP address and a user agent naming Lucent. If a newer release
exists, Lucent offers it; the installer or APK is downloaded from GitHub only if you choose to
update.

### 5.6 Sharing and exports

Lucent sends nothing out on its own. Two features hand data to other applications, and both are
under your control:

- **Exporting.** Exporting a note or task to Markdown, Word, PDF or Excel, or exporting a backup,
  writes a file that you choose the location for. Exported documents are deliberately not
  encrypted, because the point of an export is to be readable elsewhere; anyone who obtains that
  file can read it. Keep it somewhere you consider appropriate.
- **Share-sheet integration.** Making Lucent a share target is off by default. When you turn it on,
  Lucent appears in other applications' share sheets so that text and files can be shared into it,
  and appears in your system's share sheet as a destination. Content shared into Lucent is stored
  in the same encrypted database as everything else. Lucent still does not send anything out by
  itself.

### 5.7 The on-device model

If you import a GGUF model, the assistant can run it with llama.cpp directly on the device. In that
mode nothing is sent anywhere: there is no account, no API key and no network request involved in
generating a reply. The model file stays on your device, and the model is unloaded when you leave
the app. Local tools are a separate switch and are off by default. GPU acceleration is optional and
can be turned off.

### 5.8 The agent toolkit

Beyond notes and tasks, the assistant can be given a real toolkit: a workspace folder it may read
and write, shell commands, documents, plugins, and connections to outside services. Each of those
is a separate switch in Settings → Advanced → Toolkit, and the parts that reach outside your device only do
anything when you turn them on and, by default, ask you first.

- **Workspace.** You choose one folder. Everything the assistant reads or writes lives inside it.
  Files outside it are refused unless you allow the "outside the workspace" permission, and nothing
  outside it can ever be written.
- **Commands.** Running a shell command asks you first unless you change that permission. Commands
  run on your machine with the working directory inside the workspace, with a timeout you set, and
  each one is written to the activity log.
- **Documents.** Word, Excel and PowerPoint files are written and read on your device by Lucent's
  own code. Nothing is uploaded to convert or create a file at any point.
- **Plugins.** Optional tools — a Linux userland, Python with document libraries, LibreOffice,
  Node.js, media and OCR tools — are downloaded only when you ask for them, from the upstream
  project or a public mirror, straight into your own storage. Lucent sends no telemetry about them.
- **Connections.** GitHub, Notion, Slack, Google Drive, OneDrive, GitLab, Jira, Linear, WebDAV and
  any MCP server you add are reached directly from your device with a token you supply. Only the
  request you asked for is sent, and only to the service you configured. Lucent has no server of
  its own in the middle, and stores the tokens encrypted on your device.
- **Tokens and the privileged shell.** GitHub access tokens are stored encrypted and are never shown
  again once saved; you may keep up to five. On Android, giving the assistant the Shizuku permission
  is a switch of its own, off by default, and only then can a command run with those privileges.
- **Sub-agent reports.** A sub-agent report you save by hand is written as a Markdown file in the
  workspace's `sub-agents` folder on your device, and nothing is sent anywhere.
- **Device control (Android).** If you enable it and switch on the accessibility service, the
  assistant can read the screen, tap, type, take screenshots, list and open apps, read
  notifications, and use the clipboard and sensors. Screen reading happens on the device; it is
  only ever sent anywhere if a tool result containing it goes to the assistant provider you chose,
  which is the same channel as any other message you send.
- **What stays local.** The workspace, snapshots taken before a file is changed, the activity log,
  remembered facts, and skill files all stay on your device. The activity log records the tool
  name, the arguments (shortened to 300 characters) and the outcome; it is stored encrypted and
  can be cleared at any time from the same settings page.

## 6. Backups

A backup is a single `.lcb` file that you create. It can contain notes, tasks, version history,
notebooks, conversations, attachments, settings and API keys, and optionally imported fonts and
local model files. You choose which of those to include, and where the file is written.

Every `.lcb` file is encrypted. There are two modes:

- **No password.** The file is encrypted with the app's built-in key. It restores on any device
  that has Lucent, with nothing but the file, which is convenient and also means anyone holding
  the file and a copy of the app can read it.
- **With a password.** The key is derived from your password with PBKDF2-HMAC-SHA256 over 210,000
  iterations, and exists nowhere else. This is stronger, with the usual trade-off: the same
  password is required to restore the file, it is not stored anywhere, and a forgotten password
  cannot be recovered by anyone, including the developer.

A password-protected backup can optionally carry a security question and answer so that the
password can be recovered later. That option is off by default and it makes the backup only as
strong as the weaker of the password and the answer, so choose a question whose answer nobody else
knows.

Automatic backups, when you enable them, write to a folder you choose, keep a number of copies that
you set, and can also upload to your configured WebDAV folder. The backup file itself is the same
encrypted container in every case. Where your backup files live, and how many copies exist, is
entirely your decision; so is deleting them.

## 7. Device integration

Lucent's privileged shell integration is optional, off by default, and requires software and
permission outside Lucent.

On Android, the app can use Shizuku to run privileged operations when you have installed Shizuku,
started its service and granted Lucent permission in the system. This exists to carry out actions
you explicitly ask for that ordinary applications cannot perform on their own, such as installing
an app update you downloaded. Lucent cannot start Shizuku, cannot grant itself the permission, and
does nothing through this path unless you have enabled the integration and granted the permission.
You can revoke it at any time in Shizuku or in Android's settings, and turn the integration off in
Lucent. Verification of what the privileged identity actually is can be seen in the app, which
shows the uid it obtained.

Lucent also declares the ordinary Android permissions it needs, each for a stated purpose; see
section 8.

## 8. Permissions Lucent requests

| Permission | Why Lucent asks for it |
|---|---|
| Network access (Android `INTERNET`) | Sending a message to the assistant provider you configured, web search when you enable it, WebDAV backup when you enable it, and update checks when you enable them. Nothing is sent without one of those being active. |
| Notifications (Android `POST_NOTIFICATIONS`) | Showing task reminders, and progress for a download, backup or generation you started. |
| Exact alarms (Android `SCHEDULE_EXACT_ALARM`) | Firing task reminders at the time you set rather than approximately. |
| Run at startup (Android `RECEIVE_BOOT_COMPLETED`) | Re-registering your reminders after the device restarts, so a reminder set for tomorrow survives a reboot. |
| Foreground service (Android `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`) | Keeping a backup, download or model generation running when the app is not in the foreground, instead of being killed halfway. |
| Biometric unlock (Android `USE_BIOMETRIC`; Windows Hello on Windows) | Letting you unlock the app with a fingerprint or face instead of typing the app-lock password. Optional, off by default, and only used for the local unlock. |
| Vibration (Android `VIBRATE`) | Typing haptics and tactile feedback. |
| Install packages (Android `REQUEST_INSTALL_PACKAGES`) | Installing an update that you chose to download, using the system installer. |
| Shizuku service permission (Android `moe.shizuku.manager.permission.API_V23`) | The optional privileged integration described in section 7. Declared only so that Shizuku can be used at all; it has no effect unless you install Shizuku and grant it. |
| All-files access (Android `MANAGE_EXTERNAL_STORAGE`) | Optional, off unless you grant it, and used only so that the workspace folder you choose can be read and written as ordinary files. Without it Lucent keeps the workspace inside its own storage. |
| Location (Android `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`) | Only read when the assistant calls its location tool, which itself only happens when device control is enabled and you have granted the permission. Lucent never tracks you in the background. |
| Termux commands (Android `com.termux.permission.RUN_COMMAND`) | Lets Lucent hand a command to Termux, which is the only supported way to run real command-line tools on Android. Termux must also be installed, configured to allow external apps, and started by you; otherwise the permission does nothing. |

Lucent does not ask for broad access to your files or your storage unless you turn the agent
toolkit on and point it at a workspace folder, in which case all-files access is what lets that one
folder be read and written as ordinary files; you can decline it and keep the workspace inside
Lucent's own storage instead. Elsewhere, files and folders are chosen by you through the system's
own document picker, and Lucent only receives the specific item you selected. Fonts and model files are the same: you pick them, and they are copied into the app's own
storage.

## 9. The controls you hold

Privacy in Lucent is a set of switches, all of them in your hands, all of them in Settings. The
main ones:

- **App lock.** Settings → Security. Choose a password, and optionally a security question so that
  a forgotten password can be reset. Only a salted hash is stored.
- **Unlock attempt limits.** Alongside the app lock. Set how many attempts are allowed in the first
  round and in later rounds; failures trigger escalating cooldowns that are remembered across
  restarts.
- **Erase after repeated failures.** Also alongside the app lock, off by default. After the number
  of wrong passwords you set, everything is erased. This is permanent and cannot be undone by
  anyone.
- **Biometric unlock.** Optional, off by default.
- **Blackout Mode.** Settings → Privacy. While it is on, nothing leaves the device: the cloud
  assistant, web search and network features are blocked, cloud surfaces are frozen, the app is
  hidden from the recents screen and cannot be screenshotted, the share target is removed, and a
  password is required every time you return. It overrides every other setting, and turning it off
  restores your previous settings.
- **Diagnostic logging.** Settings → Privacy, off by default and requiring explicit consent. It
  writes locally, may include text you type to the assistant, can be exported by you, and can be
  cleared and switched off at any time. Crash Shield, when enabled, holds it on so that caught
  errors are recorded.
- **Crash Shield.** Settings → Privacy, off by default. It catches errors inside the app so that
  the app stays open instead of closing, and records them locally. It states plainly what it cannot
  catch: a crash inside the on-device model engine, the system closing the app to free memory, or
  the system terminating it for not responding.
- **System integration.** Settings → Privacy, off by default. Controls whether Lucent can appear in
  other applications' share sheets.
- **Version history.** Settings → Privacy. Note history and task history can each be switched off,
  which stops new snapshots being kept.
- **Assistant provider and memory.** Settings → Assistant, then Cloud model or Local model. Choose
  the provider, or import a local model with no network at all. Each model keeps its own memory
  setting, which controls how much past conversation is included in each request; lower memory means
  less text leaves the device.
- **Web search.** Settings → Assistant → Personalisation. One switch, off by default, with the
  search engine beside it. It is hidden while the local model runs without agent mode, because
  nothing can use it then, and it comes back exactly as you left it.
- **Semantic search.** Settings → Assistant. It follows the active model: the local model keeps
  indexing on the device, the cloud model sends the text to your provider. Switch models to switch
  the behaviour, or leave the assistant off entirely.
- **Backup contents and password.** Settings → Data. Choose what a backup includes, and whether it
  is password-protected. Automatic backup, its folder, its interval and how many copies are kept
  are all yours to set, and it is off by default.
- **Cloud backup.** Settings → Data, off by default. Configure or clear your WebDAV credentials at
  any time.
- **Encryption check.** Settings → Security. Verifies on demand that stored values are sealed and
  open correctly.
- **Clear all data.** Settings → Data. See section 10.

## 10. Retention and deletion

Lucent keeps your data on your device until you delete it. Notes and tasks that you delete go to
the trash first and are removed permanently after 30 days, unless you empty the trash or delete all
data sooner. Version history is kept only while the corresponding history setting is on, and is
removed with the note or task it belongs to.

To delete everything:

- **Settings → Data → Clear all data** deletes every note, task and conversation, removes
  attachments, clears history, resets settings including your API key, deletes imported models and
  fonts, clears the local diagnostic log and switches logging off, and empties the app's cache. It
  cannot be undone. There are also narrower controls for clearing only notes, only tasks or only
  chat history.
- **Clearing the application's data** in Android's or Windows' system settings removes the app's
  storage in the same way.
- **Uninstalling Lucent** removes the application and its private storage.

Three things are outside Lucent's reach, and the app cannot delete them for you:

- backup files you exported to a folder, another device or a cloud service;
- backups already uploaded to your WebDAV service;
- anything you sent to an AI provider through the assistant, or submitted to a search service,
  which is governed by that provider's own retention policy.

If you want those gone too, delete the files and use the provider's own deletion controls.

## 11. Children

Lucent is a general-purpose notes and tasks application. It is not directed at children, it has no
account system, no advertising and no social features, and the developer collects no personal
information from anyone, including children. The app's data stays on the device of the person
using it.

## 12. Changes to this policy

If a future version of Lucent changes how data is handled, this policy will be updated and the date
at the top will change. Substantive changes, meaning ones that affect what leaves your device,
will be described in the release notes for the version that introduces them. The version of this
policy that applies to you is the one shipped with the version of Lucent you are running, and its
history is visible in the repository's commit history.

## 13. Contact

Questions, corrections and concerns about privacy in Lucent can be sent to
**yuan47578@gmail.com**.

---

Developer: Jessica Martinez

Copyright © 2026-2027 Jessica Martinez.

<div align="center">

[🇬🇧 English](./README.md) · [🇨🇳 简体中文](./docs/README.zh-CN.md) · [🇯🇵 日本語](./docs/README.ja.md) · [🇰🇷 한국어](./docs/README.ko.md)

</div>

<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### Modern · Minimalist · Quietly Overqualified

**A notes-and-tasks app with an assistant that can actually touch your data — sealed in an
encrypted database on your own device, fluent in four languages, and built from start to finish
by pressing one button on GitHub. It comes in pocket and desk sizes alike — an Android APK and a
Windows installer — with one shared heart, ready to travel further.**

![Lucent — Platform, Build, Interface, Assistant, Privacy, License](badges/badges.svg)

</div>

---

## The general idea

Most note-taking apps offer you a sporting choice of two from three: the pretty one, the private one,
or the clever one. Choose your two and live with the disappointment. Lucent declines the arrangement.
Everything you write is sealed in an encrypted database that never leaves your device. The assistant
can be a cloud model you pay for, a model running on the machine in front of you, or nothing at all.
And it is assembled without a line of local tooling: press a button on GitHub, wander off, and an APK
or a Windows installer is waiting when you return.

## One app, wherever you put it

Lucent is **one product with a single shared heart**: the house rule is that every feature lives on
every platform it ships to. Your pocket and your desk speak the same language:

- **`:app`** — the Android application (Kotlin and Jetpack Compose, Room over SQLCipher, llama.cpp
  through the NDK). Built by `.github/workflows/build.yml` into a signed release APK.
- **`:desktop`** — the desktop application, Windows today (Compose for Desktop, pure JVM, SQLite over
  JDBC, llama.cpp compiled as a DLL). Built by `.github/workflows/build-windows.yml` into a
  double-click `.exe` installer.
- **`:shared`** — the single source tree both platforms compile: business logic, data, most of the
  interface, one translation catalogue in four languages. Edit once; both machines change.

Everything that can be shared lives in `shared/`; the few things that cannot — `SettingsRepository`,
`Daos`/`Db`, `DocumentExport` and the largest screens — stay once per platform because their seams
run deep. The native build stages are each optional, so a hiccup in one leaves you with a working app
rather than a waiting room.

## The assistant with hands, not merely opinions

This is the feature that defines Lucent. Bring your own model — OpenAI, Anthropic and Google request
shapes are spoken fluently, several profiles kept and switched in one tap — or run the whole thing on
the device. Say nothing at all and the assistant isn't there; the notes app loses nothing.

What makes it worth having is that it can *act*: create, read, edit, colour, pin, archive and delete
notes; create, complete, reopen, schedule and prioritise tasks; search with a real filter language;
and attach, rename or remove files. Before it changes anything it shows you precisely what it
intends — in your own language, in a dialog that is itself the editor, with every arguable field
editable before a single byte is written. Your answer is always the final word. Someone with an
opinion and no hands is a chat; this is a butler who has been told to knock first.

Conversations run as long as you like and travel with you; the model's reasoning can be shown as a
collapsible trace, one line per tool call.

## The workshop behind the assistant

Notes and tasks are the front of house. Behind it, a switch away in Settings → Agent toolkit, there
is a workshop the assistant is allowed into. It has a workspace folder of your choosing, and inside
it, real tools: read, write, search, edit with a diff, snapshot and roll back. It can run commands
with a timeout and a working directory, keep long ones as background jobs, and show you the output.
It can create and edit Word, Excel and PowerPoint files — the formats are written natively, by
Lucent, with no office library underneath — and read PDFs, render them to pictures, split and merge
them. It can clone, diff, commit and push with Git, raise and read GitHub issues, pull requests and
CI logs, fetch and read web pages, query SQLite, and run a job inside Docker, PRoot or a plain
shell, whichever this machine can offer.

It remembers three ways: this conversation, this project, and you. It keeps a plan you can watch,
writes down skills so it stops re-learning your conventions, and can call for sub-agents when a
task is heavy enough to deserve them — a switch in Personalisation, off unless you want it.

Anything large stays out of the installer: a Linux userland, Python with its document libraries,
LibreOffice, Node.js, a browser engine, OCR and media tools are listed as plugins, downloaded only
if you ask, from the project's own servers or a fast mirror, whichever answers first. Permissions
are a page of their own — read, write, delete, commands, network, Git, GitHub, browsing, the space
beyond the workspace, and the device itself — each set to allow, ask or block, and every tool call
written to an activity log you can read and clear.

On a phone the same workshop reaches the device: read the screen, tap, type, swipe, take a
screenshot, list and open apps, read notifications, use the clipboard and the flashlight, and run
real command-line tools through Termux. It also speaks MCP, so any server you point it at becomes
part of the toolkit.

## Notes that remember what they used to be

Every meaningful edit is snapshotted, so you can always see what a note used to say and restore it
when a confident rewrite turns out to have been optimism. Type `[[Shopping list]]` and it becomes a
tappable link; point at a title that doesn't exist yet and the link glows red until a tap politely
brings the note into existence. Markdown renders when you want it and stays exactly as typed when you
don't. Checklists are first-class citizens: reword items in place, open a roomy pop-out editor when a
quick item develops ambitions. Tags, colours, pinning, individually encrypted attachments, rich text,
a doodle canvas for thoughts words can't reach, and a private area with its own lock — with drafts
beside the trash, so an unfinished thought is never an abandoned one.

A blank note offers four one-tap starters — journal, meeting, project idea, checklist — and then the
real trick: templates of your own. Save one and it greets you on every future blank page; long-press
to edit or retire any of them, built-ins included.

## Tasks with due dates that actually mean something

Subtasks, priorities, repeat schedules, and reminders that survive a reboot. Due dates are parsed from
ordinary language — *next Friday at 6* becomes a genuine timestamp with a genuine alarm — and repeat
cadences are first-class rather than a clever sentence that eventually gives up. Completing a task
ticks its whole checklist off with it, and completed tasks take themselves to a screen of their own.

## Or run the whole thing on the device itself

Import a `.gguf` file (or a `.zip` with one inside) and the assistant answers using llama.cpp running
directly on the device — no account, no API key, no network, and the model unloaded the moment you
leave the app. Roughly 1–4 GB Q4 models hit the sweet spot on a phone; a desktop can afford more
optimism. Tools are opt-in locally and GPU acceleration is a choice made after a plain warning: the
CPU always works, and a GPU that disagrees falls back gracefully. Vision is optional too — import an
mmproj file and the assistant will look at a photograph and discuss it like a mildly clairvoyant
librarian.

## Four languages, switched without ceremony

English, 中文, 日本語, 한국어 — every screen, dialog, date format and template. Switch the language
and the whole app follows on the same frame, because there is one catalogue and it is what the
interface reads: each word kept once, by construction rather than by care.

## The look of it: made, unashamedly, of glass

A living gradient drifts behind frosted panels that blur whatever passes beneath them, never quite
repeating itself, at a price the device agreed to in advance. A generous spread of palettes across
eight style families, with an auto-cycle or random companion that ambles through them. Light, dark,
system and a gallery of Monet-tinted themes — and on Android 12 or later the palette borrows your
wallpaper's plan for the day. Widgets bring the same glassy surface to your launcher; on Windows the
app slips into the tray and a sidebar takes over from the bottom tab bar, because a large monitor
deserves better than a phone layout stretched sideways.

## The lock that counts, and the backup that leaves with you

The app lock is a real brute-force policy rather than a polite request: escalating cooldowns, a
persisted counter that survives reboots, an optional security question, an optional self-destruct, and
a fingerprint (Android) or Windows Hello (Windows) that is politely out of office for the whole
cooldown. The database is encrypted at rest, attachments individually, and backups are a single
password-protectable `.lcb` file carrying notes, tasks, history, chats, attachments and settings
across devices — previewed before a single item is changed, and armed to run automatically. A backup
nobody can read is a keepsake rather than a backup, so this one is read on the way out and again on
the way in.

## Privacy that is structural, not merely promised

Nothing leaves the device until you export it or give a model a reason to look. Share-sheet
integration is off by default; diagnostics are off by default and kept locally; the assistant services
you connect are entirely your choice; cloud mirroring is a module you configure yourself, over WebDAV.
The one deliberate exception to encryption — exporting to Markdown, Word, PDF or
Excel — is a file you can open anywhere else, which is rather the point.

## Rust, but only where it earns its keep

Two hot paths are written in Rust and reached through JNI — the PBKDF2 and AES-256-GCM routines behind
backups and attachment encryption, and the mathematics behind the drifting background. Both fall back
to identical Kotlin when the native library isn't present, so the app is never held hostage by a
compiler.

## Building it (yes, from a phone, in your dressing gown)

No Android Studio, no local SDK, no command line. Push to GitHub, open **Actions**, run the workflow
you want, and download the result — a properly signed release or a double-click installer. The
workflows live in the repository, so the recipe is in the box rather than in the author's head, and the
build is as inspectable as the code.

## Project layout

```
shared/       THE single shared source tree — business logic and most UI live here ONCE, and both
              :app and :desktop compile it. Edit a file here to change both platforms together.
app/          Android module (:app) — Activity shell, Room database layer, JNI bridges, widgets,
              and other genuinely Android-bound code only
desktop/      Desktop module (:desktop) — Compose for Desktop shell, the android.* JVM shims,
              the JDBC database core, native CMake build for the engine DLL
rust/         The Rust accelerator (shared across platforms)
.github/      build.yml (Android APK), build-windows.yml (Windows installer), check.yml
```

## Where it goes next

Lucent is a great deal of application for a to-do list, and it has never once apologised for the fact.
Notes with memory, tasks with teeth, an assistant with hands and manners, encryption that keeps its
promises and a surface worth looking at. Take it for a week and see what you notice; take it for a
month, and notice that you stopped noticing.

## With thanks to the giants whose shoulders these are

Underneath the glass, Lucent is a great deal of other people's excellent work. It would be poor
manners — and, in one or two cases, an outright licence violation — not to say so out loud. The full
texts and copyright notices live in **[`THIRD-PARTY-NOTICES.md`](./docs/THIRD-PARTY-NOTICES.md)**; the
short version, with our gratitude, is this:

| Borrowed brilliance | Doing the job of | Under |
|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) & [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | the language, and its patience with concurrency | Apache-2.0 |
| [Jetpack Compose & AndroidX](https://developer.android.com/jetpack/androidx) | the Android half of the interface | Apache-2.0 |
| [Compose Multiplatform & Skiko](https://github.com/JetBrains/compose-multiplatform) | the desktop half of the interface | Apache-2.0 |
| [Material Icons](https://github.com/google/material-design-icons) | the small pictures that mean things | Apache-2.0 |
| [Haze](https://github.com/chrisbanes/haze) — © Chris Banes | all that fashionable blur | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) — © Square, Inc. | talking to the cloud | Apache-2.0 |
| [Apache PDFBox](https://pdfbox.apache.org/) | PDFs on the desktop | Apache-2.0 |
| [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) — © Taro L. Saito et al. | the desktop's way into SQLite | Apache-2.0 |
| [SQLite](https://www.sqlite.org/) | the database itself, quietly running the world | Public Domain |
| [SQLCipher](https://www.zetetic.net/sqlcipher/) — © Zetetic LLC | the lock on that database, on Android | BSD-style |
| [llama.cpp & GGML](https://github.com/ggml-org/llama.cpp) — © Georgi Gerganov & contributors | an entire language model, on your own silicon | MIT |
| [org.json](https://github.com/stleary/JSON-java) — © JSON.org | reading JSON on the desktop | JSON License |

### And the notes apps we studied

Six mature notes-and-tasks applications were read, poked, and taken to tea for this project — not
for their code, which Lucent does not bundle, but for the things their authors figured out first.
Their fingerprints are on Lucent's structure and its manners, rather than on its binaries, and a
design debt repaid loudly is the least we can do:

| Studied for | What Lucent borrowed | Their words |
|---|---|---|
| [Quillpad](https://github.com/quillpad/quillpad) | the two-level drawer — pin where you can reach it, configuration one level down | GPL-3.0 |
| [Omni-Notes](https://github.com/federicoiosue/Omni-Notes) | the honesty of system pickers and long-press actions that explain themselves | GPL-3.0 |
| [OpenNote-Compose](https://github.com/YangDai2003/OpenNote-Compose) | the dynamic-colour priority model — wallpaper first, stored choices never destroyed | GPL-3.0 |
| [AppFlowy](https://github.com/AppFlowy-IO/AppFlowy) | saying what is happening in the open, in the settings page and elsewhere | AGPL-3.0 |
| [Logseq](https://github.com/logseq/logseq) | that a notes app should feel like a place, not a dashboard | AGPL-3.0 |
| [MarkLeaf](https://github.com/jeiel85/markleaf-android) | that the editorial surface stays calm while the configuration waits its turn | Apache-2.0 |

Lucent's own licence governs everything you install; the projects above are honoured as sources of
ideas and arrangements, not as included works. The structure is ours; the courtesy is theirs.

A particular word for **SQLCipher**, whose BSD-style licence asks — not unreasonably, given it is
the thing keeping your diary shut — that its copyright and notice be reproduced somewhere a user can
actually find them. So they are, in the notices file above; if you ship a build of Lucent, keep them
findable. Fonts, incidentally, no longer appear in that table at all: Lucent bundles none, and the
ones you import stay your own files under whatever terms you hold them.

## Licence, and the one small thing it asks in return

Lucent is released under the **MIT Licence** — see [`LICENSE`](./LICENSE). Do very nearly whatever
you like with it: use it, change it, fold it into something commercial, build something better on
top and never write to thank us. The single, entirely reasonable condition is that our copyright
notice and the licence text come along for the ride in any copy or substantial portion of the code —
so if you reuse Lucent, keep the `LICENSE` file (and the name on it) with what you ship, and we are
square. The third-party components above make their own, similarly modest requests; honour those in
the same spirit and everyone stays friends.

Privacy policy: **[`PRIVACY/PRIVACY.md`](https://github.com/Yuan0-o/Lucent/blob/main/PRIVACY/PRIVACY.md)** — what Lucent stores, what leaves your device, and every control you hold.

## Contributing

Should you be seized by the urge to improve Lucent, we should be quietly delighted. Bug reports, thoughtful suggestions, and pull requests submitted with good grace are entirely welcome. We ask only that everyone remain strictly civil, keep the tea warm, and treat fellow contributors with the courtesy one expects in a respectable reading room.

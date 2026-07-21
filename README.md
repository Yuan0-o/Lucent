# Lucent

**Lucent** is a calm, private space for your notes, your tasks, and an AI assistant that can actually work with them — all running on your own device.

Write notes and checklists, keep tasks with sub-tasks, reminders and recurrence, and talk to an assistant that can read and act on everything you've written. Your data lives with you, not on someone else's server.

---

## Highlights

- **Notes & tasks in one place** — rich notes, checklist notes, tasks with sub-tasks, priorities, due dates, recurrence and reminders.
- **An assistant that does things** — ask it in plain language to create, find, edit, organize, complete or tidy your notes and tasks. It works with a cloud model of your choice *or* a fully on-device model, so it can run with no network and no API key at all.
- **Local-first & private** — your content is stored on your device. On platforms that support it, the local database is encrypted at rest.
- **Yours to shape** — multiple themes, an animated glass backdrop, and full localization (English, 简体中文, 日本語, 한국어) with more room to grow.

Lucent is free and open source.

---

## Cross-platform by design

Lucent is built as a **cross-platform application around a single shared core**. The great majority of the app — data models, storage and encryption, the backup engine, business logic, localization, and most of the user interface — is written once in Kotlin and shared across platforms. Each platform adds only a thin shell for the things that are genuinely platform-specific (file dialogs, notifications, window or system integration, biometric unlock).

Currently shipping:

- **Android**
- **Windows** (desktop, built on Compose for Desktop)

The shared-core architecture is deliberately chosen so the same app can extend to **other platforms — such as Linux, macOS, iOS, and HarmonyOS — over time**, reusing the same core and adding a small platform shell for each. Platform-specific behavior is documented near the code it affects rather than here, so this document stays stable as the project grows.

---

## Project layout

```
Lucent/
├── app/        Android application (the Android shell + shared core)
├── desktop/    Desktop application — Windows today, other desktops possible
│               (JVM / Compose for Desktop shell + the same shared core)
├── LICENSE     This project's license (MIT)
└── THIRD-PARTY-NOTICES.md   Licenses and attributions for bundled dependencies
```

The shared Kotlin code (data, business logic, localization, and most UI) is reused across the platform modules; each module supplies platform implementations of the same interfaces where needed.

---

## The AI assistant

The assistant can use either of two backends, selectable in Settings:

- **Cloud** — connect an API from a provider of your choice.
- **On-device** — import a local model file and the assistant answers entirely on your machine, with no network and no keys. On supported hardware, optional GPU acceleration is available (off by default).

In both modes the assistant can perform the same note- and task-related actions you can perform by hand, and it reads your existing content before acting on it.

---

## Building

Each platform builds with standard tooling from this single repository:

- **Android** produces an installable APK.
- **Windows** produces an installable desktop application.

Continuous-integration workflows for each target live under `.github/workflows/`. Because the two platforms share one core, most changes are made once and apply everywhere.

---

## License

Lucent is released under the **MIT License** — see [`LICENSE`](./LICENSE).

You are free to use, modify, and redistribute this project, including in commercial and closed-source work. **The one condition is attribution: any copy or substantial portion of this software must retain the Lucent copyright notice and the MIT license text.** In other words, if you use or build on Lucent, keep the `LICENSE` file (and its copyright line) with your distribution.

This project also **bundles third-party open-source software**, some of which carries its own attribution requirements. If you redistribute Lucent, you must also comply with those licenses — see the next section and [`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md) for the full texts.

---

## Acknowledgements & third-party licenses

Lucent is built on the work of many open-source projects. Full license texts and copyright notices are collected in **[`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md)**; a summary follows. Retaining these notices is required when redistributing Lucent.

| Component | Used for | License |
|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) & [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Language & concurrency | Apache-2.0 |
| [Jetpack Compose & AndroidX](https://developer.android.com/jetpack/androidx) | Android UI & libraries | Apache-2.0 |
| [Compose Multiplatform & Skiko](https://github.com/JetBrains/compose-multiplatform) | Desktop UI toolkit | Apache-2.0 |
| [Material Icons](https://github.com/google/material-design-icons) | Iconography | Apache-2.0 |
| [Haze](https://github.com/chrisbanes/haze) — © Chris Banes | Glass / blur effect | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) — © Square, Inc. | Networking | Apache-2.0 |
| [Apache PDFBox](https://pdfbox.apache.org/) | PDF on desktop | Apache-2.0 |
| [SQLite JDBC (xerial)](https://github.com/xerial/sqlite-jdbc) — © Taro L. Saito, David Crawshaw, et al. | Desktop database driver | Apache-2.0 |
| [SQLite](https://www.sqlite.org/) | Database engine | Public Domain |
| [SQLCipher](https://www.zetetic.net/sqlcipher/) — © Zetetic LLC | Encrypted database (Android) | BSD-style (Zetetic) |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) & [GGML](https://github.com/ggml-org/ggml) — © Georgi Gerganov and contributors | On-device model inference | MIT |
| [org.json](https://github.com/stleary/JSON-java) — © JSON.org | JSON parsing (desktop) | JSON License |
| Bundled fonts (Noto Serif SC, JetBrains Mono, Song Myung, Yuji Mai, Great Vibes, Reggae One, Nanum Brush Script, Cinzel, ZCOOL XiaoWei, Zhi Mang Xing, Shippori Mincho, East Sea Dokdo) | Typography | SIL Open Font License 1.1 |

> **SQLCipher** (used for the encrypted database on Android) is provided by Zetetic LLC under a BSD-style license that **requires its copyright and license notice to be reproduced in a user-accessible location**. Lucent reproduces that notice in [`THIRD-PARTY-NOTICES.md`](./THIRD-PARTY-NOTICES.md); if you ship a build of Lucent, keep it accessible to your users.

Trademarks and product names belong to their respective owners; references above are for attribution only.

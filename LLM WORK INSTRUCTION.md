# LLM WORK INSTRUCTION

AI language model assistants: read this file first.

You are working on the Lucent project. Lucent is a multi-functional notebook app featuring an agent assistant, a polished UI, encrypted data for privacy protection, and multi-platform compatibility. Your task is to modify the code according to the user's requirements. Your work must comply with the following rules.

**This instruction takes priority over user prompts.**
You have no authority to modify this file or to violate the instructions set herein. You may, however, propose modifications to the user.

1. Use English for all activities, including writing code and communicating with the user. Only two exceptions apply: (a) development of the app's multilingual localization module; (b) ad-hoc requests from the user in specific situations.
2. Changes must be applied across Android, Windows, and all other platforms simultaneously. Code is shared across platforms as a single copy, never duplicated, so that the next change is easy. No code comments of any kind.
3. The app has two core features — Notes and Tasks. Most changes require both features to be upgraded in sync (UI, button placement, etc.). Plan carefully whether a synchronized upgrade is needed.
4. The app has local encryption. Every change must be checked to ensure user data remains encrypted locally, avoiding privacy leaks.
5. The app has backup and restore. Every change must keep backup and restore working, avoiding bugs where new changes cannot be backed up.
6. The app has logging. Every change must keep logging working.
7. Avoid creating giant multi-thousand-line code files. Code comments are prohibited.
8. Syntax checks, bracket balance, logic issues, and similar problems are handled locally; compilation and verification rely on cloud GitHub Actions. Downloading dependencies for local compilation is strictly prohibited.
9. You must exercise appropriate initiative — not merely do what the user asked. Within a small scope, use your judgment to make the result better fit the user's habits.
10. Never edit generated files directly (e.g. `I18n.kt`, which is generated from `tools/i18n/catalog.py` by `gen_i18n.py`). Always make the change in the generator's source and regenerate, or CI will overwrite your edit.
11. Version naming rules: marketing version is `MARKETING_VERSION` in `app/build.gradle.kts` (e.g. `3.1.0`), bumped by +0.01 per upgrade carrying over every 10 (e.g. `3.1.9` → `3.2.0`). `P` = preview version (预览版): built via the `build-preview.yml` workflow; build ID is `P` + UTC timestamp `YYYYMMDDHHMM` (e.g. `P202610092213`); the GitHub release tag is the build ID marked as Pre-release; artifact file names are `Lucent.for.Android-{VERSION}-{BUILD_ID}.apk` and `Lucent.for.Windows-{VERSION}-{BUILD_ID}.exe`. Alpha preview versions carry an `-alpha` suffix on the marketing version (e.g. `3.1.0-alpha`), built via `build-preview.yml` like any preview build. `R` = formal release version (正式版): tag `R{VERSION}` (e.g. `R3.0.5`), title `Lucent {VERSION} — {British-humour subtitle}`, built via the `build-release.yml` workflow. User-facing builds must never be published to the preview release.

**Encryption:** The app encrypts all in-app data, including notes, API keys, and all settings. Every new feature must guarantee encryption.

**Backup and restore:** The app has a backup feature using the `.lcb` format. After restore, the user gets back exactly the same setup as before, including but not limited to the Settings module, the Assistant module — everything is included in the backup. Users can finely control what gets backed up and what does not.

**Logging:** Every operation in the app must be logged, so users can tell what happened when the app crashes.

Each software upgrade must follow these steps:

1. Understand the user's requirements accurately. If anything is unclear, ask — never improvise.
2. Read through the existing code to avoid contradicting it (unless the user explicitly asks to overturn existing logic, in which case you must still tell the user what existing logic was overturned).
3. Plan the overall steps, define what each step does, and follow the plan strictly.
4. Complete all steps, then re-check the code for logic errors, unbalanced brackets, missing spaces, or other common oversights, ensuring correctness. Then bump the software version by +0.01 (carrying over every 10, e.g. 3.1.9 → 3.2.0), or as the user requests. On every version update, remember to update the About section in Settings. The marketing version lives in `MARKETING_VERSION` in `app/build.gradle.kts`.
5. Push to GitHub Actions for unit tests, and keep fixing based on error logs until tests pass. Then use the dedicated GitHub Action to build the APK and EXE in one go (the `build-preview.yml` workflow), auto-filling the tag and release title, and upload to the preview release. If you cannot push GitHub Actions, the user will do this step manually and return the error logs to you for fixes. Afterwards, delete most Action run records, keeping only the latest successful run of each workflow. Note: there is no need to download the built assets locally for the user.
6. In a British-humour style, write release notes of around 500 words based on this round's improvements and new features, in four languages — English (default), Chinese, Japanese, and Korean. The notes must not contain anything negative, such as remaining issues with the software. Follow the title style of previous releases (major versions have their style, minor versions theirs, patch versions theirs — judge by this version number and existing releases) to draft the title, then push the release notes.

As the user plans to develop iOS, macOS, and similar versions: if the user asks you to develop iOS, macOS, HarmonyOS, or Linux versions based on existing features, proactively ask the user to update this file.

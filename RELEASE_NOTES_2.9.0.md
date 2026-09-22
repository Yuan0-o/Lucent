# The Access You Deserve

There are apps that keep their hands politely behind their backs, and there are apps that reach into the system, roll up their sleeves, and get things done. Lucent has always been the latter — it just needed the right credentials.

**Shizuku** is now a first-class citizen on Android. The app requests elevated privilege through Shizuku's clean permission flow, and once granted, a whole new category of system-level operations becomes possible. On Windows, the same intent is served through a UAC elevation prompt — the shield icon appears, you click "Yes", and the app gains the same authority on the desktop that Shizuku provides on the phone. One privilege chain, two platforms, zero compromises.

The first feature to use this new power is **auto-update**. Toggle it on in the new About page, and every time you open the app it silently checks GitHub for the latest release. When a new version is found, it downloads and installs — via Shizuku on Android, via an elevated installer on Windows. No manual downloads, no "check for updates" rituals. The app keeps itself current while you keep yourself busy.

The **About page** itself is new, sitting at the bottom of the settings list like a modest afterthought that turns out to have quite a lot to say. It shows the version number (v2.9.0), the build number, the copyright line that keeps the lawyers happy, a link to the GitHub repository, the developer's contact email, and a summary of the open-source licenses that make the whole thing possible. The "Check for updates" button lets you trigger a manual check whenever you feel the need — though with auto-update on, you probably won't.

Token counting, meanwhile, has had its quiet moment in the spotlight. The estimator now caches repeated text so that system prompts and common phrases aren't re-counted every time they appear. The cache uses a WeakHashMap that respects memory pressure, so the app doesn't trade one inefficiency for another. A new `labelFromUsage` helper accepts separate prompt and completion counts, making future integration with API usage data straightforward.

The README has also learned four new languages — Chinese, Japanese, and Korean join the English original, each written with the same playful voice and the same complete set of information. A language switcher at the top lets visitors pick their preferred version without ceremony.

**Shizuku privilege elevation** — Android system access through reflection-based Shizuku integration.
**UAC elevation** — the same authority on Windows, through the standard shield dialog.
**Auto-update** — fetch and install new versions silently, on both platforms.
**About page** — version, build, copyright, GitHub, contact, licenses, update check.
**Token cache** — repeated text is counted once, cached efficiently.
**Four-language README** — English, 中文, 日本語, 한국어, with a clickable switcher.

Everything that was encrypted is still encrypted. Everything that backed up still backs up. The assistant still assists, the notes still remember, and the tasks still do their homework. Only the reach has changed — and the reach, as it turns out, was what was missing.
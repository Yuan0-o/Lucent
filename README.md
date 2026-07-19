# Lucent

<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### Modern · Minimalist · Quietly Overqualified

**A notes-and-tasks app with an assistant that can actually touch your data —
sealed in an encrypted database on your own phone, fluent in four languages, and built
from start to finish by pressing one button on GitHub and going off to put the kettle on.**

![Lucent — Platform, Build, Interface, Assistant, Privacy, License](badges/badges.svg)

</div>

---

## The general idea

Most note-taking apps offer you a sporting choice of two from three: the pretty one, the private
one, or the clever one. Pick any two and learn to live with the disappointment. Lucent politely
declines this arrangement. Everything you write is sealed in an encrypted database that never leaves
your handset. The assistant can be a cloud model you pay for, a model running on the phone in your
pocket, or — if you are the sort of person who keeps notes the way one keeps a diary, which is to say
resentfully and alone — nothing at all. And the whole thing is assembled without a line of local
tooling: you press a button on GitHub, wander off, and an APK is waiting when you return.

It is, we admit, a lot of app for a to-do list. We have made our peace with this.

## Notes that remember what they used to be

A note is a title, a body, some tags, a colour, and — should prose feel like too much commitment on a
Monday — a checklist instead. Beyond the basics, it quietly does the things you only miss once
they're gone:

- **Version history.** Every meaningful edit is snapshotted, so you can read exactly what a note said
  last Tuesday and restore it when today's confident rewrite turns out to have been a mistake. It
  usually was.
- **Links between notes.** Type `[[Shopping list]]` and it becomes tappable; the note you pointed at
  grows a *linked from* reference in return, like a very well-mannered correspondence. Point at a
  title that doesn't exist yet and the link shows red — tap it, and that note is politely brought
  into existence.
- **Markdown, entirely optional.** Headings, bold, code and lists render when you want them and stay
  exactly as typed when you don't. Links and formatting are two independent switches, because
  deciding your text should look plain shouldn't quietly sever every connection you've drawn — an
  indignity an alarming number of apps consider acceptable.
- **Templates.** Four one-tap starters — journal, meeting notes, project idea, checklist — offered
  only on a blank note and never, ever over your actual work.
- **Archive and trash, kept firmly separate**, because "I am finished with this" and "I would like
  this gone" are different sentiments, and conflating them is how things you wanted end up in the bin.

## Tasks with due dates that actually mean something

Subtasks, priorities, repeat schedules, and reminders that survive a reboot — the last of which
sounds like a low bar until you meet the reminders that don't. Due dates are parsed from ordinary
language, so *next Friday at 6* becomes a genuine timestamp with a genuine alarm attached, rather
than a note reading "next Friday" that helpfully reminds you of nothing at all. Completed tasks take
themselves off to their own screen instead of loitering at the bottom of the list, radiating a faint
air of accomplishment you did not ask for.

## An assistant with hands, not merely opinions

Bring your own model. Lucent speaks the OpenAI, Anthropic, and Google request shapes fluently,
fetches the live model list from whichever endpoint you point it at, and lets you keep **up to five
API profiles** to switch between with a single tap — one for the clever expensive one, one for the
cheap cheerful one, and three for the indecision in between.

What makes it genuinely useful is that it can *act*. **Twenty-six tools** are wired to your real
data: it creates and edits notes and tasks, sets priorities and due dates, ticks off subtasks, pins,
archives, searches with proper filters, and attaches or removes files. Crucially, before it does any
of this it shows you precisely what it intends to do, in your own language, and then waits for a yes
like a butler hovering at a respectful distance. Replies stream in as they're written, conversations
are kept and switchable, and you decide how much history rides along with each message — this one,
this conversation, or across the lot — because that choice is a bill as much as it is a memory.

## Or run the whole thing on the phone itself

Feeling anti-social, or simply on a train through a tunnel? Import a `.gguf` file — or a `.zip` with
one inside, which is unpacked for you — and the assistant answers using **llama.cpp running directly
on the device**. No account, no API key, no network, no small monthly fee that you keep meaning to
cancel. The model is unloaded the moment you leave the app, so it isn't quietly sitting on your RAM
like an uninvited guest. Roughly 1–4 GB Q4 models hit the sweet spot of speed and coherence on a
typical phone; anything larger is an act of optimism.

Two things about on-device mode are yours to decide, and both default to the cautious option because
a phone is not a workstation and we would rather it didn't burst into flames:

- **Tools are opt-in.** Off by default, because driving the tool protocol costs extra thinking per
  reply and older phones feel every millisecond. Turn them on in Settings — after a short, honest
  warning — and the local assistant can edit your notes and tasks exactly like the cloud one, network
  or no network.
- **CPU or GPU, your call.** It runs on the CPU by default, which works on every device and whose
  worst-case behaviour is "a bit slow" rather than "a bit on fire". Prefer speed and feeling brave?
  Switch it to the GPU (Vulkan) — again after a warning — and if your particular phone's graphics
  driver turns out to disagree with the whole idea, it quietly falls back to the CPU rather than
  taking the afternoon down with it.

Web search and cross-conversation memory stay off on device throughout, on purpose: the point of
local mode is that it asks nothing of the outside world, and we intend to keep that promise.

## Four languages, switched without ceremony

English, Simplified Chinese, Japanese, and Korean — **676 interface strings** covering every dialog,
toast, and accessibility label, with English as the sensible default and the other three a tap away.
Switching is immediate: no restart, no reload, no theatrical flash of the old language while the new
one gets its coat on. And it studiously never touches your content — your notes, your tasks, and the
assistant's replies stay in whatever language you wrote them, because translating a person's private
thoughts without being asked is precisely the sort of thing a well-behaved app does not do.

Each script also brings **three typefaces of its own** — twelve in all, tidily grouped by language
and labelled in their own native names, so a serif for your journal or a brush script for your
grievances is never more than a couple of taps away.

## Privacy that is structural, not merely promised

Anyone can *promise* privacy in a paragraph. Lucent builds it into the plumbing:

- The database is **SQLCipher-encrypted**, and attachments are encrypted individually on disk — so a
  lost phone is an inconvenience, not a confession.
- **App lock** with an optional security question, where neither the password nor the answer is ever
  stored — only a salted hash. We cannot read them. This is by design, not by forgetfulness.
- **Backups** are a single encrypted `.lcb` file holding notes, tasks, history, chats, attachments
  and settings. It defaults to a portable built-in key so it restores anywhere, or takes a password
  of your own for the real thing — and importing shows you exactly what's inside *before* it changes
  a single item, which is more courtesy than most restore dialogs can be bothered with.
- Lucent is invisible to other apps until you say otherwise: share-sheet integration is off by
  default, and diagnostic logging is both off by default and stubbornly local-only.
- Exports are the one deliberate exception — **Markdown, Word, PDF, or Excel**, unencrypted — because
  a file you cannot open anywhere else is not an export; it is a hostage.

## Made, rather unashamedly, of glass

Soft blobs of colour drift behind frosted panels that blur whatever passes beneath them, which is
either sophisticated restraint or showing off, depending on your mood. There are **15 palettes**
across solid, gradient, and classic families, or an auto-cycle that ambles through the lot; light,
dark, system, and Monet-tinted themes; and a typeface picker that previews each face drawn in itself,
because describing a font in words is a fool's errand. **Seven home-screen widgets** carry the same
glassy surface out to your launcher: new note, new task, assistant, quick actions, task summary,
today's tasks, and a pinned note.

## Rust, but only where it earns its keep

Two hot paths are written in Rust and reached through JNI: the PBKDF2 + AES-256-GCM routines behind
backups and attachment encryption, and the maths that keeps the background animation drifting
smoothly. Both are byte-for-byte compatible with the Kotlin they replace, and both fall back to that
Kotlin automatically when the native library isn't present — so the app is faster where Rust is
available and precisely identical where it isn't. Rust for its own sake is a hobby; this is Rust with
a job.

## Building it (yes, from a phone, in your dressing gown)

No Android Studio, no local SDK, no command line, no ceremonial incantations. Push to GitHub, open
**Actions → Build signed release APK → Run workflow**, and download the signed APK when the tick
turns green. The final step even runs `apksigner` and refuses the build if the result isn't a
properly signed *release* — so a misconfigured secret can't quietly hand you a debug-keyed
disappointment.

The signing key lives in encrypted repository secrets and is decoded into a throwaway runner
directory for exactly one build, then vanishes with the runner; it is never committed. The fiddly
native bits — llama.cpp at a pinned tag, the Rust cross-targets, the Vulkan shader compiler and its
headers — are fetched in clearly labelled steps with retries, so a network hiccup reads as an honest
failure in an obvious place rather than a cryptic one buried six layers deep in Gradle. Want a
smaller, CPU-only build? Add `-PcpuOnly` and the GPU backend stays home.

Targets Android 8.0 (API 26) and up, shipping `arm64-v8a` and `armeabi-v7a` — which is to say, every
phone anyone has actually owned this decade.

## Licence

Released under the [MIT Licence](LICENSE). Do very nearly anything you like with it; simply keep the
notice, and try to think kindly of us. That's the whole deal.

## Acknowledgements & third-party software

Lucent would be a considerably shorter story without a great deal of other people's excellent work.
The app bundles or builds against the open-source projects below, each of which keeps its own
copyright and licence; our sincere thanks to their authors and maintainers. Where a licence asks for
its notice to travel with the software, that notice is kept.

**Frosted-glass interface**
- [Haze](https://github.com/chrisbanes/haze) — © 2024 Chris Banes — Apache License 2.0. The blur
  behind every frosted panel, and the reason the whole thing looks like it does.

**On-device model**
- [llama.cpp](https://github.com/ggml-org/llama.cpp), including the **ggml** tensor library —
  © Georgi Gerganov and the llama.cpp/ggml contributors — MIT License. Runs the local GGUF assistant
  entirely on the phone.
- [SPIRV-Headers](https://github.com/KhronosGroup/SPIRV-Headers) — © The Khronos Group Inc. —
  MIT License. Needed to build llama.cpp's Vulkan (GPU) backend.

**Data, networking & app framework**
- [SQLCipher for Android](https://github.com/sqlcipher/sqlcipher-android) — © Zetetic LLC —
  BSD-style licence. Provides the encrypted database.
- [OkHttp](https://github.com/square/okhttp) — © Square, Inc. — Apache License 2.0. The HTTP client
  behind the cloud assistant.
- [Jetpack Compose, Room, DataStore and other AndroidX libraries](https://developer.android.com/jetpack)
  — © The Android Open Source Project — Apache License 2.0.
- [Kotlin](https://github.com/JetBrains/kotlin) and
  [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) — © JetBrains s.r.o. and
  contributors — Apache License 2.0.

**Native cryptography & animation (Rust)**
- The [RustCrypto](https://github.com/RustCrypto) crates `aes-gcm`, `pbkdf2`, `sha2` and `hmac`, and
  the [`jni`](https://github.com/jni-rs/jni-rs) crate — each dual-licensed **MIT OR Apache-2.0**.
  These power the backup/attachment encryption and the JNI bridge.

**Typefaces**
- Twelve families, every one under the [SIL Open Font License 1.1](https://openfontlicense.org),
  from the Google Fonts library and their respective designers: JetBrains Mono, Great Vibes, Cinzel,
  Noto Serif SC, ZCOOL XiaoWei, Zhi Mang Xing, Shippori Mincho, Yuji Mai, Reggae One, Song Myung,
  Nanum Brush Script, and East Sea Dokdo.

The build toolchain also leans on the Android NDK's bundled `glslc` (from Google's
[Shaderc](https://github.com/google/shaderc), Apache License 2.0) to compile the Vulkan shaders. None
of the above endorse Lucent; they simply made it possible, and we are duly grateful.

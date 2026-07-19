# Lucent

<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### Modern · Minimalist · Vivid

**A notes and tasks app with an assistant that can actually touch your data —
encrypted on device, translated into four languages, and buildable entirely from a phone.**

![Platform](badges/platform.svg)
![Build](badges/build.svg)
![Interface](badges/ui.svg)
<br/>
![Assistant](badges/ai.svg)
![Privacy](badges/privacy.svg)
![License](badges/license.svg)

</div>

---

## The idea

Most note apps ask you to choose: the pretty one, the private one, or the clever one. Lucent is an
attempt to refuse that trade. Everything you write is sealed in an encrypted database on your own
phone. The assistant can be a cloud model you pay for, a local model that never leaves the device,
or nothing at all. And the whole thing is built with no local toolchain — you press a button on
GitHub and an APK comes out.

## Notes that remember what they were

A note is a title, a body, tags, a colour, and — if you want — a checklist rather than prose.
Beyond that:

- **Version history.** Every meaningful edit is snapshotted, so you can read what a note said last
  Tuesday and restore it if today's rewrite was a mistake.
- **Links between notes.** Write `[[Shopping list]]` and it becomes tappable; the note you pointed
  at grows a *linked from* reference back. Point at a title that doesn't exist yet and the link
  shows red — tap it and that note is created.
- **Markdown, optional.** Headings, bold, code and lists render when you want them, and your text
  stays exactly as typed when you don't. Links work either way; the two switches are independent.
- **Templates.** Four one-tap starters — journal, meeting notes, project idea, checklist — offered
  only on a blank note, never over your work.
- **Archive and trash**, separately, because "done with this" and "delete this" are different.

## Tasks with due dates that mean something

Subtasks, priorities, repeat schedules, and reminders that survive a reboot. Due dates are parsed
from ordinary language, so *next Friday at 6* becomes an actual timestamp with an actual alarm
attached — not a note reading "next Friday". Completed tasks move to their own screen instead of
cluttering the list.

## An assistant with hands, not just opinions

Bring your own model. Lucent speaks the OpenAI, Anthropic, and Google request shapes, fetches the
live model list from whichever endpoint you point it at, and lets you keep **up to five API
profiles** to switch between with one tap.

What makes it useful is that it can *act*: **26 tools** wired to your real data — create and edit
notes and tasks, set priorities and due dates, tick off subtasks, pin, archive, search with real
filters, attach and remove files. Every action shows you exactly what it is about to do, in your
language, and waits for a yes. Replies stream in as they are generated, conversations are kept and
switchable, and you choose how much history is sent with each message — one message, this
conversation, or across conversations — because that choice is a cost decision as much as a memory
one.

## Or run the model on the phone itself

Import a `.gguf` file — or a `.zip` containing one, unpacked for you — and the assistant answers
with **llama.cpp running on device**. No account, no API key, no network. Tools, web search, and
cross-conversation memory switch off in this mode deliberately, keeping replies quick on hardware
that has to be conservative with memory; the model is unloaded when you leave the app. Roughly
1–4 GB Q4 models hit the best speed-to-quality balance on a typical phone.

## Four languages, switched instantly

English, Simplified Chinese, Japanese, and Korean, covering **662 interface strings** and every dialog, toast,
and accessibility label. Switching is immediate — no restart, no flash — and it deliberately never
touches your content: your notes, your tasks, and the assistant's replies stay in whatever language
you wrote them. Each script also ships three of its own typefaces alongside the Latin ones.

## Privacy that is structural, not promised

- The database is **SQLCipher-encrypted**; attachments are encrypted individually on disk.
- **App lock** with an optional security question. Neither password nor answer is stored — only a
  salted hash.
- **Backups** are a single encrypted `.lcb` file holding notes, tasks, history, chats, attachments
  and settings. It defaults to a portable built-in key so it restores anywhere, or takes a password
  of your own for real protection. Importing shows you what is inside *before* it changes anything.
- Lucent is invisible to other apps until you say otherwise: the system share sheet integration is
  off by default, and diagnostic logging is both off by default and local-only.
- Exports are the deliberate exception — **Markdown, Word, PDF, or Excel**, unencrypted, because a
  file you cannot open elsewhere is not an export.

## Made of glass

Soft colour blobs drift behind frosted panels that blur whatever passes beneath them. **15
palettes** in solid, gradient, and classic families, or an auto-cycle that wanders through all of
them; light, dark, system, and Monet-tinted themes; a typeface picker that previews each face in
itself. **Seven home-screen widgets** carry the same surface out to your launcher: new note, new
task, assistant, quick actions, task summary, today's tasks, and pinned note.

## Rust where it earns its place

Two hot paths are implemented in Rust and reached through JNI: the PBKDF2 + AES-256-GCM routines
behind backups and attachment encryption, and the maths that drives the background animation. Both
are byte-for-byte compatible with the Kotlin implementations they replace, and both fall back to
those implementations automatically when the native library is absent — so the app is faster where
Rust is available and identical where it is not.

## Building it

No Android Studio, no local SDK, no command line. Push to GitHub, open **Actions → Build release
APK → Run workflow**, and download the signed APK when it turns green. The signing key lives in
encrypted repository secrets and is decoded into a throwaway runner directory for one build; it is
never committed. The native pieces — llama.cpp at a pinned tag, the Rust cross-targets — are
fetched in labelled steps with retries, so a network hiccup reads as a clear failure rather than a
cryptic one.

Targets Android 8.0 (API 26) and above, shipping `arm64-v8a` and `armeabi-v7a`.

## Licence

Released under the [MIT Licence](LICENSE). Do what you like with it; keep the notice.

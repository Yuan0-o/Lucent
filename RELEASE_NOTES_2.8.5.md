# The Settings Finally Sit Still

There are some things in life you can always count on: the sun rising, taxes being due, and that little switch in Settings flashing the wrong value before settling into what you actually chose. Well, three out of three isn't bad, but we've just fixed the third one.

Every toggle, every dropdown, every carefully curated preference in every settings page now remembers what you set it to — and shows it immediately. No brief glimpse of the old state. No flash of a value you haven't touched in months. You open the page, you see your settings. It's the sort of fix that sounds simple until you realise how much of the app's configuration flows through a single cache layer that needed to be told, in no uncertain terms, "when someone changes this, update the cache immediately, not whenever you feel like it."

While we were poking around the preferences, the navigation also had a quiet word with itself. If you tap a tab at the bottom — Notes, Tasks, Assistant, Settings — it now jumps straight there without the sliding animation. The swipe gesture is still there for those who enjoy a dramatic transition, but a deliberate tap means business: you want that screen now, and you get it now.

But the swipe itself hasn't been left to fend for itself. When you do swipe between pages, the bottom tabs used to take a moment longer than the main content to update their highlight — a tiny lag that made the whole thing feel slightly out of sync, like a badly dubbed film. That gap has been closed. The tabs now track the pager's position as closely as a well-trained border collie, so the highlight follows your finger in real time, not a beat behind.

A side effect of the pager fix is that the adjacent page now stays rendered just off-screen, so when you swipe, it's already there, fully composed, waiting for its cue. No white flash, no half-loaded state, just a seamless slide from one screen to the next.

Elsewhere, the check workflow that guards every release got a name change and a personality alignment — `desktop-jvm-check` and `android-jvm-check` now pull in the same quality checks where each platform supports them, so both green checkmarks mean the same standard of readiness.

Your data remains encrypted, your backups still back up, and your assistant still assists. Nothing has changed about the things that protect you — only about the things you touch every day.

**Settings that stay put** — no more flash of yesterday's value before today's appears.
**Tap-jump navigation** — tabs go where you click, no animation required.
**Tabs that keep up** — bottom highlights follow the swipe in real time.
**Preloaded pages** — the next screen is ready before you arrive.
**Same encryption, same backups, same assistant — same peace of mind.**
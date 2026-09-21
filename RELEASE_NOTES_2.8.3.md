# Lucent 2.8.3 — The Swipe Becomes Natural

A modest collection of refinements that should have been there all along, really.

## What's New

**Navigation Gets a Swipe**  
You can now swipe horizontally between tabs (Tasks, Notes, Assistant, Settings) because apparently tapping the bottom navigation was too much effort. We aim to please.

**The Keyboard Knows Its Place**  
When editing notes or tasks, the text field now scrolls properly to keep your cursor visible above the keyboard. Revolutionary stuff—keeping text you're editing actually *visible*.

**Settings That Remember Their Own Values**  
Fixed a rather embarrassing flash where settings pages would briefly show the wrong selections before remembering what you'd actually chosen. The settings now load correctly on the first attempt. Progress!

**Pinned Items Already Let You Unpin**  
For those wondering how to unpin items: just tap the pin icon. It's been there all along. We've simply clarified this in the documentation for those who prefer reading to clicking.

**Windows Desktop Gets Its Enter Key**  
The desktop Assistant already handles Enter to send and Shift+Enter for new lines properly. This note is here so Windows users stop asking about it.

**Automated Releases**  
Added a GitHub Action workflow for creating releases with properly attached APK assets. Because manually uploading files is terribly last decade.

## Technical Notes

- Version format now strictly enforced as x.y.z (e.g., 2.8.3)
- Improved text field cursor tracking with selection change detection
- HorizontalPager implementation for tab navigation with smooth animations
- Settings UI now uses null-safe flow collection to prevent initial value flash
- Release workflow validates version format and includes R8 mapping files

## For Developers

This release improves the developer experience with consistent version numbering and automated release workflows. The swipe navigation uses `HorizontalPager` with proper state synchronization.

---

*Built with moderate enthusiasm and a proper cup of tea.*

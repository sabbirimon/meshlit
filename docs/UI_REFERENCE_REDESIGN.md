# Reference-driven UI continuation

Updated 2026-10-07, Asia/Dhaka. The owner's screenshots are the visual reference:
compact AI chat toolbar, centered empty state, pale neutral canvas with a blue/cyan
bottom glow, rounded bottom composer and an adjusted viewport while typing.
Use Meshlit's identity and licensed Figtree/system fonts. Do not copy Google assets.

## Implemented layout

- Settings owns one toolbar. The shared shell omits its toolbar for Settings;
  scaffold padding is consumed before rendering child scaffolds. Settings detail
  pages hide the tab bar; Back returns to settings. Root Settings retains drawer
  navigation, search, Basic/Advanced filters and the main tabs.
- Appearance removes the hero/banner. Short System/Light/Dark/Scheduled chips and
  short Amber label avoid needless wrapping; section dividers, aligned switch rows
  and a one-edit Reference blue preset retain all persisted controls.
- Fresh defaults use Sky accent, system light/dark and a curated palette instead
  of wallpaper color. Existing saved preferences are retained. Reference blue in
  Appearance explicitly selects light/Paper/Sky and removes custom palette stops,
  while preserving font size, accessibility and motion preferences.
- Chat has one compact model selector header, a centered two-line welcome, no
  stacked suggestions, one pill composer and real photo/file/options controls.
  The static blue glow is inexpensive; custom palettes tint their own glow and
  high contrast removes it. No blur or placeholder model output is used.
- The chat viewport consumes IME insets once; the composer sits above the keyboard.
  Welcome and mode caption disappear while typing. The activity uses adjustResize.
  Loaded model names ellipsize in a whole 48dp interactive selector, not a tiny
  text-only target. Send changes to Stop during generation.
- Replies retain selectable text/code and have a compact Copy/Share action row.
  Android's chooser is human initiated. Photos/camera open the existing bounded
  vision workflow; text-only chat does not pretend to understand images.
- Actual generated image/audio/video outputs can be saved or shared. FileProvider
  exposes only the generated-media directory, grants read access for a chosen file
  and validates the output's canonical parent. No automatic publication occurs.
- Shared rounded shapes and tighter body tracking apply to all themed screens.
  Heading weights are softened; font family and Android/app text scaling remain.
  Drawer history and tools scroll together, so long histories do not hide tools.

## Validation gates

Build both flavors; keep fatal lint enabled. Inspect actual light/dark chat,
keyboard-open composer, Settings and Appearance using emulator screenshots and
UI bounds. Check narrow width and enlarged text. Record screenshot paths and
build evidence in PROGRESS.md. A reference match is not acceptance of every
legacy tools page. Preserve unavailable backend labels and all saved controls.

Physical Samsung typing, inset behavior, performance and large-font accessibility
remain separate checks until a real authorized phone is reachable through ADB.
The OS keyboard, Android share chooser and device photo picker retain OS styling;
the app does not draw an imitation keyboard or fabricated content.

Models now places import/download actions, Hub expansion, search/filters and real
model rows before optional device/startup details. Download-method/token controls
expand explicitly. Final light-theme selected chip labels use dark text on a
pale accent container; bright cyan on pale cyan was caught in screenshot review.
The chat header reflects an online profile or router selection independently of
the currently loaded local model. Its offline caption does not imply cluster
execution is on-device.

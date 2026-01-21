# Changelog

## 1.0.3 - 2026-01-21
- Add `/trash add <itemId>` and `/trash remove <itemId>` to manage your auto-trash list via chat.
- Add `/trash enable [true/false]`, `/trash on`, and `/trash off` for quick enable/disable control.
- Add `/trash notify [true/false]` to toggle auto-trash notification messages.
- Expand documentation so all `/trash` commands are listed in the README.
- Code cleanup in command handling and documentation.

## 1.0.2 - 2026-01-19
- Add per-player auto-trash profiles with create, duplicate, rename, and delete actions.
- Update the config UI with profile management controls and an inventory scan cleanup action.
- Migrate legacy exact-item lists into the new profile data model.

## 1.0.1 - 2026-01-18
- Expand the AutoTrash configuration UI with item slot rows.
- Improve the config page layout and structure for easier use.
- Refresh public plugin documentation and screenshots.

## 1.0.0 - 2026-01-17
- Initial release of AutoTrash.
- Per-player trash lists with opt-in enable/disable.
- In-game configuration UI via `/trash`.
- Automatic removal of matching items on pickup.
- Optional notification when items are trashed.

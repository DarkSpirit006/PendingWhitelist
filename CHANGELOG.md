# Changelog

All notable changes to PendingWhitelist are documented here.

## [1.2.0] - 2026-07-16

### Added

- Added clickable admin review actions that use the official `/wl add` and `/wl rpl` commands.
- Added cleaner command help, list headers, and grouped command results.
- Added permission-aware and prefix-filtered tab completion.

### Changed

- Removed duplicate public review aliases. `/wl add` is now the whitelist action, and `/wl rpl` is the pending-only removal action.
- Updated README, usage docs, configuration docs, and GitHub templates for a cleaner public release.
- Modernized text formatting helpers around Adventure components while keeping the legacy `TextUtil.color(...)` method available.

### Fixed

- Fixed pending players being added to `whitelist.json` with a blank name when a UUID was used and Bukkit did not have a cached username.
- Fixed `/wl add` potentially removing a player from the whitelist immediately after adding them.
- Fixed stale command usage text and tab completion entries.

## [1.0.1] - 2026-07-15

### Added

- Initial pending whitelist tracking, storage, purge checks, and admin commands.

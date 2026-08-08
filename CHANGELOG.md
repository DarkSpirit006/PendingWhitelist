# Changelog

All notable changes to PendingWhitelist are documented here.

## [Unreleased]

### Added

- Added automatic update checks and downloads through `/wl version`.
- Added `/wl version` with the installed version, latest release, and clickable update button.
- Added Geyser compatibility by converting temporary UUID whitelist entries to username entries after join.
- Expanded `/wl add` to support arbitrary usernames and UUIDs as a `/whitelist add` replacement.
- Added server-panel logging for update checks and results.

## [1.2.7] - 2026-08-07

### Fixed

- Fixed whitelist updates being processed too early during player join.
- Improved player join handling to ensure whitelist updates occur after the join event completes.
- Improved UUID handling for pending whitelist entries.

## [1.2.6] - 2026-08-07

### Added

- Added automatic plugin update checking.
- Added update check logging.
- Added handling for invalid or unavailable version information.
- Added HTTP redirect support for update checks.

## [1.2.5] - 2026-08-07

### Improved

- Improved plugin update checking.
- Improved handling of update information and version tags.

## [1.2.4] - 2026-08-07

### Added

- Added `/wl version` to display the installed plugin version.
- Added latest-version checking.
- Added an option to update the plugin when a newer version is available.

## [1.2.3] - 2026-08-07

### Added

- Added plugin update checking.
- Added update-related logging.

### Improved

- Improved handling of invalid version information.

## [1.2.2] - 2026-08-07

### Changed

- No plugin functionality changes.

## [1.2.1] - 2026-08-06

### Changed

- No plugin functionality changes.

## [1.2.0] - 2026-07-16

### Added

- Added clickable admin review actions using `/wl add` and `/wl rpl`.
- Added cleaner command help and list output.
- Added grouped command results.
- Added permission-aware and prefix-filtered tab completion.

### Changed

- `/wl add` is now the whitelist action.
- `/wl rpl` is now the pending-only removal action.
- Removed duplicate public review aliases.
- Modernized text formatting using Adventure components while keeping the legacy `TextUtil.color(...)` method available.

### Fixed

- Fixed pending players being added to `whitelist.json` with a blank name when a UUID was used and Bukkit did not have a cached username.
- Fixed `/wl add` potentially removing a player from the whitelist immediately after adding them.
- Fixed stale command usage text.
- Fixed stale tab-completion entries.

## [1.0.1] - 2026-07-15

### Added

- Added initial pending whitelist tracking.
- Added persistent pending player storage.
- Added automatic purge checks for expired pending entries.
- Added admin commands for managing pending whitelist entries.
- Added player join handling for pending whitelist requests.
- Added command tab completion.
- Added configurable plugin messages and settings.

## [1.0.0] - 2026-07-15

### Added

- Initial release of PendingWhitelist.
- Added the core pending whitelist system.
- Added pending player management.
- Added whitelist request handling.
- Added persistent storage for pending players.
# Changelog

## [2.0.0] - 2026-09-01

### Major Update

- Redesigned the administration GUIs and interactions.
- Unified Add Players and Whitelisted Players layouts and navigation.
- Added row-safe Java and Bedrock grouping.
- Added Shift-click page-wide add and remove actions.
- Added asynchronous skin caching and live GUI skin updates.
- Improved Floodgate identity and username-prefix handling.
- Removed GUI refresh/reload controls; screens rebuild their data automatically.
- Simplified player names and item lore to avoid duplicate or misleading text.
- Moved the build to Gradle 9.7.1 with Kotlin DSL.
- Made GitHub Actions builds manual and removed automatic version increments/releases.

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
- Added automatic purge checks for expired pending players.
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

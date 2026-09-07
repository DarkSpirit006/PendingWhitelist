# Changelog

## [2.2.0] - 2026-09-05

### Added

- Added a new Paper plugin setup with a more reliable plugin lifecycle.
- Added automatic grouping of players in the Add Players screen into pending, online, and offline players.
- Added Bedrock-first ordering for pending and online players.
- Added cooldown-based suppression for repeated whitelist join notifications.
- Added safer recovery for pending player data.
- Added optional detailed debug logging through `config.yml`.
- Added optional SkinsRestorer support for displaying skins for offline-mode players.

### Improved

- Improved pending player lookup for servers with larger player lists.
- Improved whitelist management for Java and Bedrock players.
- Improved player head loading and caching in the administration GUI.
- Improved player-facing messages and GUI formatting.
- Improved Add Players tab completion and player sorting.
- Improved GUI input handling when switching between views.
- Improved tab completion responsiveness.
- Improved pending data handling to reduce unnecessary disk writes.

### Changed

- Changed the minimum supported server version to Paper 1.20.1.
- Changed repeated whitelist join notifications to respect the configured cooldown.
- Changed `/wl version` to show available updates with a clickable Modrinth download link.
- Changed `/wl reload` to safely reload the plugin configuration and refresh runtime tasks.

### Fixed

- Fixed `/wl add` failing when GUI layouts contained intentional empty slots.
- Fixed incorrect player identity and whitelist name handling in several cases.
- Fixed pending join processing when the login event is asynchronous.
- Fixed GUI transitions where stale inventory events could interfere with the newly opened screen.
- Fixed update notifications incorrectly appearing when the installed version was already newer.

## [2.1.0] - 2026-09-02

### Improved

- Improved compatibility with Paper 1.20.1.

## [2.0.0] - 2026-09-01

### Added

- Added a complete in-game management GUI for pending and whitelisted players.
- Added separate Java and Bedrock player sections.
- Added bulk add and remove actions for players on the current page.
- Added player skin loading for GUI player heads.
- Added improved Floodgate support and Bedrock username handling.
- Added improved UUID handling for Java and Bedrock players.
- Added `/wl version` for checking the installed plugin version and available updates.
- Added configurable cleanup of expired pending players.
- Added sound feedback for GUI actions and notifications.

### Improved

- Improved pending whitelist request tracking.
- Improved whitelist handling for Floodgate players.
- Improved player name and UUID handling.
- Improved GUI navigation and player grouping.
- Improved command handling and tab completion.
- Improved duplicate and incomplete whitelist entry handling.
- Improved automatic pending player management.

### Changed

- Reworked the whitelist administration interface.
- Removed the need to manually manage pending player data through server files.

## [1.2.8] - 2026-08-07

### Fixed

- Fixed whitelist updates being processed too early during player join.
- Improved join handling so whitelist updates occur after the join process completes.
- Improved UUID handling for pending whitelist entries.

## [1.2.6] - 2026-08-07

### Added

- Added automatic update checking.
- Added `/wl version` for displaying the installed plugin version and checking for updates.
- Added handling for invalid or unavailable version information.
- Added support for following HTTP redirects during update checks.

### Improved

- Improved update checking and version handling.
- Improved handling of invalid version information.

## [1.2.0] - 2026-07-16

### Added

- Added clickable admin review actions using `/wl add` and `/wl rpl`.
- Added cleaner command help and list output.
- Added grouped command results.
- Added permission-aware and prefix-filtered tab completion.
- Added pending whitelist tracking and persistent pending player storage.
- Added automatic cleanup of expired pending players.
- Added admin commands for managing pending whitelist entries.
- Added player join handling for pending whitelist requests.
- Added configurable plugin messages and settings.

### Changed

- `/wl add` became the whitelist action.
- `/wl rpl` became the pending-only removal action.
- Removed duplicate public review aliases.
- Updated message formatting for a more consistent in-game appearance.

### Fixed

- Fixed pending players being added to `whitelist.json` with a blank name when only a UUID was available.
- Fixed `/wl add` potentially removing a player from the whitelist immediately after adding them.
- Fixed outdated command usage text.
- Fixed outdated tab-completion entries.

## [1.0.0] - 2026-07-15

### Added

- Initial release of PendingWhitelist.
- Added the core pending whitelist system.
- Added pending player management.
- Added whitelist request handling.
- Added persistent pending player storage.
- Added automatic cleanup of expired pending players.
- Added admin commands for managing pending whitelist entries.
- Added player join handling for pending whitelist requests.
- Added command tab completion.
- Added configurable plugin messages and settings.
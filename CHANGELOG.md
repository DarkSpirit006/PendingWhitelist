# Changelog

## [2.2.0] - 2026-09-05

### Added

- Added native Paper plugin support using `paper-plugin.yml`.
- Added automatic grouping of Add Players entries into pending, online, and offline players.
- Added Bedrock-first ordering within pending and online player groups.
- Added cooldown-based suppression for repeated join-attempt notifications.
- Added safer pending data persistence with backup recovery.
- Added optional detailed debug logging controlled from `config.yml`.
- Added optional SkinsRestorer integration for offline-mode skin resolution.

### Improved

- Improved pending player lookup and handling for large player lists.
- Improved persistence to coalesce rapid pending-entry updates instead of writing once per change.
- Improved whitelist name persistence to avoid redundant disk writes during GUI access.
- Improved whitelist management for Java and Bedrock players.
- Improved player head loading and caching in the administration GUI.
- Improved asynchronous update checking so network requests do not block the server thread.
- Improved update checking to avoid duplicate in-flight Modrinth requests.
- Improved player-facing messages and GUI formatting with a consistent visual style.
- Improved Add Players tab completion so known players are grouped and sorted consistently.
- Improved GUI input isolation so PendingWhitelist only handles inventory input while its GUI is active.
- Improved tab completion responsiveness by reusing the player list during rapid consecutive requests.

### Changed

- Changed the minimum supported server version to Paper 1.20.1.
- Changed repeated whitelist join notifications to respect the configured cooldown.
- Changed `/wl version` to report updates with a clickable Modrinth download link.
- Changed `/wl reload` to safely reload configuration and refresh runtime scheduling without replacing the running Paper plugin instance.

### Fixed

- Fixed `/wl add` failing when GUI layouts contained intentional empty slots.
- Fixed update checks incorrectly treating a newer installed version as an available update.
- Fixed several player identity and whitelist name resolution edge cases.
- Fixed pending join-attempt processing so Bukkit state is updated on the server thread when the login event is asynchronous.
- Fixed GUI transitions so stale inventory close events cannot disable input handling for the newly opened view.

## [2.1.0] - 2026-09-02

### Changed

- Added Paper 1.20.1 compatibility baseline.

## [2.0.0] - 2026-09-01

### Added

- Added a new in-game management GUI for pending and whitelisted players.
- Added separate Java and Bedrock player sections in the GUI.
- Added bulk add and remove actions for players on the current page.
- Added player skin loading and caching for GUI player heads.
- Added improved Floodgate support and Bedrock username handling.
- Added better UUID handling for Java and Bedrock players.
- Added `/wl version` for checking the installed version and available updates.
- Added configurable cleanup handling for expired pending players.
- Added sound feedback for GUI actions and notifications.

### Improved

- Improved pending whitelist request tracking and player information.
- Improved whitelist handling for Floodgate players.
- Improved player name handling and persistence.
- Improved GUI navigation and player grouping.
- Improved command handling and tab completion.
- Improved whitelist entry handling to avoid duplicate or incomplete entries.
- Improved automatic handling of pending player data.

### Changed

- Reworked the whitelist administration interface.
- Removed the need to manually manage pending player data through server files.

## [1.2.7] - 2026-08-07

### Fixed

- Fixed whitelist updates being processed too early during player join.
- Improved player join handling to ensure whitelist updates occur after the join event completes.
- Improved UUID handling for pending whitelist entries.

## [1.2.6] - 2026-08-07

### Added

- Added automatic plugin update checking.
- Added handling for invalid or unavailable version information.
- Added HTTP redirect support for update checks.

### Improved

- Improved update-check handling.

## [1.2.5] - 2026-08-07

### Improved

- Improved plugin update checking and version handling.

## [1.2.4] - 2026-08-07

### Added

- Added `/wl version` to display the installed plugin version.
- Added latest-version checking.
- Added an option to update the plugin when a newer version is available.

## [1.2.3] - 2026-08-07

### Added

- Added plugin update checking and update-related logging.

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

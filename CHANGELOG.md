# Changelog

## [2.2.2] - 2026-09-17

### Fixed

- Fixed GUI compatibility with newer Paper releases by avoiding binary-sensitive `InventoryView` method dispatch.
- Fixed stale GUI viewer tracking when another plugin cancels an inventory open.
- Fixed a whitelist-name repair race where a newer name could be left unscheduled after an older repair completed.
- Hardened whitelist JSON repair lifecycle during shutdown and concurrent name updates.
- Prevented failed pending-storage writes from being marked as persisted; saves now retry and remain retryable after failure.
- Prevented synchronous offline-mode name resolution from blocking the server thread during `/wl add`.
- Prevented synchronous Floodgate UUID lookups from blocking the server thread.
- Hardened pending player data parsing so malformed individual entries are skipped safely.
- Fixed unnecessary network-backed offline-player lookups when building GUI candidates.
- Avoided applying textureless cached profiles to GUI heads when no skin texture is available.

### Improved

- Added asynchronous player-profile resolution for unknown online-mode names.
- Improved optional Floodgate integration handling without making Floodgate a required dependency.
- Reduced repeated whitelist JSON parsing by caching stored whitelist names until `whitelist.json` changes.
- Reduced redundant whitelist JSON repair operations when stored names are already correct.
- Reduced repeated Add GUI layout and candidate lookups during rendering and bulk-add operations.
- Improved Modrinth response parsing so unexpected API entries are ignored safely.
- Simplified pending-entry removal and persistence error handling.
- Improved GUI toggle controls, status text, and action descriptions.
- Updated the build and release workflows to use JDK 25 while emitting Java 17-compatible bytecode.
- Updated the compatibility baseline to the Paper 1.20 API.

### Tests

- Added regression coverage for pending data persistence, recovery, malformed entries, legacy data, and version comparison.

## [2.2.0] - 2026-09-05

### Added

- Added a new Paper plugin setup with a more reliable plugin lifecycle.
- Added automatic grouping of players in the Add Players screen into pending, online, and offline players.
- Added Bedrock-first ordering for pending and online players.
- Added cooldown-based suppression for repeated whitelist join notifications.
- Added safer recovery for pending player data.
- Added optional detailed debug logging through `config.yml`.
- Added optional SkinsRestorer support for displaying skins for offline-mode players.

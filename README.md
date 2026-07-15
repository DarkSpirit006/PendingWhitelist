# PendingWhitelist

Lightweight Paper/Purpur plugin that records players who attempted to join while the server whitelist is enabled and offers simple management commands.

# PendingWhitelist

![Build](https://github.com/Dark_Spirit69/PendingWhitelist/actions/workflows/ci.yml/badge.svg)

Lightweight Paper/Purpur plugin for Minecraft 26.2 that records players rejected by the server whitelist and provides simple management commands for administrators.

Summary

- Tracks players denied by whitelist and stores their username, attempt count, and timestamps in `plugins/PendingWhitelist/pending.json`.
- Commands for listing and batch-whitelisting pending players.
- Configurable pagination and automatic purge of stale entries.

Requirements

- Java 25
- Paper/Purpur for Minecraft 26.2

Quick build

Windows (from project root):

```powershell
build-plugin.bat
```

Or use the Gradle wrapper (recommended once present):

```powershell
.\gradlew.bat clean build
```

Result

- The plugin jar is written to `build/libs/PendingWhitelist-1.0.0.jar` after a successful build.

Installation

1. Stop your server.
2. Copy the jar to the server `plugins/` folder.
3. Start the server and verify the plugin created `plugins/PendingWhitelist/`.

Configuration

- Edit `plugins/PendingWhitelist/config.yml` to change `page-size` or purge settings.

Commands

- `/wl list [page]` — list pending players (newest first)
- `/wl add <username> [username ...]` — whitelist and remove pending entries
- `/wl reload` — reload `config.yml` only

Contributing

- See `CONTRIBUTING.md` for guidance on reporting issues and submitting pull requests.

License

- MIT — see `LICENSE`.

- This project intentionally stores plain usernames in `pending.json` (no UUIDs or IPs).
- Do not commit local Gradle distributions or build artifacts — `.gitignore` already excludes them.

License: MIT

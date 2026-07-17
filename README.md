# PendingWhitelist

[![Build](https://github.com/DarkSpirit006/PendingWhitelist/actions/workflows/ci.yml/badge.svg)](https://github.com/DarkSpirit006/PendingWhitelist/actions/workflows/ci.yml)
![Version](https://img.shields.io/badge/version-1.2.0-blue)
![License](https://img.shields.io/badge/license-proprietary-red)

PendingWhitelist is a lightweight Paper/Purpur plugin that records players who try to join while the server whitelist rejects them. Admins can review pending players, whitelist them, remove pending entries, and receive clickable in-game review actions.

## Features

- Tracks pending whitelist attempts with UUID, username, attempt count, and timestamps.
- Shows pending players newest first with configurable pagination.
- Whitelists pending players while preserving the stored username in `whitelist.json`.
- Sends clickable admin notifications for whitelist and remove-pending actions.
- Supports batch commands for adding, removing, and clearing pending entries.
- Automatically purges stale pending entries when enabled.

## Requirements

- Java 25
- Paper or Purpur compatible with Minecraft/Paper API `26.2`

## Build

```powershell
.\gradlew.bat build
```

The plugin jar is written to:

```text
build/libs/PendingWhitelist-1.2.0.jar
```

## Installation

1. Stop your server.
2. Place `PendingWhitelist-1.2.0.jar` in the server `plugins` folder.
3. Start your server.
4. Edit `plugins/PendingWhitelist/config.yml` if you want to change pagination or purge settings.

## Commands

| Command | Description |
| --- | --- |
| `/wl pl [page]` | Show pending players, newest first. |
| `/wl list` | Show whitelisted players. |
| `/wl add <identifier> [identifier ...]` | Whitelist pending players and remove their pending entries. |
| `/wl remove <identifier> [identifier ...]` | Remove players from both the server whitelist and pending storage. |
| `/wl rpl <identifier> [identifier ...]` | Remove players only from the pending list. |
| `/wl reload` | Reload `config.yml`. |

`identifier` can be a stored username or UUID. When a pending username is available, PendingWhitelist prefers it for whitelist writes so `whitelist.json` keeps a readable player name.

## Configuration

The default config is created at `plugins/PendingWhitelist/config.yml`:

```yaml
page-size: 10

purge:
  enabled: true
  days: 30
```

## Storage

Pending entries are stored in `plugins/PendingWhitelist/pending.json`. The file is kept in memory while the plugin is running and written asynchronously when entries change.

The server-owned `whitelist.json` remains managed by Bukkit/Paper. PendingWhitelist only calls the server whitelist API.

## Documentation

- [Usage guide](docs/usage.md)
- [Configuration guide](docs/config.md)
- [Changelog](CHANGELOG.md)

## Contributing

Bug reports and feature requests are welcome. Pull requests are reviewed at the maintainer's discretion and are subject to the project license. See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

Proprietary. All rights reserved. This project may not be copied, modified, distributed, hosted, sold, or otherwise used without prior written permission from the copyright holder. See [LICENSE](LICENSE).

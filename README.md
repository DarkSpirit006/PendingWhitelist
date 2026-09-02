<div align="center">

# PendingWhitelist

### Review and manage whitelist requests directly in-game.

[![Build](https://img.shields.io/github/actions/workflow/status/DarkSpirit006/PendingWhitelist/build.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=Build)](https://github.com/DarkSpirit006/PendingWhitelist/actions/workflows/build.yml)
[![CodeFactor](https://img.shields.io/codefactor/grade/github/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=codefactor&logoColor=white&label=Code%20Quality)](https://www.codefactor.io/repository/github/darkspirit006/pendingwhitelist)
[![Release](https://img.shields.io/github/v/release/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=github&logoColor=white&label=Release)](https://github.com/DarkSpirit006/PendingWhitelist/releases/latest)
[![Modrinth](https://img.shields.io/modrinth/dt/pending-whitelist?style=for-the-badge&logo=modrinth&logoColor=white&label=Downloads)](https://modrinth.com/plugin/pending-whitelist)

[![Java 25](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/temurin/)
[![Gradle 9.7.1](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![Paper](https://img.shields.io/badge/Paper-26.2-1D1D1D?style=for-the-badge&logo=minecraft&logoColor=white)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-See%20LICENSE-6E6E6E?style=for-the-badge&logo=readthedocs&logoColor=white)](LICENSE)

</div>

PendingWhitelist records players rejected by a server whitelist and gives staff a fast, in-game way to review and manage them. Version 2.0.0 is a major GUI-focused update with unified layouts, Bedrock support, background skin handling, and live GUI updates.

## Features

- Records UUID, username, attempt count, and timestamps for rejected joins.
- `/wl add` provides a graphical replacement for `/whitelist add` when used without a player name.
- `/wl list [page]` shows whitelisted players in chat.
- `/wl remove` opens the Whitelisted Players GUI; `/wl remove <player...>` removes players directly.
- `/wl pl [page]` shows pending players in chat.
- `/wl rpl` opens the Add Players GUI; `/wl rpl <player...>` removes pending requests only.
- `/wl on` and `/wl off` control the server whitelist.
- Player entries are grouped by Pending/Other and Java/Bedrock without breaking row boundaries.
- Shift-click supports page-wide add and remove actions.
- Pagination controls appear only when another page exists.
- GUI changes update immediately; there is no manual GUI refresh button.
- Player skins are resolved in the background when possible, without blocking GUI clicks.
- Floodgate usernames use the configured Floodgate prefix when players are added.
- The join notification keeps clickable whitelist, reject, and GUI actions.
- Old pending requests can be purged automatically.
- `/wl version` checks the latest stable release on Modrinth.

## Requirements

- Java 25
- Paper or Purpur using the Paper API `26.2`
- Floodgate is optional and is used when installed.
- SkinsRestorer is optional and is used as a skin provider when installed.

## Build

The project uses **Gradle 9.7.1** and Kotlin DSL.

```powershell
.\gradlew.bat clean build
```

The plugin JAR is written to `build/libs/PendingWhitelist-2.0.0.jar`.

## Install

1. Stop the server.
2. Copy the plugin JAR into the server's `plugins` directory.
3. Start the server once.
4. Configure `plugins/PendingWhitelist/config.yml` if required.

## Commands

| Command | Behaviour |
| --- | --- |
| `/wl` | Opens the admin dashboard. |
| `/wl add` | Opens the Add Players GUI. |
| `/wl add <player>` | Adds the player to the server whitelist. |
| `/wl list [page]` | Shows only whitelisted players in chat. |
| `/wl remove` | Opens the Whitelisted Players GUI. |
| `/wl remove <player...>` | Removes players from the server whitelist. |
| `/wl pl [page]` | Shows only pending players in chat. |
| `/wl rpl` | Opens the Add Players GUI. |
| `/wl rpl <player...>` | Removes players from pending storage only. |
| `/wl reload` | Reloads configuration and pending data. |
| `/wl on` | Enables the server whitelist. |
| `/wl off` | Disables the server whitelist. |
| `/wl version` | Checks the latest stable Modrinth release. |

`/wl add` and `/wl remove` retain command-style player arguments, so they can be used as direct replacements for the corresponding whitelist commands. When no player is supplied, the Add Players or Whitelisted Players GUI is opened respectively.

## GUI

The dashboard contains the main administration actions:

- **Add Players** — pending players first, followed by other known non-whitelisted players.
- **Whitelisted Players** — all currently whitelisted players, with direct removal.
- **Configure** — edit the supported plugin settings.
- **Close** — closes the administration interface.

Player GUIs use the same 6-row structure. Player entries occupy the top four rows; navigation occupies the bottom row. The back button is always in the same position. Previous/next controls and the page indicator are hidden when pagination is unnecessary.

Player names are displayed once as the item title. UUIDs are included in player-head tooltips, while useful status and action information is shown in lore.

## Configuration

```yaml
page-size: 10

purge:
  enabled: true
  days: 30
```

- `page-size` controls the chat `/wl list [page]` and `/wl pl [page]` output.
- `purge.enabled` controls automatic cleanup of old pending requests.
- `purge.days` controls the age at which pending requests become eligible for cleanup.

The supported settings can also be changed from **/wl -> Configure** and are saved immediately.

## Data and identity handling

Pending requests are stored in `plugins/PendingWhitelist/pending.json` and written asynchronously. Paper remains responsible for the server's `whitelist.json`.

For Floodgate players, PendingWhitelist preserves the Floodgate UUID and applies the configured Floodgate username prefix when writing the whitelist entry. This avoids treating Bedrock identities as ordinary Java accounts.

Skin lookups run outside the server thread. Cached textures are reused, duplicate requests share a single in-flight request, and temporary failures are backed off to avoid repeated remote requests.

## Update checks

When an administrator joins, PendingWhitelist checks Modrinth for a newer stable version. `/wl version` performs the same check on demand. The plugin does not download or install updates automatically.

## Documentation

- [Usage guide](docs/usage.md)
- [Configuration guide](docs/config.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)

## License

PendingWhitelist is distributed under the license included in `LICENSE`.

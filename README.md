<div align="center">

# PendingWhitelist

### Review and manage whitelist requests directly in-game.

[![Build][build-badge]][build-link] [![Code Quality][codefactor-badge]][codefactor-link] [![Release][release-badge]][release-link] [![Downloads][downloads-badge]][downloads-link] [![Stars][stars-badge]][stars-link] [![Java][java-badge]][java-link] [![Gradle][gradle-badge]][gradle-link] [![Paper API][paper-api-badge]][paper-link] [![License][license-badge]][license-link]

</div>

[build-badge]: https://img.shields.io/github/actions/workflow/status/DarkSpirit006/PendingWhitelist/build.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=Build
[build-link]: https://github.com/DarkSpirit006/PendingWhitelist/actions/workflows/build.yml

[codefactor-badge]: https://img.shields.io/codefactor/grade/github/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=codefactor&logoColor=white&label=Code%20Quality
[codefactor-link]: https://www.codefactor.io/repository/github/darkspirit006/pendingwhitelist

[release-badge]: https://img.shields.io/github/v/release/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=github&logoColor=white&label=Release&labelColor=30363d&color=58a6ff
[release-link]: https://github.com/DarkSpirit006/PendingWhitelist/releases/latest

[downloads-badge]: https://img.shields.io/modrinth/dt/pending-whitelist?style=for-the-badge&logo=modrinth&logoColor=white&label=Downloads&labelColor=30363d&color=9b59ff
[downloads-link]: https://modrinth.com/plugin/pending-whitelist

[stars-badge]: https://img.shields.io/github/stars/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=github&logoColor=white&label=Stars&labelColor=30363d&color=f2cc60
[stars-link]: https://github.com/DarkSpirit006/PendingWhitelist/stargazers

[java-badge]: https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkSpirit006%2FPendingWhitelist%2Fmain%2Fbuild.gradle.kts&search=JavaLanguageVersion%5C.of%5C%28%5Cs*%28%5Cd%2B%29%5Cs*%5C%29&replace=%241&style=for-the-badge&logo=openjdk&logoColor=white&label=Java&labelColor=30363d&color=f89820
[java-link]: https://adoptium.net/temurin/

[gradle-badge]: https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkSpirit006%2FPendingWhitelist%2Fmain%2Fgradle%2Fwrapper%2Fgradle-wrapper.properties&search=gradle-%28%5Cd%2B%5C.%5Cd%2B%5C.%5Cd%2B%29-bin&replace=%241&style=for-the-badge&logo=gradle&logoColor=white&label=Gradle&labelColor=30363d&color=0f6b78
[gradle-link]: https://gradle.org/

[paper-api-badge]: https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkSpirit006%2FPendingWhitelist%2Fmain%2Fbuild.gradle.kts&search=io%5C.papermc%5C.paper%3Apaper-api%3A%28%5B%5E%22%5D%2B%29&replace=%241&style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNTYgMjU2Ij4KPGcgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoMTI4IDEyOCkiPgo8ZyBvcGFjaXR5PSIuOTgiPgo8cGF0aCBmaWxsPSIjRjREMDNGIiBkPSJNLTctNzkgMjMtNTQgNy00Mi0yMy02N3oiLz4KPHBhdGggZmlsbD0iIzdFRDMyMSIgZD0iTS0xMi03NiA3LTQ2LTgtMzctMzEtNjd6Ii8+CjxwYXRoIGZpbGw9IiMyRjhGRTUiIGQ9Ik0tMjctNjMgMy0zMC0xMC0xOS0zOS00OXoiLz4KPHBhdGggZmlsbD0iI0ZGNEY1RSIgZD0iTS03MS0zNy0zNi04LTQzIDktNzctMTh6Ii8+CjxwYXRoIGZpbGw9IiNGNEQwM0YiIGQ9Ik0tNzQgMzMtMzkgNS0zMSAxNy02NCA0OHoiLz4KPHBhdGggZmlsbD0iIzJGOEZFNSIgZD0iTS00MiA1OC0xMyAyMy0yIDM1LTMwIDcweiIvPgo8cGF0aCBmaWxsPSIjN0VEMzIxIiBkPSJNNiA3Mi0yIDMwIDEzIDI2IDIzIDY3eiIvPgo8cGF0aCBmaWxsPSIjRkY0RjVFIiBkPSJNNDEgNjAgMTAgMjEgMjIgMTEgNTQgNDd6Ii8+CjxwYXRoIGZpbGw9IiMyRjhGRTUiIGQ9Ik02NyA0MSAyMSA4IDI3LTYgNzggMjV6Ii8+CjxwYXRoIGZpbGw9IiNGNEQwM0YiIGQ9Ik03NyA0IDI1LTExIDI5LTI2IDgyLTEyeiIvPgo8cGF0aCBmaWxsPSIjN0VEMzIxIiBkPSJNNjktMzEgMjEtMjMgMTctMzggNjQtNDl6Ii8+CjxwYXRoIGZpbGw9IiNGRjRGNUUiIGQ9Ik01MS01NyAxMy0zMCAzLTQyIDQyLTcyeiIvPgo8L2c+CjxyZWN0IHg9Ii04MyIgeT0iLTgzIiB3aWR0aD0iMTY2IiBoZWlnaHQ9IjE2NiIgcng9IjIiIGZpbGw9IiM0MTQxNDEiLz4KPHBhdGggZD0iTS00OCAyIDQyLTQ5YzEwLTYgMTkgMiAxNiAxM0wyMyA2NmMtMiAxMC0xNCAxMy0yMCA0TC03IDQ1IDUgMTZsLTUzIDRjLTggMS05LTEzIDAtMThaIiBmaWxsPSIjZmZmIi8+CjxwYXRoIGQ9Im0tNyA0NSAxOC0xNC0zIDM2Yy0xIDEwLTE1IDExLTE3IDFaIiBmaWxsPSIjQ0ZDRkNGIi8+CjxwYXRoIGQ9Im0tNSA0NiAxMS04LTIgMjdjLTEgNS04IDYtMTAgMVoiIGZpbGw9IiM5QTlBOUEiLz4KPC9nPgo8L3N2Zz4=&label=Paper%20API&labelColor=30363d&color=33b5e5
[paper-link]: https://papermc.io/

[license-badge]: https://img.shields.io/github/license/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=opensourceinitiative&logoColor=white&label=License&labelColor=30363d&color=3fb950
[license-link]: LICENSE

PendingWhitelist records players rejected by a server whitelist and gives staff a fast, in-game way to review and manage them. Version 2.1.1 is a compatibility-focused update with unified layouts, Bedrock support, background skin handling, and live GUI updates.

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

- Java 21 or newer
- Paper or Purpur using the Paper API 1.20.1 or newer
- Paper 26.1+ requires Java 25 on the server
- Floodgate is optional and is used when installed.
- SkinsRestorer is optional and is used as a skin provider when installed.

## Build

Use the GitHub Actions workflows to build or publish the project. Releases are published from the current project version.


The project uses **Gradle 9.7.1** and Kotlin DSL.

```powershell
.\gradlew.bat clean build
```

The plugin JAR is written to `build/libs/PendingWhitelist-2.1.1.jar`.

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

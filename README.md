<div align="center">

# PendingWhitelist

Keep track of players who try to join while your server whitelist is enabled.

Review pending players and manage the whitelist directly in-game.

[![Build][build-badge]][build-link]
[![Code Quality][codefactor-badge]][codefactor-link]
[![Release][release-badge]][release-link]
[![Downloads][downloads-badge]][downloads-link]
[![Stars][stars-badge]][stars-link]
[![Java][java-badge]][java-link]
[![Gradle][gradle-badge]][gradle-link]
[![Paper API][paper-api-badge]][paper-link]
[![License][license-badge]][license-link]

[![bStats Statistics](https://bstats.org/signatures/bukkit/PendingWhitelist.svg)](https://bstats.org/plugin/bukkit/PendingWhitelist/33884)

</div>

<p align="center">
  <img src="https://raw.githubusercontent.com/DarkSpirit006/PendingWhitelist/main/assets/banner.png" alt="PendingWhitelist Banner" width="100%">
</p>

## What it does

When a player who is not whitelisted tries to join, PendingWhitelist saves the attempt.

Staff can then open `/wl` and see those players without having to search through the console. From there, players can be added to the whitelist, removed from it, or cleared from the pending list.


## How it works

1. A player tries to join while the whitelist is enabled.
2. The join is rejected normally.
3. PendingWhitelist records the attempt.
4. Staff can review the player with `/wl` or `/wl pl`.
5. The player can be added with the GUI or `/wl add <player>`.


## Features

- Records the player's UUID, name, attempt count, first attempt, and last attempt.
- In-game dashboard for pending players and whitelist management.
- Separate pending-player and whitelisted-player views.
- Organises pending and online players by Bedrock/Java status and name.
- Shift-click support for adding or removing every player on the current page.
- Clickable join notifications for staff with `pendingwhitelist.admin`.
- Configurable notification cooldown.
- Automatic cleanup of old pending requests.
- Background skin loading with local caching.
- Floodgate support for Bedrock players.
- Optional SkinsRestorer integration.
- `/wl version` for checking the latest stable Modrinth release.
- Local persistent storage; no separate database is required.

## GUI

### Add Players

The Add Players GUI shows players in this order:

1. Pending players
2. Online players
3. Previously joined offline players

Within the pending and online groups, Bedrock players are shown before Java players and names are sorted alphabetically.

**Left-click** adds the selected player to the whitelist.

**Shift-click** adds every player on the current page.

**Back** returns to the dashboard. **Previous/Next** appear when another page is available.

<p align="center">
  <img src="https://raw.githubusercontent.com/DarkSpirit006/PendingWhitelist/main/assets/add-players.gif" alt="Add Players GUI" width="100%">
</p>

### Whitelisted Players

The Whitelisted Players GUI shows the current server whitelist.

**Left-click** removes the selected player.

**Shift-click** removes every player on the current page.

<p align="center">
  <img src="https://raw.githubusercontent.com/DarkSpirit006/PendingWhitelist/main/assets/whitelisted-players.gif" alt="Whitelisted Players GUI" width="100%">
</p>

### Join notifications

Staff with `pendingwhitelist.admin` can receive a notification when a non-whitelisted player is rejected.

The notification includes clickable actions for:

- Whitelisting the player
- Rejecting the request
- Opening the GUI

Repeated notifications are limited by the configured cooldown.

<p align="center">
  <img src="https://raw.githubusercontent.com/DarkSpirit006/PendingWhitelist/main/assets/join-notification.gif" alt="Join notification" width="100%">
</p>

## Commands

| Command | Description |
| --- | --- |
| `/wl` | Open the admin dashboard. |
| `/wl add` | Open the Add Players GUI. |
| `/wl add <player>` | Add a player to the whitelist. |
| `/wl list [page]` | List currently whitelisted players in chat. |
| `/wl remove` | Open the Whitelisted Players GUI. |
| `/wl remove <player...>` | Remove players from the whitelist. |
| `/wl pl [page]` | List pending players in chat. |
| `/wl rpl` | Open the Add Players GUI. |
| `/wl rpl <player...>` | Remove players from pending storage. |
| `/wl on` | Enable the server whitelist. |
| `/wl off` | Disable the server whitelist. |
| `/wl reload` | Reload PendingWhitelist configuration. |
| `/wl version` | Check for a newer stable release on Modrinth. |

All administration commands require `pendingwhitelist.admin`.

## Configuration

The default configuration is:

```yaml
logging:
  debug: false

page-size: 10

notifications:
  join-attempts: true
  join-attempt-cooldown-seconds: 60

purge:
  enabled: true
  days: 30
```

See [`docs/config.md`](docs/config.md) for the full list of options and their behaviour.

After changing the configuration:

```text
/wl reload
```

## Requirements

- Paper or Purpur using the Paper API 1.20.1 or newer
- Java 21 or newer
- Floodgate is optional
- SkinsRestorer is optional

Use the Java version required by your server release if it is newer than the minimum above.

## Installation

1. Stop the server.
2. Put `PendingWhitelist-<version>.jar` in the server's `plugins` directory.
3. Start the server.
4. Adjust `plugins/PendingWhitelist/config.yml` if needed.

No separate bStats installation is required. bStats is bundled with the release JAR.

## Data and integrations

Pending requests are stored in:

```text
plugins/PendingWhitelist/pending.json
```

Paper continues to manage the normal `whitelist.json`.

With Floodgate installed, Bedrock players keep their Floodgate UUID and configured username prefix when added to the whitelist.

Skin data is cached locally so the plugin does not need to perform a fresh lookup every time the GUI is opened.

PendingWhitelist reports anonymous usage statistics through bStats. See the [bStats page](https://bstats.org/plugin/bukkit/PendingWhitelist/33884) for the current statistics. bStats can be disabled through its global configuration.

## Troubleshooting

Enable `logging.debug` in `plugins/PendingWhitelist/config.yml` when you need more detail in the server console.

The setting can also be changed from **/wl → Configure**.

For bStats troubleshooting, `plugins/bStats/config.yml` can be used to enable bStats request/response logging. Metrics may take some time to appear after the server starts.

## Building from source

The project uses Gradle with the Kotlin DSL.

### Windows

```powershell
.\gradlew.bat clean build
```

### Linux / macOS

```bash
./gradlew clean build
```

The production JAR is written to:

```text
build/libs/PendingWhitelist-<version>.jar
```

## Documentation

- [Usage guide](docs/usage.md)
- [Configuration guide](docs/config.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)
- [Third-party licenses](THIRD_PARTY_LICENSES.md)

## Contributing

Bug reports, fixes, and feature ideas are welcome.

For changes that affect commands, configuration, compatibility, or integrations, update the relevant documentation with the pull request.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the project guidelines.

## License

PendingWhitelist is licensed under the [MIT License](LICENSE).

---

<p align="center">
  <sub>PendingWhitelist is a small addition to the normal whitelist system that keeps track of players who tried to join.</sub>
</p>

[build-badge]: https://img.shields.io/github/actions/workflow/status/DarkSpirit006/PendingWhitelist/build.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=Build
[build-link]: https://github.com/DarkSpirit006/PendingWhitelist/actions/workflows/build.yml

[codefactor-badge]: https://img.shields.io/codefactor/grade/github/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=codefactor&logoColor=white&label=Code%20Quality
[codefactor-link]: https://www.codefactor.io/repository/github/darkspirit006/pendingwhitelist

[release-badge]: https://img.shields.io/github/v/release/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=github&logoColor=white&label=Release
[release-link]: https://github.com/DarkSpirit006/PendingWhitelist/releases/latest

[downloads-badge]: https://img.shields.io/modrinth/dt/pending-whitelist?style=for-the-badge&logo=modrinth&logoColor=white&label=Downloads
[downloads-link]: https://modrinth.com/plugin/pending-whitelist

[stars-badge]: https://img.shields.io/github/stars/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=github&logoColor=white&label=Stars
[stars-link]: https://github.com/DarkSpirit006/PendingWhitelist/stargazers

[java-badge]: https://img.shields.io/badge/Java-21%2B-f89820?style=for-the-badge&logo=openjdk&logoColor=white&label=Java
[java-link]: https://adoptium.net/temurin/

[gradle-badge]: https://img.shields.io/badge/Gradle-Kotlin%20DSL-0f6b78?style=for-the-badge&logo=gradle&logoColor=white&label=Gradle
[gradle-link]: https://gradle.org/

[paper-api-badge]: https://img.shields.io/badge/Paper%20API-1.20.1%2B-33b5e5?style=for-the-badge&logo=papermc&logoColor=white&label=Paper%20API
[paper-link]: https://papermc.io/

[license-badge]: https://img.shields.io/github/license/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=opensourceinitiative&logoColor=white&label=License
[license-link]: LICENSE

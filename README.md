<div align="center">

# PendingWhitelist

**Keep track of players who tried to join while the server whitelist was enabled. Review and manage them from one in-game interface.**

[![Build][build-badge]][build-link] [![Code Quality][codefactor-badge]][codefactor-link] [![Release][release-badge]][release-link] [![Downloads][downloads-badge]][downloads-link] [![Stars][stars-badge]][stars-link] [![Java][java-badge]][java-link] [![Gradle][gradle-badge]][gradle-link] [![Paper API][paper-api-badge]][paper-link] [![License][license-badge]][license-link]

[![bStats Statistics](https://bstats.org/signatures/bukkit/PendingWhitelist.svg)](https://bstats.org/plugin/bukkit/PendingWhitelist/33884)

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

[paper-api-badge]: https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FDarkSpirit006%2FPendingWhitelist%2Fmain%2Fbuild.gradle.kts&search=io%5C.papermc%5C.paper%3Apaper-api%3A%28%5B%5E%22%5D%2B%29&replace=%241&style=for-the-badge&logo=papermc&logoColor=white&label=Paper%20API&labelColor=30363d&color=33b5e5
[paper-link]: https://papermc.io/

[license-badge]: https://img.shields.io/github/license/DarkSpirit006/PendingWhitelist?style=for-the-badge&logo=opensourceinitiative&logoColor=white&label=License&labelColor=30363d&color=3fb950
[license-link]: LICENSE

## What it does

When a player is rejected by the server whitelist, PendingWhitelist stores the attempt so staff can review it later. The `/wl` command opens a small admin dashboard where players can be added to the whitelist, removed from it, or cleared from the pending list without editing `whitelist.json` by hand.

It is designed for Paper servers and also supports Bedrock players through Floodgate. SkinsRestorer can be used as the skin provider on offline-mode servers.

## How it works

1. A player tries to join while the whitelist is enabled.
2. PendingWhitelist records the rejected attempt.
3. Staff can review the request from the `/wl` dashboard or chat list.
4. The player can be added to the whitelist directly from the GUI or with `/wl add <player>`.

## Highlights

- Stores the player's UUID, name, attempt count, and timestamps for rejected joins.
- Provides an in-game dashboard and player-management GUIs.
- Keeps pending, online, offline, Java, and Bedrock entries organised in the GUI.
- Supports page-wide add and remove actions with shift-click.
- Sends clickable join-attempt notifications to staff with the plugin permission.
- Keeps skin lookups off the main server thread and caches the results.
- Can remove old pending requests automatically.
- Includes a `/wl version` check for the latest stable Modrinth release.

## Requirements

- Paper or Purpur using the Paper API 1.20.1 or newer.
- Java 21 or newer.
- Floodgate is optional.
- SkinsRestorer is optional and is used for skin lookups on offline-mode servers when available.

For server versions that require a newer Java runtime, use the Java version required by that server release.

## Install

1. Stop the server.
2. Put `PendingWhitelist-<version>.jar` in the server's `plugins` directory.
3. Start the server.
4. Adjust `plugins/PendingWhitelist/config.yml` if you want to change the defaults.

No separate bStats installation is required. bStats is bundled with the release JAR.

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

All `/wl` administration commands require the `pendingwhitelist.admin` permission.

## GUI

### Add Players

The Add Players GUI shows pending requests first, followed by online players and then previously joined offline players. Within the pending and online groups, Bedrock players are shown before Java players and names are sorted alphabetically.

Left-click adds the selected player. Shift-click adds every player on the current page. Navigation controls are shown only when there is another page.

### Whitelisted Players

The Whitelisted Players GUI lists the current whitelist and supports direct removal. Left-click removes the selected player; shift-click removes every player on the current page.

### Join notifications

When a non-whitelisted player is rejected, staff with `pendingwhitelist.admin` can receive a clickable notification with actions to whitelist the player, reject the request, or open the GUI. Repeated notifications are limited by the configured cooldown.

## Configuration

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

See [`docs/config.md`](docs/config.md) for the full list of settings and behaviour.

## Data and integrations

Pending requests are stored in `plugins/PendingWhitelist/pending.json`. The server's normal `whitelist.json` remains managed by Paper.

Floodgate users keep their Floodgate UUID and configured username prefix when they are written to the whitelist. Skin data is cached locally so repeated GUI opens do not need a fresh lookup every time.

PendingWhitelist also reports anonymous usage statistics through bStats. See the [bStats page](https://bstats.org/plugin/bukkit/PendingWhitelist/33884) for the current statistics. Server owners can opt out through the global bStats configuration.

## Troubleshooting

Enable `logging.debug` in `plugins/PendingWhitelist/config.yml` when you need more detail in the server console. The setting can also be toggled from **/wl -> Configure**.

For bStats troubleshooting, `plugins/bStats/config.yml` can be used to enable bStats request/response logging. bStats controls its own submission schedule, so metrics may not appear immediately after a server starts.

## Building from source

The project uses Gradle with the Kotlin DSL.

```powershell
.\gradlew.bat clean build
```

On Linux or macOS:

```bash
./gradlew clean build
```

The production JAR is written to `build/libs/PendingWhitelist-<version>.jar`.

## Documentation

- [Usage guide](docs/usage.md)
- [Configuration guide](docs/config.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)
- [Third-party licenses](THIRD_PARTY_LICENSES.md)

## Contributing

Bug reports, fixes, and feature ideas are welcome. For changes that affect commands, configuration, compatibility, or integrations, please update the relevant documentation with the pull request.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the project guidelines.

## License

PendingWhitelist is licensed under the [MIT License](LICENSE).

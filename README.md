<div align="center">

# PendingWhitelist

### Review whitelist requests with in-game clickable commands.

[![Build](https://img.shields.io/github/actions/workflow/status/DarkSpirit006/PendingWhitelist/ci.yml?style=for-the-badge&label=build)](https://github.com/DarkSpirit006/PendingWhitelist/actions/workflows/ci.yml)
[![CodeFactor](https://img.shields.io/codefactor/grade/github/DarkSpirit006/PendingWhitelist?style=for-the-badge&label=CodeFactor)](https://www.codefactor.io/repository/github/darkspirit006/pendingwhitelist)
[![Latest release](https://img.shields.io/github/v/release/DarkSpirit006/PendingWhitelist?style=for-the-badge&color=4169e1&label=release)](https://github.com/DarkSpirit006/PendingWhitelist/releases/latest)
[![Repository size](https://img.shields.io/github/repo-size/DarkSpirit006/PendingWhitelist?style=for-the-badge&color=8a2be2&label=repo%20size)](https://github.com/DarkSpirit006/PendingWhitelist)
[![Code size](https://img.shields.io/github/languages/code-size/DarkSpirit006/PendingWhitelist?style=for-the-badge&color=ff8c00&label=code%20size)](https://github.com/DarkSpirit006/PendingWhitelist)
[![Modrinth downloads](https://img.shields.io/modrinth/dt/pending-whitelist?style=for-the-badge&logo=modrinth&logoColor=white&color=5ca424&label=Modrinth%20downloads)](https://modrinth.com/plugin/pending-whitelist)
[![GitHub downloads](https://img.shields.io/github/downloads/DarkSpirit006/PendingWhitelist/total?style=for-the-badge&logo=github&logoColor=white&color=24292f&label=GitHub%20downloads)](https://github.com/DarkSpirit006/PendingWhitelist/releases)

</div>

PendingWhitelist keeps track of players turned away by a Paper or Purpur server whitelist. Staff can review those players in-game, whitelist them, or clear their requests without editing JSON files by hand. It also supports Geyser/Bedrock players, automatic release updates, and server-panel update logging.

## What it does

- Records a player's UUID, name, attempt count, and most recent attempt.
- Shows pending requests newest first with configurable pagination.
- Provides clickable staff actions for approving or dismissing requests.
- Supports batch operations for approving, removing, and clearing entries.
- Purges old requests automatically when enabled.
- Converts approved Geyser player UUID whitelist entries into username entries after they join.
- Checks GitHub releases and stages newer plugin versions automatically.

## Requirements

- Java 25
- Paper or Purpur with Paper API `26.2`

## Build

Run the Gradle wrapper from the project directory:

```powershell
.\gradlew.bat build
```

The finished plugin is available at `build/libs/PendingWhitelist-<version>.jar`.

Changes to plugin source, resources, or build files on `main` are released
automatically. Documentation-only and workflow-only commits do not create a
release. The release workflow increments the patch version from the latest `v*`
tag, creates a Git tag, and attaches the built plugin JAR to the GitHub release.
Manual releases can still be started with `workflow_dispatch`.

## Install

1. Stop the server.
2. Copy the plugin JAR into the server's `plugins` directory.
3. Start the server once to create the configuration file.
4. Adjust `plugins/PendingWhitelist/config.yml` if needed.

## Commands

| Command | Description |
| --- | --- |
| `/wl pl [page]` | List pending players, newest first. |
| `/wl list` | List players on the server whitelist. |
| `/wl add <identifier> [identifier ...]` | Whitelist players and clear pending requests; also works as a `/whitelist add` replacement for names and UUIDs. |
| `/wl remove <identifier> [identifier ...]` | Remove players from the server whitelist and pending storage. |
| `/wl rpl <identifier> [identifier ...]` | Clear pending requests without changing the server whitelist. |
| `/wl reload` | Reload `config.yml`. |
| `/wl version` | Show the installed version, latest available version, and a clickable update button when applicable. |

All commands require the `pendingwhitelist.admin` permission, which defaults to server operators. An identifier can be either the stored player name or UUID.

## Configuration

```yaml
page-size: 10

purge:
  enabled: true
  days: 30

update:
  enabled: true
  check-interval-hours: 24
```

See the [configuration guide](docs/config.md) for details.

## Data and storage

Pending requests are stored in `plugins/PendingWhitelist/pending.json`. The plugin keeps entries in memory and writes changes asynchronously. The server's `whitelist.json` remains managed by Paper; PendingWhitelist uses the server whitelist API and console whitelist commands rather than editing that file directly. For approved Geyser players, the UUID is whitelisted first so they can join, then the UUID entry is replaced with `/whitelist add <username>` after their first join.

## Automatic updates

PendingWhitelist checks the official GitHub releases page once a day by default. Startup checks, version checks, current-version results, download results, and errors are written to the server panel. When a newer JAR is available, it downloads the file to `plugins/update`. Paper installs staged plugin updates on the next server restart. Set `update.enabled` to `false` in `config.yml` to manage updates manually.

## Documentation

- [Usage guide](docs/usage.md)
- [Configuration guide](docs/config.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)

## Contributors

<a href="https://github.com/DarkSpirit006/PendingWhitelist/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=DarkSpirit006/PendingWhitelist" alt="Project contributors" />
</a>

## License

[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg?style=for-the-badge)](LICENSE)

PendingWhitelist is distributed under the [MIT License](LICENSE).

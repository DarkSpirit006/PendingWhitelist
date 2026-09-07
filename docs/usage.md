# Usage

PendingWhitelist is managed through `/wl`. The main command opens the admin dashboard; the remaining subcommands can be used directly from chat or the console where applicable.

## Commands

| Command | Description |
| --- | --- |
| `/wl` | Open the admin dashboard. |
| `/wl add` | Open the Add Players GUI. |
| `/wl add <player>` | Add a player to the server whitelist. |
| `/wl list [page]` | List currently whitelisted players in chat. |
| `/wl remove` | Open the Whitelisted Players GUI. |
| `/wl remove <player...>` | Remove players from the server whitelist. |
| `/wl pl [page]` | List pending whitelist requests in chat. |
| `/wl rpl` | Open the Add Players GUI. |
| `/wl rpl <player...>` | Remove players from pending storage only. |
| `/wl on` | Enable the server whitelist. |
| `/wl off` | Disable the server whitelist. |
| `/wl reload` | Reload the plugin configuration and runtime state. |
| `/wl version` | Check the latest stable Modrinth release. |

All `/wl` administration commands require `pendingwhitelist.admin`.

## Add Players GUI

The list is ordered as follows:

1. Pending players
2. Online players
3. Previously joined offline players

Pending and online players are grouped with Bedrock players before Java players, then sorted alphabetically by name.

- **Left-click**: add the selected player to the whitelist.
- **Shift-click**: add every player on the current page.
- **Back**: return to the dashboard.
- **Previous/Next**: change pages when more than one page is available.

The GUI is rebuilt after an add operation, so there is no separate refresh step.

## Whitelisted Players GUI

This screen lists players currently on the server whitelist.

- **Left-click**: remove the selected player from the whitelist.
- **Shift-click**: remove every player on the current page.
- **Back**: return to the dashboard.

## Join notifications

When a non-whitelisted player is rejected, staff with `pendingwhitelist.admin` can receive a notification with clickable actions. The notification can be configured and repeated attempts are limited by the configured cooldown.

## Whitelist controls

`/wl on` and `/wl off` are shortcuts for changing the server whitelist state. PendingWhitelist does not replace Paper's normal whitelist file; it records rejected join attempts separately.

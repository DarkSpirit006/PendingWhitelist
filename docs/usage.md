# Usage

## Commands

- `/wl` — opens the dashboard.
- `/wl add` — opens the Add Players GUI.
- `/wl add <player>` — adds a player to the server whitelist.
- `/wl list [page]` — shows only whitelisted players in chat.
- `/wl remove` — opens the same Whitelisted Players GUI.
- `/wl remove <player...>` — removes players from the server whitelist.
- `/wl pl [page]` — shows only pending players in chat.
- `/wl rpl` — opens the Add Players GUI.
- `/wl rpl <player...>` — removes players from pending storage only.
- `/wl reload` — reloads PendingWhitelist configuration without replacing the running Paper plugin instance.
- `/wl version` — checks the latest stable Modrinth release.

## Add Players GUI

The Add Players screen is ordered as pending players first, online players second, and previously joined offline players last. Pending and online players are sorted with Bedrock players first, then Java players; each group is alphabetical by player name.

- **Left-click** — add the selected player to the whitelist.
- **Shift-click** — add every player on the current page.
- **Back** — return to the dashboard.
- **Previous/Next** — move between pages when more than one page exists.

The GUI is rebuilt automatically after an add operation, so a separate refresh action is not required.

## Whitelisted Players GUI

The Whitelisted Players screen uses the same grid and navigation layout as Add Players.

- **Left-click** — remove the selected player from the whitelist.
- **Shift-click** — remove every whitelisted player on the current page.
- **Back** — return to the dashboard.

The displayed player name comes from the stored whitelist identity when Bukkit does not have a cached name.

## Join notifications

When a non-whitelisted player is rejected, administrators with `pendingwhitelist.admin` receive clickable actions to whitelist the player, reject the pending request, or open the GUI.


### Whitelist control

- `/wl on` enables the server whitelist.
- `/wl off` disables the server whitelist.
- `/wl list [page]` shows only currently whitelisted players in chat.
- `/wl pl [page]` shows only pending players in chat.

## Build artifacts

A normal Gradle build produces one JAR:

- `build/libs/PendingWhitelist-<version>.jar` — production release build.

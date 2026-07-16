# Usage

This document provides expanded examples for managing PendingWhitelist.

## Pending Players

- `/wl pl` - show the first page of pending players, newest first.
- `/wl pl 2` - show page 2 of pending players.

Hover over pending player names in-game to see stored details such as UUID and attempt count.

## Whitelisting Players

- Single player: `/wl add Steve123`
- Multiple players: `/wl add Steve123 AlexGaming Notch`

When the player is pending, `/wl add` whitelists the player and removes the pending entry. If the player is already whitelisted, the pending entry is still cleared.

PendingWhitelist prefers the stored username when adding a pending player to the server whitelist. This keeps Bukkit/Paper's `whitelist.json` readable instead of writing blank names for UUID-only entries.

## Removing Players

- `/wl rpl Steve123` - remove only the pending entry.
- `/wl remove Steve123` - remove from both the pending list and the server whitelist.

Use `rpl` when you want to reject or ignore a pending request without touching the server whitelist.

## Whitelist View

- `/wl list` - show currently whitelisted players.

## Reloading Config

- `/wl reload` - reloads `config.yml`. This does not delete or rewrite `pending.json` unless storage changes are already pending.

## Notes

- Admin join notifications include clickable whitelist and remove-pending actions.
- Entries are kept in memory and written asynchronously to disk when changed.
- `/wl add` and `/wl rpl` are the official review commands. Older review aliases are intentionally not exposed.

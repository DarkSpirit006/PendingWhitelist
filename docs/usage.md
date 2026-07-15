# Usage

This document provides expanded examples of how to use PendingWhitelist.

Listing pending players

- `/wl list` — show the first page of pending players (newest first).
- `/wl list 2` — show page 2.

Whitelisting players

- Single user:
  - `/wl add Steve123`
- Multiple users:
  - `/wl add Steve123 AlexGaming Notch`

The command output summarizes which players were added, already whitelisted, or unknown.

Reloading config

- `/wl reload` — reloads `config.yml` only. Does not touch `pending.json`.

Notes

- The plugin stores usernames and simple metadata in `plugins/PendingWhitelist/pending.json`.
- Entries are kept in memory and written asynchronously to disk when changed.

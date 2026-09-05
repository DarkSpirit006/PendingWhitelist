# Configuration

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

- `logging.debug` enables detailed diagnostic logging for troubleshooting. Keep it disabled during normal production operation unless extra diagnostics are needed.
- Player skin textures are cached for one hour and persisted in `skin-cache.json`. Generic offline profiles are cached for 10 minutes. Expired entries are refreshed only when a skin is requested.
- Offline-mode servers never query Mojang for skins. An available server-stored profile is preferred, followed by SkinsRestorer when installed; otherwise the local generic profile is used without a remote lookup. Pending requests get that generic profile immediately before the player joins. Pending-player skin cache entries are removed when the request is removed or purged.
- `page-size` controls the legacy text `/wl pl <page>` output.
- `notifications.join-attempts` controls whether staff receive join-attempt notifications.
- `notifications.join-attempt-cooldown-seconds` limits repeated notifications for the same player. The default is 60 seconds. Set it to `0` to notify on every attempt.
- `purge.enabled` controls whether old pending requests are removed automatically.
- `purge.days` controls how old a pending request may become before it is eligible for purge.

Configuration settings can be changed from **/wl -> Configure**. The debug logging toggle applies immediately. Changes made directly in `config.yml` take effect after `/wl reload`. The reload preserves the running Paper plugin instance and refreshes configuration safely.

PendingWhitelist automatically adds missing configuration keys when an existing `config.yml` is loaded. Existing values are preserved; updating the plugin will not overwrite your custom settings.

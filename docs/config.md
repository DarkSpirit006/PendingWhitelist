# Configuration

The default `config.yml` (created under `plugins/PendingWhitelist/config.yml`) contains two main settings:

```yaml
page-size: 10

purge:
  enabled: true
  days: 30
```

- `page-size` controls how many players are shown per page for `/wl list`.
- `purge.enabled` controls whether automatic purging runs.
- `purge.days` removes entries whose `lastAttempt` is older than the configured number of days.

Tips

- If your server accumulates many pending entries and you want to be conservative, increase `purge.days`.
- Purging runs once at startup and then hourly; it is performed asynchronously.

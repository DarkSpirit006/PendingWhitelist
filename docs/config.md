# Configuration

The default `config.yml` (created under `plugins/PendingWhitelist/config.yml`) contains two main settings:

```yaml
page-size: 10

purge:
  enabled: true
  days: 30
```

- `page-size` controls how many players are shown per page for `/wl pl`.
- `purge.enabled` controls whether automatic purging runs.
- `purge.days` removes entries whose `lastAttempt` is older than the configured number of days.
- `update.enabled` enables the official GitHub release updater.
- `update.check-interval-hours` controls how often the updater checks for a release.

When an update is found, the JAR is downloaded to the server's `plugins/update`
directory. Paper installs it on the next restart. The plugin is never replaced
while the server is running.

## Tips

- If your server accumulates many pending entries and you want to be conservative, increase `purge.days`.
- Purging checks hourly while the plugin is enabled.
- Set `update.enabled` to `false` if updates should be installed manually.

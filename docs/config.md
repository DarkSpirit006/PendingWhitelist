# Configuration

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

## Logging

`logging.debug` enables detailed troubleshooting messages in the server console. Leave it disabled during normal operation unless you are investigating a problem.

The setting can be changed from **/wl -> Configure** or directly in `config.yml`. Missing configuration keys are added automatically and existing values are preserved.

## Lists and notifications

`page-size` controls the number of entries shown by the paginated chat lists.

`notifications.join-attempts` controls whether staff receive notifications when a non-whitelisted player is rejected.

`notifications.join-attempt-cooldown-seconds` controls how often the same player can trigger a notification. Set it to `0` to allow a notification for every attempt.

## Pending request cleanup

`purge.enabled` enables automatic removal of old pending requests.

`purge.days` sets the age at which a pending request becomes eligible for cleanup.

## Skin caching

Player skin textures are cached for one hour and persisted in `skin-cache.json`. Generic offline profiles are cached for 10 minutes. Expired entries are refreshed when they are needed.

On offline-mode servers, PendingWhitelist prefers a server-stored profile, then SkinsRestorer when it is installed. When neither is available, it uses a local generic profile instead of querying Mojang.

Pending-player cache entries are removed when the corresponding request is removed or purged.

## Reloading

Changes made directly to `config.yml` take effect after `/wl reload`. Reloading keeps the current Paper plugin instance running and refreshes its configuration and scheduled tasks.

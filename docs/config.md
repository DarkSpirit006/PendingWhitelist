# Configuration

```yaml
page-size: 10

purge:
  enabled: true
  days: 30
```

- `page-size` controls the legacy text `/wl pl <page>` output.
- `purge.enabled` controls whether old pending requests are removed automatically.
- `purge.days` controls how old a pending request may become before it is eligible for purge.

The settings can be changed from **/wl -> Configure**. Changes are saved immediately.

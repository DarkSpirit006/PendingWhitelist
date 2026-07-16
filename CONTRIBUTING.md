# Contributing

Thanks for helping improve PendingWhitelist.

## Before Opening An Issue

- Search existing issues first.
- For bugs, include the plugin version, server version, Java version, and reproduction steps.
- Include relevant logs or sanitized `pending.json` / `whitelist.json` snippets when storage behavior is involved.

## Development

1. Fork the repository.
2. Create a focused feature or fix branch.
3. Build locally before opening a pull request.

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

## Pull Request Checklist

- [ ] The project builds successfully.
- [ ] Documentation was updated for behavior changes.
- [ ] Command usage, tab completion, and `plugin.yml` stay in sync.
- [ ] The change is focused and avoids unrelated formatting churn.

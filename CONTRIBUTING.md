# Contributing

Thanks for taking the time to improve PendingWhitelist.

## Before opening an issue

Search existing issues first. For a bug report, include:

- PendingWhitelist version
- Minecraft/server version and server software
- Java version
- Steps to reproduce the problem
- Relevant console output or a sanitized configuration snippet

Do not post passwords, tokens, IP addresses, or private server data.

## Development

Fork the repository and keep changes focused. Build the project before opening a pull request.

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

When a change affects a command, configuration option, compatibility, or integration, update the relevant documentation and changelog entry.

## Pull requests

Please keep pull requests small enough to review easily and avoid unrelated formatting changes. Include a short explanation of what changed and why.

Before opening the pull request, check that:

- [ ] The project builds successfully.
- [ ] Commands and tab completion still match the documented behaviour.
- [ ] `paper-plugin.yml` and the documentation are up to date.
- [ ] User-facing behaviour has been tested where practical.
- [ ] Documentation has been updated for behaviour changes.

## License

By contributing, you agree that your contribution is provided under the [MIT License](LICENSE).

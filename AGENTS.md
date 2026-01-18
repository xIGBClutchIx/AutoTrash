# Agent Guide for AutoTrash

This file describes build, test, and style expectations for agents.
It applies to the whole repository.

## Repo Overview
- Java plugin project for Hytale Server.
- Local dependency lives in `libraries/HytaleServer.jar`.
- Entry point class: `me.clutchy.hytale.autotrash.AutoTrashPlugin`.
- Plugin manifest: `src/main/resources/manifest.json`.
- Source root: `src/main/java`.
- Tests (if added) go in `src/test/java`.
- Compiled outputs appear under `bin/` and `.gradle/` (do not edit).

## Plugin Architecture
- **AutoTrashPlugin.java** - Plugin entry point, registers components, events, and commands.
- **AutoTrashPlayerSettings.java** - Per-player component storing settings (enabled, notify, exactItems[]).
- **AutoTrashSystem.java** - Handles inventory change events and removes configured trash items.
- **TrashCommand.java** - `/trash` command that opens the configuration GUI.

## UI Resources
- UI page: `src/main/resources/Common/UI/Custom/Pages/AutoTrashConfigPage.ui`
- Uses shared components from `../Common.ui`

## Build / Run / Test
Use `./gradlew` (wrapper checked in).

Build:
```bash
./gradlew build
```

Clean:
```bash
./gradlew clean
```

Compile only:
```bash
./gradlew classes
```

Test:
```bash
./gradlew test
```

Single test class:
```bash
./gradlew test --tests "com.example.MyTest"
```

Single test method:
```bash
./gradlew test --tests "com.example.MyTest.myMethod"
```

Run (if application plugin is added later):
```bash
./gradlew run
```

Lint/format:
- Use `./gradlew spotlessApply` to format.
- Use `./gradlew spotlessCheck` to verify formatting.

## Dependencies
- Local jar dependency is declared via `implementation files('libraries/HytaleServer.jar')`.
- Keep the jar path relative to repo root.
- Avoid adding Maven coordinates for the server SDK unless requested.

## Libraries and Sources
- Runtime SDK jars live under `libraries/` (notably `libraries/HytaleServer.jar`).
- Source jar is `libraries/HytaleServer-Source.jar` and can be inspected with `jar tf` or `jar xf` when you need API details.
- If you extract sources from the jar, do it from the repo root and remove the extracted folders afterward (e.g. `rm -rf com`).

## Manifest Conventions
- `manifest.json` lives in `src/main/resources`.
- `Main` must match the fully qualified plugin class.
- Keep `Version` as semver format.
- Maintain `Dependencies` keys using `Vendor:Module` format.

## Code Style (Java)
Use the existing style in `src/main/java`.

### Formatting
- 4 spaces per indent, no tabs.
- Opening brace on same line as declaration.
- One statement per line.
- Wrap long calls with hanging indent (8 spaces).
- Keep blank lines between methods and fields.
- Limit line length to ~120 where possible.

### Imports
- No wildcard imports.
- Order groups: `java.*`, `javax.*`, third-party (`com.hypixel.*`), then project.
- Separate import groups with a single blank line.

### Naming
- Packages: lowercase with dots (`me.clutchy.hytale.autotrash`).
- Classes: PascalCase (`AutoTrashPlugin`).
- Methods/fields: lowerCamelCase.
- Constants: UPPER_SNAKE_CASE.
- Avoid one-letter names except for indices.

### Types and Nullability
- Prefer explicit types; avoid raw types.
- Use `@Nonnull` for required params and override signatures.
- Do not return `null` when an empty object is available.
- Keep fields `final` when not reassigned.

### Logging
- Use `HytaleLogger.forEnclosingClass()` for class loggers.
- Log important lifecycle events in `setup()`.
- Avoid noisy debug logs unless explicitly requested.

### Plugin Lifecycle
- Extend `JavaPlugin`.
- Use constructor for minimal initialization only.
- Register commands/listeners in `setup()`.
- Keep `setup()` quick; avoid heavy blocking work.

### Commands
- Extend `CommandBase`.
- Provide short command name and description.
- Set permission group explicitly.
- Use `Message.raw(...)` for simple chat messages.
- Keep command execution in `executeSync`.

### Error Handling
- Fail fast with clear exceptions for bad state.
- Do not swallow exceptions; log and rethrow when needed.
- Prefer checked exceptions only when callers can recover.
- Validate external inputs early (commands, configs).

### Comments and Docs
- Use Javadoc for public classes/methods only.
- Keep comments concise and factual.
- Do not add TODOs unless asked.

## Commit Messages
Conventional commits: use `type(scope): subject` format for the first line.
- Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `build`.
- Scope is optional but preferred when change is localized.
- Keep the subject in imperative mood, lowercase, and under 72 chars.
- Use body bullets for rationale or breaking changes when needed.

## File Hygiene
- Do not edit compiled output in `bin/`.
- Do not commit `.gradle/` content.
- Keep resources under `src/main/resources`.

## Quick Reference
- Main class: `me.clutchy.hytale.autotrash.AutoTrashPlugin`
- Manifest: `src/main/resources/manifest.json`
- Local SDK jar: `libraries/HytaleServer.jar`
- Build task: `./gradlew build`
- Test task: `./gradlew test`
- Single test: `./gradlew test --tests "com.example.MyTest"`

End of agent guidance.

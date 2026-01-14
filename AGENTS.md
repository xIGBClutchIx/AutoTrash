# Agent Guide for HytaleCore

This file describes build, test, and style expectations for agents.
It applies to the whole repository.

## Repo Overview
- Java plugin project for Hytale Server.
- Local dependency lives in `libraries/HytaleServer.jar`.
- Entry point class: `me.clutchy.hytale.main.HytaleCorePlugin`.
- Plugin manifest: `src/main/resources/manifest.json`.
- Source root: `src/main/java`.
- Tests (if added) go in `src/test/java`.
- Compiled outputs appear under `bin/` and `.gradle/` (do not edit).

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
- No linting or formatting tasks are configured yet.
- If you add one, document the command here.

## Dependencies
- Local jar dependency is declared via `implementation files('libraries/HytaleServer.jar')`.
- Keep the jar path relative to repo root.
- Avoid adding Maven coordinates for the server SDK unless requested.

## Manifest Conventions
- `manifest.json` lives in `src/main/resources`.
- `Main` must match the fully qualified plugin class.
- Keep `Version` as `$version` for build-time substitution.
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
- Packages: lowercase with dots (`me.clutchy.hytale.main`).
- Classes: PascalCase (`HytaleCorePlugin`).
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

## Cursor/Copilot Rules
- No `.cursorrules`, `.cursor/rules/`, or Copilot instructions found.
- If you add any, update this section.

## Suggested Next Improvements (Optional)
- Add Gradle wrapper for reproducible builds.
- Add a testing framework (JUnit) if tests are added.
- Add `application` plugin only if a runnable main is needed.

## Quick Reference
- Main class: `me.clutchy.hytale.main.HytaleCorePlugin`
- Manifest: `src/main/resources/manifest.json`
- Local SDK jar: `libraries/HytaleServer.jar`
- Build task: `./gradlew build`
- Test task: `./gradlew test`
- Single test: `./gradlew test --tests "com.example.MyTest"`

End of agent guidance.

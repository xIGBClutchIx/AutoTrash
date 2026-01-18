# AutoTrash

A Hytale server plugin that automatically trashes configured items when picked up.

## Features

- **Per-player settings** - Each player can configure their own trash list
- **GUI configuration** - Open with `/trash` command
- **Auto-trash on pickup** - Items matching the trash list are automatically removed
- **Optional notifications** - Red text notification when items are trashed

## Usage

1. Run `/trash` to open the configuration GUI
2. Hold an item you want to auto-trash
3. Click "ADD HELD" to add it to your trash list
4. Toggle "Enable auto-trash" to activate
5. Optionally enable "Red notification text" to see when items are trashed

Click any item in your trash list to remove it.

## Installation

1. Build with `./gradlew build`
2. Copy the generated JAR from `build/libs/` to your server's plugins folder
3. Restart the server

## Commands

| Command | Description |
|---------|-------------|
| `/trash` | Opens the AutoTrash configuration GUI |

## Building

```bash
./gradlew build
```

Output JAR will be in `build/libs/`.

## Formatting

```bash
./gradlew spotlessApply
```

## Requirements

- Hytale Server with plugin support
- Java 25 toolchain (or compatible JDK for Gradle toolchains)
- Server SDK jar resolved from `libraries/HytaleServer.jar` or a local Hytale install at `~/.config/Hytale`, `~/Library/Application Support/Hytale`, or `%AppData%/Roaming/Hytale`

## Author

Cameron Weaver (Clutch) - [clutchy.me](https://clutchy.me)

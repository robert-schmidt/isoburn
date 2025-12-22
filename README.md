# isoBURN

A native macOS application for burning ISO files to USB drives. Built with Java, JavaFX, and Spring Boot.

<p align="center">
  <img src="docs/screenshot.png" alt="isoBURN Screenshot" width="600">
</p>

## Features

- **Simple GUI** - Select ISO, choose drive, click burn
- **Drive Detection** - Automatically detects removable USB drives and SD cards
- **UEFI Bootable** - Creates bootable USB drives for UEFI systems
- **Windows 11 Support** - Automatically splits large install.wim files (>4GB) for FAT32 compatibility
- **Progress Tracking** - Real-time byte-level progress with detailed logging
- **Safety First** - Confirmation dialogs and drive filtering to prevent accidents
- **No External Dependencies** - Pure Java file copying (no rsync version issues)

## Download

### Pre-built Application (Recommended)

Download the latest release from the [Releases](https://github.com/robert-schmidt/isoburn/releases) page:

| File | Description |
|------|-------------|
| `isoBURN-x.x.x.dmg` | macOS disk image with bundled JRE (no Java required) |
| `isoburn-x.x.x.jar` | Executable JAR (requires Java 21+) |

> **First Launch:** The app is not signed with an Apple Developer certificate. Right-click the app and select "Open" to bypass Gatekeeper.

### Run with Java CLI

If you have Java 21+ installed:

```bash
# Download the JAR from releases, then run:
java -jar isoburn-1.0.0.jar
```

## Requirements

### For the DMG Version
- macOS 10.14+ (Mojave or later)
- No Java installation required (JRE is bundled)

### For the JAR Version
- macOS 10.14+ (Mojave or later)
- Java 20 or later

### For Windows 11 ISOs (Optional)

Windows 11 ISOs contain an `install.wim` file larger than 4GB, which exceeds FAT32's file size limit. To burn Windows 11 ISOs, install `wimlib`:

```bash
brew install wimlib
```

> **Note:** This is only needed for Windows 11. Windows 10, Linux, and other ISOs work without wimlib.

## Usage

1. **Select ISO** - Click "Browse" to choose an ISO file
2. **Select Drive** - Choose your target USB drive from the dropdown
3. **Configure Options**:
   - **Bootable (UEFI)** - Enable for bootable installers (Windows, Linux, etc.)
   - **Split large files (Win11)** - Enable for Windows 11 ISOs (requires wimlib)
4. **Start Burn** - Click "Start Burn" and confirm the warning dialog
5. **Wait** - Progress will be shown in real-time
6. **Done** - Safely remove your USB drive when complete

### Supported ISOs

| ISO Type | Bootable | Split Files | Notes |
|----------|:--------:|:-----------:|-------|
| Windows 11 | Yes | Yes | Requires wimlib |
| Windows 10/8/7 | Yes | No | |
| Ubuntu / Linux | Yes | No | |
| macOS Recovery | Yes | No | |
| Data / Backup | No | No | |

## Building from Source

### Prerequisites

- Java 21 JDK ([Eclipse Temurin](https://adoptium.net/) recommended)
- Maven 3.8+
- macOS (required for packaging)

### Build & Run

```bash
# Clone the repository
git clone https://github.com/robert-schmidt/isoburn.git
cd isoburn

# Build the JAR
mvn clean package -DskipTests

# Run the application
java -jar target/isoburn-1.0.0.jar
```

### Create DMG for Distribution

```bash
./package.sh
```

This script:
1. Builds the JAR with Maven
2. Creates macOS .icns icon from the PNG
3. Packages the app with jpackage (bundles JRE)
4. Creates a distributable DMG

Output files in `target/dist/`:
- `isoBURN.app` - Application bundle
- `isoBURN-1.0.0.dmg` - Distributable disk image

## How It Works

isoBURN uses native macOS tools for disk operations and pure Java for file copying:

| Step | Tool | Purpose |
|------|------|---------|
| Detect drives | `diskutil list -plist` | Enumerate removable drives |
| Unmount | `diskutil unmountDisk` | Prepare drive for formatting |
| Format | `diskutil eraseDisk FAT32` | Format as FAT32 with MBR |
| Mount ISO | `hdiutil mount` | Access ISO contents |
| Copy files | Java NIO | Cross-platform file copy with progress |
| Split WIM | `wimlib-imagex split` | Handle Windows 11 large files |
| Eject | `diskutil eject` | Safely eject drive |

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Volume name for the formatted USB drive
isoburn.volume-name=ISOBURN

# Maximum WIM file size before splitting (GB)
isoburn.wim-max-size-gb=4

# Split chunk size for WIM files (MB)
isoburn.wim-split-size-mb=3800
```

## Troubleshooting

### No removable drives found
- Ensure your USB drive is plugged in
- Click "Refresh" to rescan
- Check if the drive appears in Disk Utility

### "wimlib is not installed"
```bash
brew install wimlib
```

### App won't open (Gatekeeper)
Right-click the app → "Open" → Click "Open" in the dialog

### Burn fails immediately
Run from Terminal to see detailed error logs:
```bash
java -jar isoburn-1.0.0.jar
```

## Project Structure

```
isoBURN/
├── pom.xml                           # Maven configuration
├── package.sh                        # DMG packaging script
├── README.md
└── src/main/
    ├── java/com/isoburn/
    │   ├── IsoBurnApplication.java   # Main entry point
    │   ├── controller/
    │   │   └── MainController.java   # UI controller
    │   ├── service/
    │   │   ├── IsoBurnService.java   # Burn orchestration
    │   │   ├── DriveDetectionService.java
    │   │   ├── WimSplitService.java
    │   │   └── CommandExecutor.java
    │   ├── model/
    │   │   ├── RemovableDrive.java
    │   │   ├── BurnProgress.java
    │   │   └── BurnResult.java
    │   └── util/
    │       └── PlistParser.java
    └── resources/
        ├── application.properties
        ├── fxml/main.fxml
        └── icons/
            ├── isoburn.svg
            └── isoburn.png
```

## Tech Stack

- **Java 20** - Language & Runtime
- **JavaFX 21** - GUI Framework
- **Spring Boot 3.2** - Dependency Injection
- **Maven** - Build System
- **jpackage** - Native Packaging

## License

MIT License - See [LICENSE](LICENSE) file for details.

## Author

**Robert Schmidt**

- Website: [robertschmidt.dev](https://robertschmidt.dev)
- Support: [Buy me a coffee](https://buymeacoffee.com/robbschmidt)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

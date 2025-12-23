# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2025-12-23

### Added
- Windows .exe portable application (requires Java 21+)
- Cross-platform packaging script with Launch4j integration
- Windows .ico icon generation via ImageMagick

## [1.0.0] - 2025-12-23

### Added
- Initial release of isoBURN
- Simple drag-and-drop GUI for burning ISO files to USB drives
- Automatic detection of removable USB drives and SD cards
- UEFI bootable USB creation for Windows and Linux installers
- Windows 11 support with automatic WIM file splitting (>4GB files)
- Real-time progress tracking with byte-level accuracy
- Detailed logging panel for monitoring burn operations
- Safety confirmation dialogs before formatting drives
- Drive filtering to prevent accidental system disk formatting
- Pure Java file copying (no external rsync dependency)
- macOS .app bundle with bundled JRE (no Java installation required)
- macOS .dmg installer for easy distribution
- Windows .exe portable application (requires Java 21+)
- Support for Windows 10/11, Ubuntu, Linux, and macOS recovery ISOs

### Technical
- Built with Java 21, JavaFX 21, and Spring Boot 3.2
- Uses native macOS `diskutil` and `hdiutil` for disk operations
- Optional `wimlib` integration for Windows 11 large file handling
- Cross-platform packaging via jpackage (macOS) and Launch4j (Windows)

## [Unreleased]

### Planned
- Linux support
- Drag-and-drop ISO file selection
- USB drive health check before burning
- Checksum verification (MD5/SHA256)

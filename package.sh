#!/bin/bash

# isoBURN Packaging Script
# Creates a native macOS .app bundle and .dmg installer

set -e

APP_NAME="isoBURN"
APP_VERSION="1.0.0"
MAIN_CLASS="com.isoburn.IsoBurnApplication"
VENDOR="Robert Schmidt"
COPYRIGHT="Copyright 2025 Robert Schmidt"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET_DIR="$PROJECT_DIR/target"
RESOURCES_DIR="$PROJECT_DIR/src/main/resources"
STAGING_DIR="$TARGET_DIR/staging"
ICON_DIR="$TARGET_DIR/icons"
OUTPUT_DIR="$TARGET_DIR/dist"

echo "=== isoBURN Packaging Script ==="
echo ""

# Step 1: Build the JAR
echo "[1/4] Building JAR..."
mvn clean package -DskipTests -q
JAR_FILE="$TARGET_DIR/isoburn-${APP_VERSION}.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found at $JAR_FILE"
    exit 1
fi
echo "      JAR built: $JAR_FILE"

# Step 2: Create .icns icon from PNG
echo "[2/4] Creating macOS icon (.icns)..."
mkdir -p "$ICON_DIR"
ICONSET_DIR="$ICON_DIR/isoburn.iconset"
rm -rf "$ICONSET_DIR"
mkdir -p "$ICONSET_DIR"

PNG_SOURCE="$RESOURCES_DIR/icons/isoburn.png"
if [ ! -f "$PNG_SOURCE" ]; then
    echo "ERROR: PNG icon not found at $PNG_SOURCE"
    exit 1
fi

# Create iconset with required sizes
sips -z 16 16     "$PNG_SOURCE" --out "$ICONSET_DIR/icon_16x16.png" > /dev/null 2>&1
sips -z 32 32     "$PNG_SOURCE" --out "$ICONSET_DIR/icon_16x16@2x.png" > /dev/null 2>&1
sips -z 32 32     "$PNG_SOURCE" --out "$ICONSET_DIR/icon_32x32.png" > /dev/null 2>&1
sips -z 64 64     "$PNG_SOURCE" --out "$ICONSET_DIR/icon_32x32@2x.png" > /dev/null 2>&1
sips -z 128 128   "$PNG_SOURCE" --out "$ICONSET_DIR/icon_128x128.png" > /dev/null 2>&1
sips -z 256 256   "$PNG_SOURCE" --out "$ICONSET_DIR/icon_128x128@2x.png" > /dev/null 2>&1
sips -z 256 256   "$PNG_SOURCE" --out "$ICONSET_DIR/icon_256x256.png" > /dev/null 2>&1
sips -z 512 512   "$PNG_SOURCE" --out "$ICONSET_DIR/icon_256x256@2x.png" > /dev/null 2>&1
sips -z 512 512   "$PNG_SOURCE" --out "$ICONSET_DIR/icon_512x512.png" > /dev/null 2>&1
sips -z 1024 1024 "$PNG_SOURCE" --out "$ICONSET_DIR/icon_512x512@2x.png" > /dev/null 2>&1

# Convert iconset to icns
iconutil -c icns "$ICONSET_DIR" -o "$ICON_DIR/isoburn.icns"
echo "      Icon created: $ICON_DIR/isoburn.icns"

# Step 3: Prepare staging directory (only the JAR)
echo "[3/4] Creating .app bundle..."
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR"
cp "$JAR_FILE" "$STAGING_DIR/"

# Clean previous output
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "$VENDOR" \
    --copyright "$COPYRIGHT" \
    --description "ISO to USB Burner for macOS" \
    --icon "$ICON_DIR/isoburn.icns" \
    --input "$STAGING_DIR" \
    --main-jar "isoburn-${APP_VERSION}.jar" \
    --main-class "$MAIN_CLASS" \
    --dest "$OUTPUT_DIR" \
    --java-options "-XstartOnFirstThread" \
    --mac-package-name "$APP_NAME"

echo "      App bundle created: $OUTPUT_DIR/$APP_NAME.app"

# Step 4: Create DMG installer
echo "[4/4] Creating DMG installer..."

jpackage \
    --type dmg \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "$VENDOR" \
    --copyright "$COPYRIGHT" \
    --description "ISO to USB Burner for macOS" \
    --icon "$ICON_DIR/isoburn.icns" \
    --input "$STAGING_DIR" \
    --main-jar "isoburn-${APP_VERSION}.jar" \
    --main-class "$MAIN_CLASS" \
    --dest "$OUTPUT_DIR" \
    --java-options "-XstartOnFirstThread" \
    --mac-package-name "$APP_NAME"

echo ""
echo "=== Packaging Complete ==="
echo ""
echo "App bundle: $OUTPUT_DIR/$APP_NAME.app"
echo "DMG file:   $OUTPUT_DIR/$APP_NAME-${APP_VERSION}.dmg"
echo ""
echo "To test the app:"
echo "  open \"$OUTPUT_DIR/$APP_NAME.app\""
echo ""
echo "To distribute, share the DMG file."

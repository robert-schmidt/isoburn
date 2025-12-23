#!/bin/bash

# isoBURN Cross-Platform Packaging Script
# Creates native installers:
#   - macOS: .app bundle and .dmg installer
#   - Windows: .exe application (via Launch4j, requires Java on Windows)

set -e

APP_NAME="isoBURN"
APP_VERSION="1.0.0"
MAIN_CLASS="org.springframework.boot.loader.launch.JarLauncher"
VENDOR="Robert Schmidt"
COPYRIGHT="Copyright 2025 Robert Schmidt"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET_DIR="$PROJECT_DIR/target"
RESOURCES_DIR="$PROJECT_DIR/src/main/resources"
STAGING_DIR="$TARGET_DIR/staging"
ICON_DIR="$TARGET_DIR/icons"
OUTPUT_DIR="$TARGET_DIR/dist"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== isoBURN Packaging Script ===${NC}"
echo ""

# Step 1: Build the JAR
echo -e "${YELLOW}[1/5] Building JAR...${NC}"
mvn clean package -DskipTests -q
JAR_FILE="$TARGET_DIR/isoburn-${APP_VERSION}.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}ERROR: JAR file not found at $JAR_FILE${NC}"
    exit 1
fi
echo -e "${GREEN}      JAR built: $JAR_FILE${NC}"

# Step 2: Create .icns icon from PNG
echo -e "${YELLOW}[2/5] Creating macOS icon (.icns)...${NC}"
mkdir -p "$ICON_DIR"
ICONSET_DIR="$ICON_DIR/isoburn.iconset"
rm -rf "$ICONSET_DIR"
mkdir -p "$ICONSET_DIR"

PNG_SOURCE="$RESOURCES_DIR/icons/isoburn.png"
if [ ! -f "$PNG_SOURCE" ]; then
    echo -e "${RED}ERROR: PNG icon not found at $PNG_SOURCE${NC}"
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
echo -e "${GREEN}      Icon created: $ICON_DIR/isoburn.icns${NC}"

# Step 3: Prepare staging directory (only the JAR)
echo -e "${YELLOW}[3/5] Creating .app bundle...${NC}"
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
    --mac-package-name "$APP_NAME"

echo -e "${GREEN}      App bundle created: $OUTPUT_DIR/$APP_NAME.app${NC}"

# Step 4: Create DMG installer
echo -e "${YELLOW}[4/5] Creating DMG installer...${NC}"

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
    --mac-package-name "$APP_NAME"

echo -e "${GREEN}      DMG created: $OUTPUT_DIR/$APP_NAME-${APP_VERSION}.dmg${NC}"

# Step 5: Create Windows .exe (via Launch4j)
echo -e "${YELLOW}[5/5] Creating Windows .exe (via Launch4j)...${NC}"

# Check if Launch4j is installed
LAUNCH4J=""
if [ -d "/Applications/launch4j" ]; then
    LAUNCH4J="/Applications/launch4j/launch4j"
elif [ -d "$HOME/Applications/launch4j" ]; then
    LAUNCH4J="$HOME/Applications/launch4j/launch4j"
elif command -v launch4j &> /dev/null; then
    LAUNCH4J="launch4j"
fi

if [ -z "$LAUNCH4J" ]; then
    echo -e "${YELLOW}      Launch4j not found. To create Windows .exe from macOS:${NC}"
    echo -e "${YELLOW}        1. Download from: https://launch4j.sourceforge.net/${NC}"
    echo -e "${YELLOW}        2. Extract to /Applications/launch4j/ or ~/Applications/launch4j/${NC}"
    echo -e "${YELLOW}        Or install via: brew install launch4j${NC}"
    echo ""
else
    # Fix Windows line endings in launch4j script if needed
    sed -i '' 's/\r$//' "${LAUNCH4J}" 2>/dev/null || true
    chmod +x "${LAUNCH4J}" 2>/dev/null || true

    # Create Windows .ico icon if we have ImageMagick
    WIN_ICON=""
    WIN_ICO="$ICON_DIR/${APP_NAME}.ico"
    if command -v magick &> /dev/null; then
        magick "$PNG_SOURCE" -define icon:auto-resize=256,128,64,48,32,16 "$WIN_ICO" 2>/dev/null && WIN_ICON="$WIN_ICO"
        echo -e "${GREEN}      Windows icon created: $WIN_ICO${NC}"
    elif command -v convert &> /dev/null; then
        convert "$PNG_SOURCE" -define icon:auto-resize=256,128,64,48,32,16 "$WIN_ICO" 2>/dev/null && WIN_ICON="$WIN_ICO"
        echo -e "${GREEN}      Windows icon created: $WIN_ICO${NC}"
    else
        echo -e "${YELLOW}      ImageMagick not found - proceeding without custom icon${NC}"
        echo -e "${YELLOW}      Install via: brew install imagemagick${NC}"
    fi

    # Create Launch4j config XML
    L4J_CONFIG="$TARGET_DIR/launch4j-config.xml"
    EXE_OUTPUT="$OUTPUT_DIR/${APP_NAME}.exe"

    cat > "${L4J_CONFIG}" << 'LAUNCH4J_EOF'
<?xml version="1.0" encoding="UTF-8"?>
<launch4jConfig>
  <dontWrapJar>false</dontWrapJar>
  <headerType>gui</headerType>
  <jar>__JAR_FILE__</jar>
  <outfile>__EXE_OUTPUT__</outfile>
  <errTitle>isoBURN</errTitle>
  <cmdLine></cmdLine>
  <chdir>.</chdir>
  <priority>normal</priority>
  <downloadUrl>https://adoptium.net/</downloadUrl>
  <supportUrl></supportUrl>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
  <manifest></manifest>
  __ICON_LINE__
  <classPath>
    <mainClass>org.springframework.boot.loader.launch.JarLauncher</mainClass>
  </classPath>
  <jre>
    <path></path>
    <bundledJre64Bit>false</bundledJre64Bit>
    <bundledJreAsFallback>false</bundledJreAsFallback>
    <minVersion>21</minVersion>
    <maxVersion></maxVersion>
    <jdkPreference>preferJre</jdkPreference>
    <runtimeBits>64</runtimeBits>
    <opt>-Xmx512m</opt>
  </jre>
  <versionInfo>
    <fileVersion>1.0.0.0</fileVersion>
    <txtFileVersion>__APP_VERSION__</txtFileVersion>
    <fileDescription>isoBURN - ISO to USB Burner</fileDescription>
    <copyright>__COPYRIGHT__</copyright>
    <productVersion>1.0.0.0</productVersion>
    <txtProductVersion>__APP_VERSION__</txtProductVersion>
    <productName>isoBURN</productName>
    <companyName>__VENDOR__</companyName>
    <internalName>isoBURN</internalName>
    <originalFilename>isoBURN.exe</originalFilename>
    <trademarks></trademarks>
    <language>ENGLISH_US</language>
  </versionInfo>
</launch4jConfig>
LAUNCH4J_EOF

    # Replace placeholders
    sed -i.bak "s|__JAR_FILE__|${JAR_FILE}|g" "${L4J_CONFIG}"
    sed -i.bak "s|__EXE_OUTPUT__|${EXE_OUTPUT}|g" "${L4J_CONFIG}"
    sed -i.bak "s|__APP_VERSION__|${APP_VERSION}|g" "${L4J_CONFIG}"
    sed -i.bak "s|__COPYRIGHT__|${COPYRIGHT}|g" "${L4J_CONFIG}"
    sed -i.bak "s|__VENDOR__|${VENDOR}|g" "${L4J_CONFIG}"
    if [ -n "$WIN_ICON" ]; then
        sed -i.bak "s|__ICON_LINE__|<icon>${WIN_ICON}</icon>|g" "${L4J_CONFIG}"
    else
        sed -i.bak "s|__ICON_LINE__||g" "${L4J_CONFIG}"
    fi
    rm -f "${L4J_CONFIG}.bak"

    # Run Launch4j
    if "${LAUNCH4J}" "${L4J_CONFIG}" 2>/dev/null; then
        echo -e "${GREEN}      Windows .exe created: ${EXE_OUTPUT}${NC}"
    else
        echo -e "${YELLOW}      Launch4j failed. The .exe may not have been created.${NC}"
    fi
fi

echo ""
echo -e "${GREEN}=== Packaging Complete ===${NC}"
echo ""
echo "macOS:"
echo "  App bundle: $OUTPUT_DIR/$APP_NAME.app"
echo "  DMG file:   $OUTPUT_DIR/$APP_NAME-${APP_VERSION}.dmg"
if [ -f "$OUTPUT_DIR/${APP_NAME}.exe" ]; then
    echo ""
    echo "Windows:"
    echo "  Executable: $OUTPUT_DIR/${APP_NAME}.exe"
    echo -e "  ${YELLOW}Note: Requires Java 21+ installed on Windows${NC}"
fi
echo ""
echo "To test the macOS app:"
echo "  open \"$OUTPUT_DIR/$APP_NAME.app\""
echo ""

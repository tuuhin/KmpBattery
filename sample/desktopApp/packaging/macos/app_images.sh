#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Default values
INPUT_SVG=""
BG_COLOR="none"
ROUND_PERCENT="22"   # Apple's classic icon corner percentage
FILL_PERCENT="75"    # Foreground fills this % of the canvas, centered

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -c|--color)
            BG_COLOR="$2"
            shift 2
            ;;
        -r|--rounded)
            ROUND_PERCENT="$2"
            shift 2
            ;;
        -f|--fill)
            FILL_PERCENT="$2"
            shift 2
            ;;
        *)
            if [ -z "$INPUT_SVG" ]; then
                INPUT_SVG="$1"
                shift
            else
                echo "Unknown argument: $1"
                exit 1
            fi
            ;;
    esac
done

# ------------------------------------------------------------------------------
# Validation
# ------------------------------------------------------------------------------

if [ -z "$INPUT_SVG" ] || [ ! -f "$INPUT_SVG" ]; then
    echo "Error: Input SVG file not found or not specified."
    exit 1
fi

if ! command -v magick &> /dev/null; then
    echo "Error: ImageMagick ('magick') not found in PATH."
    exit 1
fi

if ! command -v iconutil &> /dev/null; then
    echo "Error: iconutil not found."
    exit 1
fi

if ! command -v qlmanage &> /dev/null; then
    echo "Error: qlmanage not found."
    exit 1
fi

# ------------------------------------------------------------------------------
# Paths
# ------------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ICONS_DIR="$(dirname "$SCRIPT_DIR")/macos"

mkdir -p "$ICONS_DIR"

ICONSET_DIR="${SCRIPT_DIR}/app.iconset"
FINAL_ICNS="${ICONS_DIR}/kmp_battery.icns"

# macOS 26 layered icon
FINAL_ICON_DIR="${ICONS_DIR}/kmp_battery.icon"
ASSETS_DIR="${FINAL_ICON_DIR}/Assets"

FOREGROUND_PNG="${ASSETS_DIR}/foreground.png"
BACKGROUND_JPEG="${ASSETS_DIR}/background.jpeg"

MASTER_PNG="${SCRIPT_DIR}/temp_master_4096.png"

echo "Processing macOS Icon Asset Generation..."
echo "  Input : $INPUT_SVG"
echo "  Color : $BG_COLOR"
echo "  Fill  : ${FILL_PERCENT}%"
echo "  Radius: ${ROUND_PERCENT}%"

rm -rf "$ICONSET_DIR"
rm -rf "$FINAL_ICON_DIR"

mkdir -p "$ICONSET_DIR"
mkdir -p "$ASSETS_DIR"

# ------------------------------------------------------------------------------
# Step 1 - Rasterize SVG
# ------------------------------------------------------------------------------

echo "Rasterizing SVG..."

qlmanage -t -s 2048 -o "$SCRIPT_DIR" "$INPUT_SVG" >/dev/null 2>&1

GENERATED_FILE="${SCRIPT_DIR}/$(basename "$INPUT_SVG").png"

if [ ! -f "$GENERATED_FILE" ]; then
    echo "Failed to rasterize SVG."
    exit 1
fi

# ------------------------------------------------------------------------------
# Step 2 - Extract transparent foreground
# ------------------------------------------------------------------------------

echo "Preparing foreground..."

TEMP_FG="${SCRIPT_DIR}/temp_clean_fg.png"

magick "$GENERATED_FILE" \
    -transparent white \
    -trim +repage \
    "$TEMP_FG"

# ------------------------------------------------------------------------------
# Step 3 - Generate macOS 26 Layered Icon
# ------------------------------------------------------------------------------

echo "Generating macOS 26 layered icon..."

# Foreground (1024x1024 transparent)

magick "$TEMP_FG" \
    -resize 788x788 \
    -background none \
    -gravity center \
    -extent 1024x1024 \
    "$FOREGROUND_PNG"

# Background

if [ "$BG_COLOR" = "none" ]; then
    magick "$GENERATED_FILE" \
        -resize 1024x1024^ \
        -gravity center \
        -extent 1024x1024 \
        -blur 0x18 \
        "$BACKGROUND_JPEG"
else
    magick \
        -size 1024x1024 \
        "xc:${BG_COLOR}" \
        "$BACKGROUND_JPEG"
fi

# Icon.json

cat > "${FINAL_ICON_DIR}/icon.json" <<'EOF'
{
  "fill": {
    "automatic-gradient": "extended-srgb:0.00000,0.53333,1.00000,1.00000"
  },
  "groups": [
    {
      "layers": [
        {
          "blend-mode-specializations": [
            {
              "value": "plus-darker"
            },
            {
              "appearance": "dark",
              "value": "soft-light"
            }
          ],
          "image-name": "foreground.png",
          "name": "foreground",
          "opacity": 0.8,
          "position": {
            "scale": 0.77,
            "translation-in-points": [
              0,
              0
            ]
          }
        },
        {
          "blend-mode": "normal",
          "glass": true,
          "image-name": "background.jpeg",
          "name": "Background",
          "opacity": 0.9,
          "position": {
            "scale": 1,
            "translation-in-points": [
              0,
              0
            ]
          }
        }
      ],
      "shadow": {
        "kind": "neutral",
        "opacity": 0.5
      },
      "translucency": {
        "enabled": true,
        "value": 0.5
      }
    }
  ],
  "supported-platforms": {
    "squares": [
      "macOS"
    ]
  }
}
EOF

# ------------------------------------------------------------------------------
# Step 4 - Build 4096x4096 master
# ------------------------------------------------------------------------------

echo "Building master icon..."

rm -f "$GENERATED_FILE"

CANVAS=4096
FG_SIZE=$(( CANVAS * FILL_PERCENT / 100 ))

magick \
    -size "${CANVAS}x${CANVAS}" \
    "xc:${BG_COLOR}" \
    \( "$TEMP_FG" -resize "${FG_SIZE}x${FG_SIZE}" \) \
    -gravity center \
    -compose Over \
    -composite \
    "$MASTER_PNG"

rm -f "$TEMP_FG"

# ------------------------------------------------------------------------------
# Step 5 - Generate iconset
# ------------------------------------------------------------------------------

echo "Generating iconset..."

sizes=(
    "16x16 16"
    "16x16@2x 32"
    "32x32 32"
    "32x32@2x 64"
    "128x128 128"
    "128x128@2x 256"
    "256x256 256"
    "256x256@2x 512"
    "512x512 512"
    "512x512@2x 1024"
)

for item in "${sizes[@]}"; do
    read -r suffix size <<< "$item"

    OUT="${ICONSET_DIR}/icon_${suffix}.png"

    RAD_PX=$(( size * ROUND_PERCENT / 100 ))

    if [ "$ROUND_PERCENT" -ne 0 ]; then
        magick \
            "$MASTER_PNG" \
            -filter Lanczos \
            -resize "${size}x${size}" \
            \( +clone \
               -alpha transparent \
               -background none \
               -draw "fill white roundrectangle 0,0 ${size},${size} ${RAD_PX},${RAD_PX}" \
            \) \
            -alpha off \
            -compose CopyAlpha \
            -composite \
            -colorspace sRGB \
            "$OUT"
    else
        magick \
            "$MASTER_PNG" \
            -filter Lanczos \
            -resize "${size}x${size}" \
            -colorspace sRGB \
            "$OUT"
    fi
done

# ------------------------------------------------------------------------------
# Step 6 - Create ICNS
# ------------------------------------------------------------------------------

echo "Packaging icns..."

iconutil -c icns "$ICONSET_DIR" -o "$FINAL_ICNS"

# ------------------------------------------------------------------------------
# Cleanup
# ------------------------------------------------------------------------------

rm -f "$MASTER_PNG"
rm -rf "$ICONSET_DIR"

echo
echo "Done."
echo
echo "Generated:"
echo "  • $FINAL_ICNS"
echo "  • $FINAL_ICON_DIR"
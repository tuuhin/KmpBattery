#!/bin/bash

# Exit immediately if a command fails
set -e

# Default values
INPUT_SVG=""
OUTPUT_PNG=""
BG_COLOR="none"
ROUND_PERCENT="22"   # Corner rounding radius as % of size
FILL_PERCENT="75"    # Foreground fills this % of the 512x512 canvas
CANVAS_SIZE="512"    # Target Linux icon size in pixels

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
        -o|--output)
            OUTPUT_PNG="$2"
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
    echo "Usage: ./generate_linux_icon.sh input.svg [-o output.png] [-c #HexColor|none] [-r round%] [-f fill%]"
    exit 1
fi

if ! command -v magick &> /dev/null; then
    echo "Error: ImageMagick ('magick') not found in PATH."
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default output location if not specified
if [ -z "$OUTPUT_PNG" ]; then
    ICONS_DIR="$(dirname "$SCRIPT_DIR")/linux"
    mkdir -p "$ICONS_DIR"
    OUTPUT_PNG="${ICONS_DIR}/app.png"
else
    mkdir -p "$(dirname "$OUTPUT_PNG")"
fi

TEMP_FG="${SCRIPT_DIR}/temp_clean_fg.png"
MASTER_PNG="${SCRIPT_DIR}/temp_master.png"

echo "Processing Linux PNG Icon Generation..."
echo "  Input : $INPUT_SVG"
echo "  Output: $OUTPUT_PNG"
echo "  Color : $BG_COLOR"
echo "  Fill  : ${FILL_PERCENT}%"
echo "  Radius: ${ROUND_PERCENT}%"

# ------------------------------------------------------------------------------
# Step 1 - Extract and Clean Foreground SVG
# ------------------------------------------------------------------------------

echo "Rasterizing SVG..."

# Render SVG cleanly directly using ImageMagick
magick -density 300 "$INPUT_SVG" \
    -transparent white \
    -trim +repage \
    "$TEMP_FG"

# ------------------------------------------------------------------------------
# Step 2 - Compose Canvas (High Res Master)
# ------------------------------------------------------------------------------

echo "Building high-resolution master..."

MASTER_SIZE=2048
FG_SIZE=$(( MASTER_SIZE * FILL_PERCENT / 100 ))

if [ "$BG_COLOR" = "none" ]; then
    magick \
        -size "${MASTER_SIZE}x${MASTER_SIZE}" \
        xc:none \
        \( "$TEMP_FG" -resize "${FG_SIZE}x${FG_SIZE}" \) \
        -gravity center \
        -compose Over \
        -composite \
        "$MASTER_PNG"
else
    magick \
        -size "${MASTER_SIZE}x${MASTER_SIZE}" \
        "xc:${BG_COLOR}" \
        \( "$TEMP_FG" -resize "${FG_SIZE}x${FG_SIZE}" \) \
        -gravity center \
        -compose Over \
        -composite \
        "$MASTER_PNG"
fi

# ------------------------------------------------------------------------------
# Step 3 - Apply Corner Mask & Resize to 512x512
# ------------------------------------------------------------------------------

echo "Generating 512x512 target PNG..."

RAD_PX=$(( CANVAS_SIZE * ROUND_PERCENT / 100 ))

if [ "$ROUND_PERCENT" -ne 0 ] && [ "$BG_COLOR" != "none" ]; then
    # Round corners only if a background color is defined
    magick \
        "$MASTER_PNG" \
        -filter Lanczos \
        -resize "${CANVAS_SIZE}x${CANVAS_SIZE}" \
        \( +clone \
           -alpha transparent \
           -background none \
           -draw "fill white roundrectangle 0,0 ${CANVAS_SIZE},${CANVAS_SIZE} ${RAD_PX},${RAD_PX}" \
        \) \
        -alpha off \
        -compose CopyAlpha \
        -composite \
        -colorspace sRGB \
        "$OUTPUT_PNG"
else
    # Transparent background or 0% rounding
    magick \
        "$MASTER_PNG" \
        -filter Lanczos \
        -resize "${CANVAS_SIZE}x${CANVAS_SIZE}" \
        -colorspace sRGB \
        "$OUTPUT_PNG"
fi

# ------------------------------------------------------------------------------
# Cleanup
# ------------------------------------------------------------------------------

rm -f "$TEMP_FG"
rm -f "$MASTER_PNG"

echo
echo "Done! Linux app icon saved to:"
echo "  • $OUTPUT_PNG"

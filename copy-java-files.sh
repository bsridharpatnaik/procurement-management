#!/bin/bash

# Source: the root folder of your Java Spring Boot project
SOURCE_DIR="."

# Destination directory
DEST_DIR="/Users/bsridharpatnaik/Downloads/code"

# Clear destination directory if it already exists
rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"

# Copy all .java files preserving folder structure
cd "$SOURCE_DIR" || exit
find . -name "*.java" -exec bash -c '
  for filepath; do
    destpath="'$DEST_DIR'/${filepath#./}"
    mkdir -p "$(dirname "$destpath")"
    cp "$filepath" "$destpath"
  done
' bash {} +

echo "✅ All Java files copied to $DEST_DIR"


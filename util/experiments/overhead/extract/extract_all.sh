#!/bin/bash
# ------------------------------------------------------------------
# This script extracts all results from running Filibuster experiments
# 
# Usage: ./extract_all.sh <target_path>
# ------------------------------------------------------------------

# Check if a folder argument is provided

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )

if [ -z "$1" ]; then
  echo "Usage: $0 <folder_path>"
  exit 1
fi

TARGET_FOLDER="$1"

find "$TARGET_FOLDER" -mindepth 1 -maxdepth 1 -type d | while read -r subdir; do
  "$parent_path/extract.sh" "$subdir"
done

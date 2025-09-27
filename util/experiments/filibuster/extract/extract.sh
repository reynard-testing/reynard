#!/bin/bash

# This script extracts the results from running Filibuster experiments
# Usage: ./extract.sh <target_path>

# Check if a folder argument is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <folder_path>"
  exit 1
fi

TARGET_FOLDER="$1"
OUTPUT_FILE_TIMINGS="$TARGET_FOLDER/timings.out"
OUTPUT_FILE_CASES="$TARGET_FOLDER/cases.out"

# Clear the output file if it exists, or create it
> "$OUTPUT_FILE_TIMINGS"
> "$OUTPUT_FILE_CASES"

echo "Processing log files in subdirectories of: $TARGET_FOLDER"
echo "Timings will be saved to: $OUTPUT_FILE_TIMINGS"
echo "Number of tests will be saved to: $OUTPUT_FILE_CASES"
echo ""

# Find all .log files in subdirectories and process them
find "$TARGET_FOLDER" -mindepth 1 -type f -name "*.log" | while read -r log_file; do
  # Extract the time elapsed from each matching line
  grep -oP '\[FILIBUSTER\] \[INFO\]: Time elapsed: \K[0-9]+\.[0-9]+' "$log_file" >> "$OUTPUT_FILE_TIMINGS"

  
  # Extract the number of tests attempted from each matching line (new functionality)
  grep -oP '\[FILIBUSTER\] \[INFO\]: Number of tests attempted: \K[0-9]+' "$log_file" >> "$OUTPUT_FILE_CASES"
done

echo "Extraction complete."

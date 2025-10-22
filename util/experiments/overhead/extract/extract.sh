#!/bin/bash
# ------------------------------------------------------------------
# This script extracts the results from running Filibuster experiments
# 
# Usage: ./extract.sh <target_path>
# ------------------------------------------------------------------

# Check if a folder argument is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <folder_path>"
  exit 1
fi

TARGET_FOLDER="$1"

LATENCY_FILE="$TARGET_FOLDER/latencies.out"
REQ_PER_SEC_FILE="$TARGET_FOLDER/req_per_sec.out"
REQS_SEC_FILE="$TARGET_FOLDER/reqs.out"
P50_FILE="$TARGET_FOLDER/p50.out"
P90_FILE="$TARGET_FOLDER/p90.out"
P99_FILE="$TARGET_FOLDER/p99.out"

# Clear the output files if they exist, or create them
> "$LATENCY_FILE"
> "$REQ_PER_SEC_FILE"
> "$REQS_SEC_FILE"
> "$P50_FILE"
> "$P90_FILE"
> "$P99_FILE"

echo "Processing log files in subdirectories of: $TARGET_FOLDER"
echo ""
# Find all .log files in subdirectories and process them
find "$TARGET_FOLDER" -mindepth 1 -type f -name "wrk.log" | while read -r log_file; do
  # Extract average latency from the log file and append to LATENCY_FILE
  avg_latency=$(grep -m1 'Latency' "$log_file" | awk '{print $2}' | sed 's/[mu]s//')
  echo "$avg_latency" >> "$LATENCY_FILE"

  # Extract requests per second from the log file and append to REQ_PER_SEC_FILE
  req_per_sec=$(grep -m1 'Requests/sec' "$log_file" | awk '{print $2}')
  echo "$req_per_sec" >> "$REQ_PER_SEC_FILE"

  # Extract total requests from the log file and append to REQS_SEC_FILE
  total_reqs=$(grep -m1 'requests in' "$log_file" | awk '{print $1}')
  echo "$total_reqs" >> "$REQS_SEC_FILE"

  # Extract p50 latency from the log file and append to P50_FILE
  p50_latency=$(grep -m1 '50%' "$log_file" | awk '{print $2}' | sed 's/ms//')
  echo "$p50_latency" >> "$P50_FILE"

  # Extract p90 latency from the log file and append to P90_FILE
  p90_latency=$(grep -m1 '90%' "$log_file" | awk '{print $2}' | sed 's/ms//')
  echo "$p90_latency" >> "$P90_FILE"

  # Extract p99 latency from the log file and append to P99_FILE
  p99_latency=$(grep -m1 '99%' "$log_file" | awk '{print $2}' | sed 's/ms//')
  echo "$p99_latency" >> "$P99_FILE"
done

# Store averages for each metric
awk '{sum+=$1} END {if (NR>0) print sum/NR; else print "0"}' "$LATENCY_FILE" > "$TARGET_FOLDER/latency.avg"
awk '{sum+=$1} END {if (NR>0) print sum/NR; else print "0"}' "$REQ_PER_SEC_FILE" > "$TARGET_FOLDER/req_per_sec.avg"
awk '{sum+=$1} END {if (NR>0) print sum/NR; else print "0"}' "$REQS_SEC_FILE" > "$TARGET_FOLDER/reqs.avg"
awk '{sum+=$1} END {if (NR>0) print sum/NR; else print "0"}' "$P50_FILE" > "$TARGET_FOLDER/p50.avg"
awk '{sum+=$1} END {if (NR>0) print sum/NR; else print "0"}' "$P90_FILE" > "$TARGET_FOLDER/p90.avg"
awk '{sum+=$1} END {if (NR>0) print sum/NR; else print "0"}' "$P99_FILE" > "$TARGET_FOLDER/p99.avg"

echo "Extraction complete."

#!/bin/sh
set -eu

SCRIPT_PATH="${K6_SCRIPT_PATH:-/work/constant_rate.js}"
echo "k6 script: $SCRIPT_PATH"
echo "compat mode: ${K6_COMPATIBILITY_MODE:-extended}"

while :; do
  echo "Starting k6..."
  k6 run --compatibility-mode=extended -o experimental-prometheus-rw "$SCRIPT_PATH" || true
  echo "Run finished, sleeping 10s..."
  sleep 10
done

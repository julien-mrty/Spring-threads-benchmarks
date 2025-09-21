#!/bin/sh
set -e
while :; do
  echo "Starting k6..."
  k6 run -o experimental-prometheus-rw scripts/k6/constant_rate.js || true
  echo "Run finished, sleeping 10s..."
  sleep 10
done

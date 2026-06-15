#!/usr/bin/env bash
#-*- coding: utf-8-unix -*-

# --- Configuration & Arguments ---
SERVICE_NAME=${1:-"counter"}
DATA_FILE=${2:-"./curl-data"}
DURATION=${3:-"5s"}
BASE_URL="http://localhost:8080/casualcallersync"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LUA_SCRIPT="$SCRIPT_DIR/soak-post.lua"

# --- Validation ---
WRK_PATH=$(command -v wrk)
if [ -z "$WRK_PATH" ]; then
    echo "Error: 'wrk' not found."
    exit 1
fi

if [ ! -f "$DATA_FILE" ]; then
    echo "Error: Payload file '$DATA_FILE' not found."
    exit 1
fi

if [ ! -f "$LUA_SCRIPT" ]; then
    echo "Error: Lua script '$LUA_SCRIPT' not found."
    exit 1
fi

# --- Check endpoint is up ---
echo -n "Checking endpoint... "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H 'Content-Type: application/casual-x-octet' \
    --data-binary @"$DATA_FILE" \
    "$BASE_URL/$SERVICE_NAME" 2>&1)

if [ $? -ne 0 ] || [ -z "$HTTP_CODE" ] || [ "$HTTP_CODE" = "000" ]; then
    echo -e "\033[0;31mFAILED\033[0m - endpoint not reachable at $BASE_URL/$SERVICE_NAME"
    exit 1
fi

if [ "$HTTP_CODE" -ge 400 ] 2>/dev/null; then
    echo -e "\033[0;31mFAILED\033[0m - endpoint returned HTTP $HTTP_CODE"
    exit 1
fi

echo -e "\033[0;32mOK\033[0m (HTTP $HTTP_CODE)"
echo

# --- Header ---
echo "========================================================"
echo "  CASUAL JCA SOAK TEST: $SERVICE_NAME"
echo "========================================================"
echo "Binary:     $WRK_PATH"
echo "Script:     $LUA_SCRIPT"
echo "Payload:    $DATA_FILE ($(du -h "$DATA_FILE" | cut -f1))"
echo "Duration:   $DURATION per concurrency level"
echo "Endpoint:   $BASE_URL/$SERVICE_NAME"
echo "========================================================"
echo

# --- Error log ---
ERROR_LOG="soak-test-errors-$(date +%Y%m%dT%H%M%S).log"

# --- Test Loop ---
TOTAL=0
PASSED=0
FAILED=0

for c in 10 50 100 200 300 500 1000; do
   TOTAL=$((TOTAL + 1))
   echo -n "Running Concurrency $c... "

   # Threads: up to 4, but never more than connections
   THREADS=$(( c < 4 ? c : 4 ))

   # Per-run temp files
   OUTPUT_FILE=$(mktemp)
   ERR_BODY_FILE=$(mktemp)

   WRK_BODY_FILE="$DATA_FILE" \
   WRK_ERROR_FILE="$ERR_BODY_FILE" \
   $WRK_PATH -t"$THREADS" -c"$c" -d"$DURATION" \
       -s "$LUA_SCRIPT" \
       "$BASE_URL/$SERVICE_NAME" \
       >"$OUTPUT_FILE" 2>&1

   WRK_EXIT=$?
   WRK_OUTPUT=$(cat "$OUTPUT_FILE")
   rm -f "$OUTPUT_FILE"

   if [ $WRK_EXIT -ne 0 ]; then
      echo -e "\033[0;31mERROR\033[0m - wrk exited with code $WRK_EXIT (logged to $ERROR_LOG)"
      {
         echo "========================================================"
         echo "  Concurrency $c — wrk exit code $WRK_EXIT — $(date)"
         echo "========================================================"
         echo "$WRK_OUTPUT"
         echo
      } >> "$ERROR_LOG"
      rm -f "$ERR_BODY_FILE"
      FAILED=$((FAILED + 1))
      curl -s -o /dev/null -w "" "$BASE_URL/$SERVICE_NAME" 2>/dev/null
      if [ $? -ne 0 ]; then
          echo "   -> Endpoint is no longer reachable, aborting."
          break
      fi
      sleep 2
      continue
   fi

   # Parse machine-readable output from Lua done()
   TOTAL_REQ=$(echo "$WRK_OUTPUT" | grep "^WRK_TOTAL:" | cut -d: -f2)
   RPS=$(echo "$WRK_OUTPUT" | grep "^WRK_RPS:" | cut -d: -f2)
   DURATION_S=$(echo "$WRK_OUTPUT" | grep "^WRK_DURATION:" | cut -d: -f2)
   SOCK_ERR=$(echo "$WRK_OUTPUT" | grep "^WRK_SOCK_ERR:" | cut -d: -f2)

   # Count non-2xx from the error body file (one line per non-2xx response)
   if [ -s "$ERR_BODY_FILE" ]; then
      NON_2XX=$(wc -l < "$ERR_BODY_FILE")
   else
      NON_2XX=0
   fi

   TOTAL_REQ=${TOTAL_REQ:-0}
   SOCK_ERR=${SOCK_ERR:-0}

   if [ "$TOTAL_REQ" -eq 0 ] 2>/dev/null; then
      echo -e "\033[0;31mERROR\033[0m - no requests completed (logged to $ERROR_LOG)"
      {
         echo "========================================================"
         echo "  Concurrency $c — no requests completed — $(date)"
         echo "========================================================"
         echo "$WRK_OUTPUT"
         echo
      } >> "$ERROR_LOG"
      rm -f "$ERR_BODY_FILE"
      FAILED=$((FAILED + 1))
      sleep 2
      continue
   fi

   ERRORS=$((NON_2XX + SOCK_ERR))

   if [ "$ERRORS" -eq 0 ]; then
      echo -e "\033[0;32mPASS\033[0m (${RPS} req/s | ${DURATION_S}s | ${TOTAL_REQ} reqs)"
      PASSED=$((PASSED + 1))
   else
      echo -e "\033[0;31mFAIL\033[0m (logged to $ERROR_LOG)"
      [ "$NON_2XX" -gt 0 ] && echo "   -> Non-2xx responses: $NON_2XX"
      [ "$SOCK_ERR" -gt 0 ] && echo "   -> Socket errors:     $SOCK_ERR"
      echo "   -> Total requests:    $TOTAL_REQ (${RPS} req/s | ${DURATION_S}s)"
      {
         echo "========================================================"
         echo "  Concurrency $c — $ERRORS errors — $(date)"
         echo "========================================================"
         [ "$NON_2XX" -gt 0 ] && echo "  Non-2xx: $NON_2XX"
         if [ "$SOCK_ERR" -gt 0 ]; then
            CONNECT_ERR=$(echo "$WRK_OUTPUT" | grep "^WRK_CONNECT_ERR:" | cut -d: -f2)
            READ_ERR=$(echo "$WRK_OUTPUT" | grep "^WRK_READ_ERR:" | cut -d: -f2)
            WRITE_ERR=$(echo "$WRK_OUTPUT" | grep "^WRK_WRITE_ERR:" | cut -d: -f2)
            TIMEOUT_ERR=$(echo "$WRK_OUTPUT" | grep "^WRK_TIMEOUT_ERR:" | cut -d: -f2)
            echo "  Socket errors: Connect=${CONNECT_ERR:-0} Read=${READ_ERR:-0} Write=${WRITE_ERR:-0} Timeout=${TIMEOUT_ERR:-0}"
         fi
         echo "--- Non-2xx response bodies ---"
         cat "$ERR_BODY_FILE"
         echo "--- End of response bodies ---"
         echo
      } >> "$ERROR_LOG"
      FAILED=$((FAILED + 1))
   fi

   rm -f "$ERR_BODY_FILE"
   sleep 2
done

# --- Summary ---
echo
echo "========================================================"
echo "  SUMMARY: $PASSED/$TOTAL passed, $FAILED failed"
echo "========================================================"

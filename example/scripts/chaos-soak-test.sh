#!/usr/bin/env bash
#-*- coding: utf-8-unix -*-

# Chaos and Soak Test Harness for Casual Quarkus Reverse Outbound Topology
# Runs continuous transactional load against front-app while randomly terminating
# and restarting backend nodes and database applications, then verifies zero in-doubt transactions.

set -e

# Default settings
DURATION=${1:-"2h"}
CONCURRENCY=${2:-"50"}
CHAOS_INTERVAL=${3:-"60"}
GRACE_PERIOD=${4:-30} # Kubernetes default grace period in seconds
CHAOS_MODE=${5:-"random-node"} # random-node or all

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LUA_SCRIPT="$SCRIPT_DIR/soak-post.lua"
DATA_FILE="$BASE_DIR/curl-data"
RUN_ID="chaos-$(date +%Y%m%d-%H%M%S)"
LOG_DIR="$BASE_DIR/logs/$RUN_ID"

mkdir -p "$LOG_DIR"

echo "========================================================"
echo "  CASUAL QUARKUS CHAOS SOAK TEST"
echo "========================================================"
echo "Run ID:         $RUN_ID"
echo "Base Directory: $BASE_DIR"
echo "Log Directory:  $LOG_DIR"
echo "Duration:       $DURATION"
echo "Concurrency:    $CONCURRENCY"
echo "Chaos Interval: ${CHAOS_INTERVAL}s"
echo "Grace Period:   ${GRACE_PERIOD}s (k8s default)"
echo "Chaos Mode:     $CHAOS_MODE"
echo "========================================================"

# Validate requirements
if ! command -v wrk &> /dev/null; then
    echo "Error: 'wrk' is required but not installed."
    exit 1
fi

if [ ! -f "$DATA_FILE" ]; then
    echo "Creating payload file $DATA_FILE..."
    echo -n "Bazinga!" > "$DATA_FILE"
fi

# Clean up any leftover ObjectStore from previous runs
rm -rf "$BASE_DIR/ObjectStore"

# PIDs and counters
PID_DB=""
PID_NODE1=""
PID_NODE2=""
PID_FRONT=""
PID_WRK=""
HARD_KILL_COUNT=0

cleanup() {
    echo
    echo "--- Stopping all running processes ---"
    [ -n "$PID_WRK" ] && kill -9 "$PID_WRK" 2>/dev/null || true
    [ -n "$PID_FRONT" ] && kill -15 "$PID_FRONT" 2>/dev/null || true
    [ -n "$PID_NODE1" ] && kill -15 "$PID_NODE1" 2>/dev/null || true
    [ -n "$PID_NODE2" ] && kill -15 "$PID_NODE2" 2>/dev/null || true
    [ -n "$PID_DB" ] && kill -15 "$PID_DB" 2>/dev/null || true
    sleep 2
    [ -n "$PID_FRONT" ] && kill -9 "$PID_FRONT" 2>/dev/null || true
    [ -n "$PID_NODE1" ] && kill -9 "$PID_NODE1" 2>/dev/null || true
    [ -n "$PID_NODE2" ] && kill -9 "$PID_NODE2" 2>/dev/null || true
    [ -n "$PID_DB" ] && kill -9 "$PID_DB" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

JAVA_OPTS="${JAVA_OPTS:-}"

start_node1() {
    echo "[$(date +%T)] Starting Node 1 (reverse outbound port 7785, inbound 7771)..."
    QUARKUS_PROFILE=reverse \
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-node-one-reverse.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java $JAVA_OPTS -jar "$BASE_DIR/node-app/build/node-app-1.0.0-runner.jar" >> "$LOG_DIR/node1.log" 2>&1 &
    PID_NODE1=$!
}

start_node2() {
    echo "[$(date +%T)] Starting Node 2 (reverse outbound port 7786, inbound 7772)..."
    QUARKUS_PROFILE=reverse,two \
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-node-two-reverse.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java $JAVA_OPTS -jar "$BASE_DIR/node-app/build/node-app-1.0.0-runner.jar" >> "$LOG_DIR/node2.log" 2>&1 &
    PID_NODE2=$!
}

start_db() {
    echo "[$(date +%T)] Starting Database App (reverse inbound to 7785 & 7786)..."
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-db-reverse.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java $JAVA_OPTS -jar "$BASE_DIR/db-app/build/db-app-1.0.0-runner.jar" >> "$LOG_DIR/db.log" 2>&1 &
    PID_DB=$!
}

start_front() {
    echo "[$(date +%T)] Starting Front App (HTTP 8080)..."
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-front.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java $JAVA_OPTS -jar "$BASE_DIR/front-app/build/front-app-1.0.0-runner.jar" >> "$LOG_DIR/front.log" 2>&1 &
    PID_FRONT=$!
}

kill_app() {
    local target_name=$1
    local target_pid=$2
    local grace_seconds=${3:-$GRACE_PERIOD}

    if [ -n "$target_pid" ] && kill -0 "$target_pid" 2>/dev/null; then
        echo "[$(date +%T)] [CHAOS] Sending SIGTERM to $target_name (PID: $target_pid), waiting up to ${grace_seconds}s for graceful shutdown..."
        kill -15 "$target_pid" 2>/dev/null || true

        local loops=$(( grace_seconds * 2 ))
        local exited=false
        for (( i=1; i<=loops; i++ )); do
            if ! kill -0 "$target_pid" 2>/dev/null; then
                exited=true
                break
            fi
            sleep 0.5
        done

        if [ "$exited" = true ]; then
            echo "[$(date +%T)] [CHAOS] $target_name (PID: $target_pid) exited gracefully."
        else
            echo "[$(date +%T)] [WARNING] $target_name (PID: $target_pid) did not exit within ${grace_seconds}s grace period! Issuing SIGKILL..."
            HARD_KILL_COUNT=$((HARD_KILL_COUNT + 1))
            kill -9 "$target_pid" 2>/dev/null || true
        fi
        wait "$target_pid" 2>/dev/null || true
    fi
}

# 1. Start all components
echo
echo "--- Starting Reverse Outbound Topology ---"
start_node1
start_node2
sleep 2
start_db
sleep 2
start_front

echo
echo "--- Waiting for system to become healthy ---"
MAX_ATTEMPTS=40
ATTEMPT=0
HEALTHY=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        -H 'Content-Type: application/casual-x-octet' \
        --data-binary @"$DATA_FILE" \
        "http://localhost:8080/casualcallersync/counter" 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        HEALTHY=true
        echo "Endpoint healthy after $ATTEMPT attempts (HTTP $HTTP_CODE)."
        break
    fi
    sleep 1
done

if [ "$HEALTHY" = false ]; then
    echo "Error: Endpoint failed to become healthy within $MAX_ATTEMPTS seconds. Logs:"
    tail -n 20 "$LOG_DIR/front.log" || true
    exit 1
fi

# 2. Launch wrk load generator
echo
echo "--- Starting Load Generator (wrk: $CONCURRENCY connections for $DURATION) ---"
WRK_OUT="$LOG_DIR/wrk.log"
WRK_ERR="$LOG_DIR/wrk_errors.log"
THREADS=$(( CONCURRENCY < 4 ? CONCURRENCY : 4 ))

WRK_BODY_FILE="$DATA_FILE" \
WRK_ERROR_FILE="$WRK_ERR" \
wrk -t"$THREADS" -c"$CONCURRENCY" -d"$DURATION" --timeout 10s \
    -s "$LUA_SCRIPT" \
    "http://localhost:8080/casualcallersync/counter" > "$WRK_OUT" 2>&1 &
PID_WRK=$!

# 3. Chaos Loop while wrk is running
echo
echo "--- Initiating Chaos Events every ${CHAOS_INTERVAL}s (Mode: $CHAOS_MODE) ---"
STEP=0

while kill -0 "$PID_WRK" 2>/dev/null; do
    sleep "$CHAOS_INTERVAL"
    if ! kill -0 "$PID_WRK" 2>/dev/null; then
        break
    fi

    STEP=$((STEP + 1))

    if [ "$CHAOS_MODE" = "random-node" ]; then
        NODE_CHOICE=$(( (RANDOM % 2) + 1 ))
        if [ "$NODE_CHOICE" -eq 1 ]; then
            echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 1 ==="
            kill_app "Node 1" "$PID_NODE1" "$GRACE_PERIOD"
            sleep 5
            start_node1
        else
            echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 2 ==="
            kill_app "Node 2" "$PID_NODE2" "$GRACE_PERIOD"
            sleep 5
            start_node2
        fi
    else
        ACTION=$((STEP % 4))
        case $ACTION in
            1)
                echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 1 ==="
                kill_app "Node 1" "$PID_NODE1" "$GRACE_PERIOD"
                sleep 5
                start_node1
                ;;
            2)
                echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 2 ==="
                kill_app "Node 2" "$PID_NODE2" "$GRACE_PERIOD"
                sleep 5
                start_node2
                ;;
            3)
                echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Database App ==="
                kill_app "Database App" "$PID_DB" "$GRACE_PERIOD"
                sleep 5
                start_db
                ;;
            0)
                echo "[$(date +%T)] === Chaos Event #$STEP: Rolling restart of both Nodes ==="
                kill_app "Node 1" "$PID_NODE1" "$GRACE_PERIOD"
                sleep 4
                start_node1
                sleep 4
                kill_app "Node 2" "$PID_NODE2" "$GRACE_PERIOD"
                sleep 4
                start_node2
                ;;
        esac
    fi
done

# Wait for wrk to fully wrap up
wait "$PID_WRK" 2>/dev/null || true
PID_WRK=""

echo
echo "--- Load test completed. Allowing 5 seconds for in-flight requests and recovery ---"
sleep 5

# 4. Graceful orderly shutdown
echo
echo "--- Performing Orderly Shutdown of Topology ---"
kill_app "Front App" "$PID_FRONT" "$GRACE_PERIOD"
PID_FRONT=""
sleep 2

kill_app "Node 1" "$PID_NODE1" "$GRACE_PERIOD"
PID_NODE1=""
kill_app "Node 2" "$PID_NODE2" "$GRACE_PERIOD"
PID_NODE2=""
sleep 2

kill_app "Database App" "$PID_DB" "$GRACE_PERIOD"
PID_DB=""
sleep 2

# 5. Verification & Report
echo
echo "========================================================"
echo "  TEST RESULTS & TRANSACTION INTEGRITY VERIFICATION"
echo "========================================================"

IN_DOUBT_FILES=$(find "$BASE_DIR/ObjectStore" -type f 2>/dev/null || true)
IN_DOUBT_COUNT=$(echo "$IN_DOUBT_FILES" | grep -v '^$' | wc -l || true)

if [ "$IN_DOUBT_COUNT" -eq 0 ]; then
    echo -e "\033[0;32m[PASS]\033[0m Zero in-doubt transactions detected in ObjectStore!"
else
    echo -e "\033[0;31m[FAIL]\033[0m Found $IN_DOUBT_COUNT in-doubt transactions in ObjectStore:"
    echo "$IN_DOUBT_FILES"
fi

echo
echo "--- Graceful Shutdown Verification ---"
if [ "$HARD_KILL_COUNT" -eq 0 ]; then
    echo -e "\033[0;32m[PASS]\033[0m All applications exited gracefully within the ${GRACE_PERIOD}s grace period (0 hard kills)."
else
    echo -e "\033[0;31m[FAIL]\033[0m $HARD_KILL_COUNT hard kills (SIGKILL) were issued after exceeding the ${GRACE_PERIOD}s grace period!"
fi

echo
echo "--- Netty Resource Leak Verification ---"
LEAK_COUNT=$(grep -rn "LEAK:" "$LOG_DIR"/*.log 2>/dev/null | wc -l || true)
if [ "$LEAK_COUNT" -eq 0 ]; then
    echo -e "\033[0;32m[PASS]\033[0m Zero Netty buffer leaks detected in application logs!"
else
    echo -e "\033[0;31m[FAIL]\033[0m Found $LEAK_COUNT Netty resource leak warnings in logs:"
    grep -rn "LEAK:" "$LOG_DIR"/*.log || true
fi

echo
echo "--- wrk Load Summary ---"
cat "$WRK_OUT"

if [ -s "$WRK_ERR" ]; then
    NON_2XX_COUNT=$(wc -l < "$WRK_ERR")
    echo
    echo "Recorded $NON_2XX_COUNT non-2xx responses during failure windows (see $WRK_ERR)."
    echo "Sample errors:"
    head -n 5 "$WRK_ERR"
fi

echo
echo "Logs preserved at: $LOG_DIR"
echo "========================================================"

if [ "$IN_DOUBT_COUNT" -ne 0 ] || [ "$HARD_KILL_COUNT" -ne 0 ] || [ "$LEAK_COUNT" -ne 0 ]; then
    exit 1
fi

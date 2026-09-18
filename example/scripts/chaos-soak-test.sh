#!/usr/bin/env bash
#-*- coding: utf-8-unix -*-

# Chaos and Soak Test Harness for Casual Quarkus
# Runs continuous transactional load against front-app while randomly terminating
# and restarting backend nodes and database applications, then verifies zero in-doubt transactions.

set -euo pipefail

# Default settings
DURATION=${1:-"2h"}
CONCURRENCY=${2:-"50"}
CHAOS_INTERVAL=${3:-"60"}
GRACE_PERIOD=${4:-30} # Kubernetes default grace period in seconds
CHAOS_MODE=${5:-"random-node"} # random-node or all
RESTART_PAUSE=${RESTART_PAUSE:-5}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LUA_SCRIPT="$SCRIPT_DIR/soak-post.lua"
DATA_FILE="$BASE_DIR/curl-data"
RUN_ID="chaos-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BASE_DIR/logs"
LOG_DIR=$(mktemp -d "$BASE_DIR/logs/$RUN_ID-XXXXXX")
STORE_ROOT="$LOG_DIR/ObjectStore"
cd "$BASE_DIR"

fail() {
    echo "Error: $*" >&2
    exit 1
}

for value in "$CONCURRENCY" "$CHAOS_INTERVAL" "$GRACE_PERIOD" "$RESTART_PAUSE"; do
    [[ "$value" =~ ^[1-9][0-9]*$ ]] || fail "Concurrency, chaos interval, grace period and restart pause must be positive integers."
done
case "$CHAOS_MODE" in
    random-node|all) ;;
    *) fail "Unknown chaos mode: $CHAOS_MODE (expected random-node or all)." ;;
esac
for executable in wrk curl java find; do
    command -v "$executable" >/dev/null || fail "Required executable not found: $executable"
done
for app in front-app node-app db-app; do
    [[ -r "$BASE_DIR/$app/build/$app-1.0.0-runner.jar" ]] || fail "Build $app before running this script."
done
[[ -r "$LUA_SCRIPT" ]] || fail "Missing load script: $LUA_SCRIPT"
for app in front node1 node2 db; do
    mkdir -p "$STORE_ROOT/$app"
done

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
echo "Restart pause:  ${RESTART_PAUSE}s"
echo "Object stores:  $STORE_ROOT"
echo "========================================================"

if [ ! -f "$DATA_FILE" ]; then
    echo "Creating payload file $DATA_FILE..."
    echo -n "Bazinga!" > "$DATA_FILE"
fi

# Keep each run's stores for inspection and reuse them for every restart in that run.

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
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

JAVA_OPTS="${JAVA_OPTS:-}"
SHUTDOWN_LOG_OPTS=(
    '-Dquarkus.log.category."se.laz.casual.network.outbound.DomainDisconnectHandler".level=INFO'
    '-Dquarkus.log.category."se.laz.casual.network.outbound.NettyNetworkConnection".level=INFO'
    '-Dquarkus.log.category."se.laz.casual.quarkus.CasualShutdownDelayHandler".level=INFO'
)

start_node1() {
    echo "[$(date +%T)] Starting Node 1 (reverse outbound port 7785, inbound 7771)..."
    QUARKUS_PROFILE=reverse \
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-node-one-reverse.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java "${SHUTDOWN_LOG_OPTS[@]}" $JAVA_OPTS -Dquarkus.transaction-manager.object-store.type=file-system \
    -Dquarkus.transaction-manager.object-store.directory="$STORE_ROOT/node1" \
    -Dquarkus.transaction-manager.node-name="chaos-node1" -jar "$BASE_DIR/node-app/build/node-app-1.0.0-runner.jar" >> "$LOG_DIR/node1.log" 2>&1 &
    PID_NODE1=$!
}

start_node2() {
    echo "[$(date +%T)] Starting Node 2 (reverse outbound port 7786, inbound 7772)..."
    QUARKUS_PROFILE=reverse,two \
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-node-two-reverse.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java "${SHUTDOWN_LOG_OPTS[@]}" $JAVA_OPTS -Dquarkus.transaction-manager.object-store.type=file-system \
    -Dquarkus.transaction-manager.object-store.directory="$STORE_ROOT/node2" \
    -Dquarkus.transaction-manager.node-name="chaos-node2" -jar "$BASE_DIR/node-app/build/node-app-1.0.0-runner.jar" >> "$LOG_DIR/node2.log" 2>&1 &
    PID_NODE2=$!
}

start_db() {
    echo "[$(date +%T)] Starting Database App (reverse inbound to 7785 & 7786)..."
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-db-reverse.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java "${SHUTDOWN_LOG_OPTS[@]}" $JAVA_OPTS -Dquarkus.transaction-manager.object-store.type=file-system \
    -Dquarkus.transaction-manager.object-store.directory="$STORE_ROOT/db" \
    -Dquarkus.transaction-manager.node-name="chaos-db" -jar "$BASE_DIR/db-app/build/db-app-1.0.0-runner.jar" >> "$LOG_DIR/db.log" 2>&1 &
    PID_DB=$!
}

start_front() {
    echo "[$(date +%T)] Starting Front App (HTTP 8080)..."
    CASUAL_CALLER_CONFIG_FILE="$BASE_DIR/config/caller-config.json" \
    CASUAL_CONFIG_FILE="$BASE_DIR/config/casual-config-front.json" \
    CASUAL_FIELD_TABLE="$BASE_DIR/casual-fields.json" \
    java "${SHUTDOWN_LOG_OPTS[@]}" $JAVA_OPTS -Dquarkus.transaction-manager.object-store.type=file-system \
    -Dquarkus.transaction-manager.object-store.directory="$STORE_ROOT/front" \
    -Dquarkus.transaction-manager.node-name="chaos-front" -jar "$BASE_DIR/front-app/build/front-app-1.0.0-runner.jar" >> "$LOG_DIR/front.log" 2>&1 &
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
        local i
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

assert_running() {
    local pid
    for pid in "$PID_FRONT" "$PID_NODE1" "$PID_NODE2" "$PID_DB"; do
        kill -0 "$pid" 2>/dev/null || fail "Application PID $pid exited unexpectedly; inspect $LOG_DIR."
    done
}

wait_for_endpoint() {
    local deadline=$((SECONDS + 120))
    local http_code port ready
    while (( SECONDS < deadline )); do
        assert_running
        ready=true
        for port in 8081 8082 8083; do
            if ! http_code=$(curl --silent --connect-timeout 2 --max-time 5 \
                    --output /dev/null --write-out '%{http_code}' "http://localhost:$port/"); then
                ready=false
                break
            fi
            [[ "$http_code" =~ ^[234][0-9][0-9]$ ]] || { ready=false; break; }
        done
        if [[ "$ready" == false ]]; then
            sleep 1
            continue
        fi
        if http_code=$(curl --silent --connect-timeout 2 --max-time 5 \
                --output /dev/null --write-out '%{http_code}' --request POST \
                --header 'Content-Type: application/casual-x-octet' \
                --data-binary @"$DATA_FILE" \
                'http://localhost:8080/casualcallersync/counter'); then
            if [[ "$http_code" == 200 ]]; then
                echo "[$(date +%T)] Transactional endpoint responds successfully."
                return
            fi
        fi
        sleep 1
    done
    fail "Transactional endpoint did not recover within the readiness window; inspect $LOG_DIR."
}

wait_for_endpoint

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

    assert_running
    STEP=$((STEP + 1))

    if [ "$CHAOS_MODE" = "random-node" ]; then
        NODE_CHOICE=$(( (RANDOM % 2) + 1 ))
        if [ "$NODE_CHOICE" -eq 1 ]; then
            echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 1 ==="
            kill_app "Node 1" "$PID_NODE1" "$GRACE_PERIOD"
            sleep "$RESTART_PAUSE"
            start_node1
        else
            echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 2 ==="
            kill_app "Node 2" "$PID_NODE2" "$GRACE_PERIOD"
            sleep "$RESTART_PAUSE"
            start_node2
        fi
    else
        ACTION=$((STEP % 4))
        case $ACTION in
            1)
                echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 1 ==="
                kill_app "Node 1" "$PID_NODE1" "$GRACE_PERIOD"
                sleep "$RESTART_PAUSE"
                start_node1
                ;;
            2)
                echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Node 2 ==="
                kill_app "Node 2" "$PID_NODE2" "$GRACE_PERIOD"
                sleep "$RESTART_PAUSE"
                start_node2
                ;;
            3)
                echo "[$(date +%T)] === Chaos Event #$STEP: Terminate & Restart Database App ==="
                kill_app "Database App" "$PID_DB" "$GRACE_PERIOD"
                sleep "$RESTART_PAUSE"
                start_db
                ;;
            0)
                echo "[$(date +%T)] === Chaos Event #$STEP: Rolling restart of both Nodes ==="
                kill_app "Node 1" "$PID_NODE1" "$GRACE_PERIOD"
                sleep 4
                start_node1
                wait_for_endpoint
                kill_app "Node 2" "$PID_NODE2" "$GRACE_PERIOD"
                sleep 4
                start_node2
                ;;
        esac
    fi
    wait_for_endpoint
done

# Wait for wrk to fully wrap up
WRK_STATUS=0
wait "$PID_WRK" || WRK_STATUS=$?
PID_WRK=""
[[ "$WRK_STATUS" -eq 0 ]] || fail "wrk exited with status $WRK_STATUS; inspect $WRK_OUT."
[[ "$STEP" -gt 0 ]] || fail "No chaos events occurred; increase the duration or reduce the interval."
WRK_SUCCESS=$(sed -n 's/^WRK_SUCCESS://p' "$WRK_OUT")
[[ "$WRK_SUCCESS" =~ ^[0-9]+$ ]] || fail "wrk did not produce a valid success count."
[[ "$WRK_SUCCESS" -gt 0 ]] || fail "No successful responses were recorded under load."
wait_for_endpoint

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

IN_DOUBT_MANIFEST="$LOG_DIR/in-doubt-files.txt"
: > "$IN_DOUBT_MANIFEST"
for app in front node1 node2 db; do
    store="$STORE_ROOT/$app"
    [[ -d "$store" && -r "$store" && -x "$store" ]] || fail "Transaction store is missing or unreadable: $store"
    find "$store" -type f -print >> "$IN_DOUBT_MANIFEST" || fail "Cannot inspect transaction store: $store"
done
IN_DOUBT_COUNT=$(wc -l < "$IN_DOUBT_MANIFEST")
IN_DOUBT_FILES=$(cat "$IN_DOUBT_MANIFEST")

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

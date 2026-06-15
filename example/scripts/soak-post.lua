-- soak-post.lua — wrk script for casual soak testing
-- Reads POST body from WRK_BODY_FILE.
-- Logs non-2xx response bodies to WRK_ERROR_FILE (append, thread-safe).
-- Summary stats come from wrk's built-in aggregation.

local body_file = os.getenv("WRK_BODY_FILE")
local error_file = os.getenv("WRK_ERROR_FILE")

-- Read POST body (runs once when script is loaded)
local f = io.open(body_file, "rb")
if not f then
    io.stderr:write("ERROR: cannot open body file: " .. tostring(body_file) .. "\n")
    os.exit(1)
end
wrk.method = "POST"
wrk.body = f:read("*all")
f:close()
wrk.headers["Content-Type"] = "application/casual-x-octet"

-- Per-thread state
local ef = nil

function init(args)
    if error_file then
        ef = io.open(error_file, "a")
    end
end

function response(status, headers, body)
    if (status < 200 or status >= 300) and ef then
        -- Append-mode writes < PIPE_BUF (4096) are atomic on Linux
        ef:write(string.format("HTTP %d: %s\n", status, body:sub(1, 500)))
        ef:flush()
    end
end

function done(summary, latency, requests)
    local dur_s = summary.duration / 1000000
    local rps = 0
    if dur_s > 0 then
        rps = summary.requests / dur_s
    end
    local connect_err = summary.errors.connect or 0
    local read_err = summary.errors.read or 0
    local write_err = summary.errors.write or 0
    local timeout_err = summary.errors.timeout or 0
    local sock_err = connect_err + read_err + write_err + timeout_err

    -- Machine-readable output for the shell script
    io.stderr:write(string.format("WRK_TOTAL:%d\n", summary.requests))
    io.stderr:write(string.format("WRK_RPS:%.2f\n", rps))
    io.stderr:write(string.format("WRK_DURATION:%.3f\n", dur_s))
    io.stderr:write(string.format("WRK_SOCK_ERR:%d\n", sock_err))
    io.stderr:write(string.format("WRK_CONNECT_ERR:%d\n", connect_err))
    io.stderr:write(string.format("WRK_READ_ERR:%d\n", read_err))
    io.stderr:write(string.format("WRK_WRITE_ERR:%d\n", write_err))
    io.stderr:write(string.format("WRK_TIMEOUT_ERR:%d\n", timeout_err))
end

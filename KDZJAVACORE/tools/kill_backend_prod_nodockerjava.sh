PIDFILE=/tmp/kdzbot-backend-prod.pid
if [ -f "$PIDFILE" ] && [ -s "$PIDFILE" ]; then
    PID=$(sudo cat "$PIDFILE")
    if [ -n "$PID" ]; then
        echo sudo kill $PID
        sudo kill $PID
    fi
fi

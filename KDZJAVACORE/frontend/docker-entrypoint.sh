#!/bin/sh
set -e

# Substitute env vars in nginx config before starting nginx
SERVER_HOST="${SERVER_HOST:-rig1.lan}"
HTTPD_SERVER_PORT="${HTTPD_SERVER_PORT:-8443}"

sed -i "s|BackendHostPlaceholder|${SERVER_HOST}:${HTTPD_SERVER_PORT}|g" /etc/nginx/conf.d/default.conf

nginx -g 'daemon off;'

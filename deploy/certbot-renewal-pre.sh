#!/bin/sh
set -eu

state_file=/run/certbot-sonograma-nginx-was-running
rm -f "$state_file"

if /usr/bin/docker inspect --format '{{.State.Running}}' sonograma-nginx 2>/dev/null | grep -qx true; then
    touch "$state_file"
    /usr/bin/docker stop -t 30 sonograma-nginx
fi

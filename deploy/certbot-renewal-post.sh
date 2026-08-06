#!/bin/sh
set -eu

state_file=/run/certbot-sonograma-nginx-was-running

if [ -f "$state_file" ]; then
    /usr/bin/docker start sonograma-nginx
    rm -f "$state_file"
fi

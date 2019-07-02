#!/usr/bin/env bash

set -e

export TAG=3.2
bash ./build.sh
docker-compose build --build-arg TAG=$TAG
docker-compose push

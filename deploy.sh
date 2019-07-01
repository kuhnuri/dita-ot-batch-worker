#!/usr/bin/env bash

bash ./build.sh
docker-compose build
docker-compose push

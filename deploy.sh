#!/usr/bin/env bash

export TAG=3.4
docker-compose build --build-arg TAG=$TAG
docker-compose push

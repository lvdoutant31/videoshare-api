#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$(realpath "$0")")"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env. Set the MySQL and Redis connection values, then run this script again."
  exit 2
fi

docker load --input docker-images/videoshare-images.tar
docker compose -f compose.yml up -d --wait --pull never
docker compose -f compose.yml ps

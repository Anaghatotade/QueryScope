#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> QueryLens startup"
echo ""

docker_ready() {
  docker info >/dev/null 2>&1
}

if ! docker_ready; then
  echo "Docker is not running."

  if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "Starting Docker Desktop..."
    open -a Docker 2>/dev/null || true

    echo "Waiting for Docker (up to 120s)..."
    for i in $(seq 1 60); do
      if docker_ready; then
        echo "Docker is ready."
        break
      fi
      sleep 2
      if [[ $i -eq 60 ]]; then
        echo ""
        echo "ERROR: Docker did not start in time."
        echo "Please open Docker Desktop manually, wait until it says 'Running', then run:"
        echo "  ./scripts/start.sh"
        exit 1
      fi
    done
  else
    echo "ERROR: Start Docker manually, then run ./scripts/start.sh"
    exit 1
  fi
fi

echo ""
echo "Building and starting services (first run may take 3-5 minutes)..."
docker compose up --build -d

echo ""
echo "Waiting for backend health..."
for i in $(seq 1 90); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "Backend is healthy."
    break
  fi
  sleep 2
  if [[ $i -eq 90 ]]; then
    echo ""
    echo "Backend did not become healthy. Logs:"
    docker compose logs backend --tail 80
    exit 1
  fi
done

echo ""
echo "QueryLens is running!"
echo "  Dashboard : http://localhost:3000"
echo "  API       : http://localhost:8080"
echo ""
echo "View logs: docker compose logs -f"
echo "Stop:      docker compose down"

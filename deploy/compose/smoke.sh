#!/usr/bin/env bash
# Walking-skeleton smoke test (doc §11): boots the `ci` stack from the single image and runs Playwright
# against it. Uses its own compose project (erp-smoke), so it never touches the dev stack or its data.
#   deploy/compose/smoke.sh                                  build erp-app:local, then test
#   ERP_APP_IMAGE=<ref> SKIP_BUILD=1 deploy/compose/smoke.sh test an already built image (CI)
set -euo pipefail
cd "$(dirname "$0")"

./init-env.sh

LOG_DIR="$PWD/smoke-logs"
rm -rf "$LOG_DIR"
mkdir -p "$LOG_DIR"

COMPOSE=(docker compose --project-name erp-smoke -f compose.yaml -f compose.smoke.yaml --profile ci --profile e2e)
E2E_CONTAINER=erp-smoke-e2e-run

cleanup() {
  local status=$?
  "${COMPOSE[@]}" logs --no-color >"$LOG_DIR/compose.log" 2>&1 || true
  for dir in playwright-report reports test-results; do
    docker cp "$E2E_CONTAINER:/work/web/e2e/$dir" "$LOG_DIR/$dir" >/dev/null 2>&1 || true
  done
  docker rm -f "$E2E_CONTAINER" >/dev/null 2>&1 || true
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  if [[ $status -ne 0 ]]; then
    echo "Smoke test FAILED (exit $status). Logs: $LOG_DIR" >&2
  fi
  exit $status
}
trap cleanup EXIT

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  "${COMPOSE[@]}" build app
fi
"${COMPOSE[@]}" up -d --no-build postgres app
"${COMPOSE[@]}" run --build --name "$E2E_CONTAINER" e2e
echo "Smoke test passed."

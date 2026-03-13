#!/usr/bin/env bash
# test_backend_et_frontend_all.sh
# Master script that runs all backend and frontend tests
# Runs frontend tests only after backend tests complete and are successful

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."

echo "======================================================================"
echo "Running ALL Tests for AI Content Generator System"
echo "======================================================================"
echo ""

# Test backend first (blocking)
echo "[1/2] Starting Backend Tests..."
cd "${SCRIPT_DIR}"
if bash test_backend_spawn_all_necessary_subshells.sh; then
  BACKEND_EXIT=0
  echo ""
  echo "Backend Tests: PASSED"
else
  BACKEND_EXIT=$?
  echo ""
  echo "Backend Tests: FAILED (exit code: $BACKEND_EXIT)"
fi

# Only run frontend tests if backend tests passed
if [ $BACKEND_EXIT -eq 0 ]; then
  echo ""
  echo "[2/2] Starting Frontend Tests..."
  cd "${SCRIPT_DIR}"
  if bash test_frontend_spawn_all_necessary_subshells.sh; then
    FRONTEND_EXIT=0
    echo ""
    echo "Frontend Tests: PASSED"
  else
    FRONTEND_EXIT=$?
    echo ""
    echo "Frontend Tests: FAILED (exit code: $FRONTEND_EXIT)"
  fi
else
  echo ""
  echo "[2/2] SKIPPED: Frontend tests (backend tests failed)"
  FRONTEND_EXIT=1
fi

echo ""
echo "======================================================================"

if [ $BACKEND_EXIT -eq 0 ] && [ $FRONTEND_EXIT -eq 0 ]; then
  echo "ALL TESTS PASSED"
  echo "======================================================================"
  exit 0
else
  echo "SOME TESTS FAILED"
  echo "Backend exit code: $BACKEND_EXIT"
  echo "Frontend exit code: $FRONTEND_EXIT"
  echo "======================================================================"
  exit 1
fi

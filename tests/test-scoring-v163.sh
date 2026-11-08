#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
kotlinc app/src/main/java/com/nfltotalslab/app/data/Models.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/PlayerUsageDriveEngine.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/Week9Lineups.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/SimcastIntegrity.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/SimcastCalibration.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/SimcastBatchEngine.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/ScoringShadowEngine.kt \
  tests/ScoringV163Smoke.kt -include-runtime -d /tmp/nfl-v163-scoring-smoke.jar
java -jar /tmp/nfl-v163-scoring-smoke.jar

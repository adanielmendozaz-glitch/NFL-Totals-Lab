#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Requires a local JDK 17+ and Kotlin CLI; does not compile Android/Compose.
kotlinc app/src/main/java/com/nfltotalslab/app/data/Models.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/PlayerUsageDriveEngine.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/Week9Lineups.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/SimcastIntegrity.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/SimcastCalibration.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/ScoringShadowEngine.kt \
  app/src/main/java/com/nfltotalslab/app/simcast/SimcastBatchEngine.kt \
  tests/V16IntegritySmoke.kt -include-runtime -d /tmp/nfl-v16-integrity-smoke.jar
java -jar /tmp/nfl-v16-integrity-smoke.jar

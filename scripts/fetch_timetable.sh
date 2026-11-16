#!/usr/bin/env bash
# Telecharge la base d'horaires publiee vers app/src/main/assets/timetable.db
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/src/main/assets
curl -fL --retry 3 -o app/src/main/assets/timetable.db \
  https://github.com/matthieurosset/fribus-info/releases/download/timetable/timetable.db
ls -l app/src/main/assets/timetable.db

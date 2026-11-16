"""Build app/src/androidTest/assets/test.db from the fixture GTFS (committed, regenerate on fixture change).

    python -I pipeline/make_test_db.py
"""
import json
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import build_timetable as bt  # noqa: E402
from fixture_gtfs import write_fixture_gtfs  # noqa: E402

OUT = os.path.join(HERE, "..", "app", "src", "androidTest", "assets", "test.db")
BUILD_DATE = "20260101"  # fixed: nothing in the fixture is pruned


def main():
    with open(os.path.join(HERE, "lines.json"), encoding="utf-8") as f:
        cfg = json.load(f)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        gtfs = os.path.join(tmp, "fixture.zip")
        write_fixture_gtfs(gtfs, feed=1)
        info = bt.build([gtfs], cfg, OUT, BUILD_DATE)
    print(info)


if __name__ == "__main__":
    main()

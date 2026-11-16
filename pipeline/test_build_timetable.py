# Run: python -I -m unittest discover -s pipeline   (the path form does not work under -I)
import contextlib
import hashlib
import io
import json
import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import build_timetable as bt  # noqa: E402
import fixture_gtfs  # noqa: E402

LINES_JSON = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lines.json")


class BuildTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.a = os.path.join(self.tmp.name, "a.zip")
        self.b = os.path.join(self.tmp.name, "b.zip")
        fixture_gtfs.write_fixture_gtfs(self.a, feed=1)
        fixture_gtfs.write_fixture_gtfs(self.b, feed=2)
        with open(LINES_JSON, encoding="utf-8") as f:
            self.cfg = json.load(f)
        self.out = os.path.join(self.tmp.name, "t.db")
        self.info = bt.build([self.a, self.b], self.cfg, self.out, build_date="20260401")
        self.db = sqlite3.connect(self.out)
        self.addCleanup(self.db.close)

    def q(self, sql, *args):
        return self.db.execute(sql, args).fetchall()

    def test_keeps_only_whitelisted_tpf_lines(self):
        labels = [r[0] for r in self.q("SELECT label FROM line ORDER BY sort")]
        self.assertEqual(labels, ["1", "2", "6", "FUN"])
        # route 2 of agency 11 and route 124 are gone
        self.assertEqual(self.q("SELECT count(*) FROM trip WHERE headsign IN ('Bern','Posieux')"), [(0,)])

    def test_agency_791_urban_line_kept(self):
        # the 2027 feed publishes urban lines under agency 791 instead of 834
        c = os.path.join(self.tmp.name, "c.zip")
        fixture_gtfs.write_fixture_gtfs(c, feed=1, agency791=True)
        out = os.path.join(self.tmp.name, "c.db")
        bt.build([c], self.cfg, out, build_date="20260401")
        db = sqlite3.connect(out)
        self.addCleanup(db.close)
        self.assertEqual(db.execute("SELECT count(*) FROM trip t JOIN line l ON l.id=t.line_id "
                                    "WHERE l.label='3'").fetchone(), (1,))

    def test_funicular_label_and_border(self):
        self.assertEqual(self.q("SELECT bordered, bg, fg FROM line WHERE label='FUN'"),
                         [(1, 0xFFFFFF, 0x000000)])
        self.assertEqual(self.q("SELECT bordered, bg, fg FROM line WHERE label='2'"),
                         [(0, 0xFF2440, 0xFFFFFF)])

    def test_platforms_collapse_to_parent_didok(self):
        self.assertEqual(self.q("SELECT id, name, commune FROM stop WHERE id=8587255"),
                         [(8587255, "Tilleul/Cathédrale", "Fribourg")])

    def test_distinct_didok_same_place_stay_distinct(self):
        self.assertEqual(self.q("SELECT id, name, commune FROM stop WHERE id IN (8592385, 8530013) ORDER BY id"),
                         [(8530013, "Fribourg-Neuveville/Motta", "Fribourg-Neuveville/Motta"),
                          (8592385, "Neuveville/Motta", "Fribourg")])

    def test_name_split_rule(self):
        self.assertEqual(bt.split_name("Villars-sur-Glâne,Petit-Moncor"),
                         ("Petit-Moncor", "Villars-sur-Glâne"))
        self.assertEqual(bt.split_name("Fribourg, Tilleul/Cathédrale"),
                         ("Tilleul/Cathédrale", "Fribourg"))
        self.assertEqual(bt.split_name("A, B, C"), ("B, C", "A"))
        self.assertEqual(bt.split_name("Fribourg-Gare"), ("Fribourg-Gare", "Fribourg-Gare"))

    def test_headsign_split(self):
        self.assertEqual(self.q("SELECT headsign, headsign_commune FROM trip WHERE headsign_commune='Villars-sur-Glâne'"),
                         [("Petit-Moncor", "Villars-sur-Glâne")] * 2)
        self.assertEqual(self.q("SELECT count(*) FROM trip WHERE headsign='Marly Gare' AND headsign_commune='Fribourg'"),
                         [(1,)])

    def test_times_after_midnight_kept_as_seconds(self):
        self.assertEqual(self.q("SELECT dep FROM stop_time WHERE stop_id=8504100 AND dep=89100"),
                         [(89100,)])
        self.assertEqual(self.q("SELECT count(*) FROM stop_time WHERE dep=24*3600+55*60"), [(1,)])

    def test_two_feeds_do_not_mix_services(self):
        rows = self.q("""SELECT t.headsign, s.days, s.start, s."end" FROM trip t
                         JOIN service s ON s.id=t.service_id
                         WHERE t.headsign IN ('Marly Gare','Marly Centre') ORDER BY t.headsign""")
        self.assertEqual(rows, [("Marly Centre", 0b1100000, 20261213, 20271211),
                                ("Marly Gare", 0b0011111, 20251214, 20261212)])
        # same string id TA in both feeds, still two services
        self.assertEqual(self.q("SELECT count(DISTINCT service_id) FROM trip"), [(2,)])

    def test_service_exceptions_only_for_used_services(self):
        self.assertEqual(self.q("SELECT count(*) FROM service"), [(2,)])
        exc = sorted(self.q("SELECT date, type FROM service_exception"))
        self.assertEqual(exc, [(20261224, 2), (20271225, 1)])

    def test_stop_line_lists_lines_per_stop(self):
        rows = self.q("""SELECT l.label FROM stop_line sl JOIN line l ON l.id=sl.line_id
                         WHERE sl.stop_id=8587255 ORDER BY l.sort""")
        self.assertEqual(rows, [("1",), ("2",), ("6",)])

    def test_only_stops_served_by_kept_lines(self):
        ids = [r[0] for r in self.q("SELECT id FROM stop ORDER BY id")]
        self.assertEqual(ids, [8504100, 8530013, 8587255, 8592385])

    def test_meta_valid_range(self):
        meta = dict(self.q("SELECT key, value FROM meta"))
        self.assertEqual(meta["valid_from"], "20251214")
        self.assertEqual(meta["valid_to"], "20271211")
        self.assertRegex(meta["version"], r"^\d{12}$")
        self.assertEqual(self.info["valid_from"], "20251214")
        self.assertEqual(self.info["valid_to"], "20271211")
        self.assertEqual(self.info["version"], meta["version"])
        self.assertEqual(self.info["stops"], 4)
        self.assertEqual(self.info["trips"], 8)
        self.assertEqual(self.info["bytes"], os.path.getsize(self.out))

    def test_single_feed(self):
        out = os.path.join(self.tmp.name, "one.db")
        info = bt.build([self.a], self.cfg, out, build_date="20260401")
        self.assertEqual(info["valid_to"], "20261212")
        self.assertEqual(info["trips"], 4)

    def test_cli_writes_db_and_version_json(self):
        out = os.path.join(self.tmp.name, "cli.db")
        ver = os.path.join(self.tmp.name, "version.json")
        with contextlib.redirect_stdout(io.StringIO()):
            bt.main(["--gtfs", self.a, "--gtfs", self.b, "--lines", LINES_JSON,
                     "--out", out, "--version-out", ver, "--build-date", "20260401"])
        with open(ver, encoding="utf-8") as f:
            v = json.load(f)
        self.assertEqual(sorted(v), ["bytes", "sha256", "stops", "trips", "valid_from", "valid_to", "version"])
        with open(out, "rb") as f:
            self.assertEqual(v["sha256"], hashlib.sha256(f.read()).hexdigest())


class BuildDateFilterTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.a = os.path.join(self.tmp.name, "a.zip")
        self.b = os.path.join(self.tmp.name, "b.zip")
        fixture_gtfs.write_fixture_gtfs(self.a, feed=1, extras=True)
        fixture_gtfs.write_fixture_gtfs(self.b, feed=2)
        with open(LINES_JSON, encoding="utf-8") as f:
            self.cfg = json.load(f)

    def run_build(self, build_date):
        out = os.path.join(self.tmp.name, "t%s.db" % build_date)
        bt.build([self.a, self.b], self.cfg, out, build_date=build_date)
        db = sqlite3.connect(out)
        self.addCleanup(db.close)
        return db

    def headsigns(self, db):
        return {r[0] for r in db.execute("SELECT headsign FROM trip")}

    def test_expired_service_dropped_with_its_stop(self):
        db = self.run_build("20260401")
        self.assertNotIn("Ancien", self.headsigns(db))
        self.assertEqual(db.execute("SELECT count(*) FROM stop WHERE id=8509999").fetchone(), (0,))
        self.assertEqual(db.execute("SELECT count(*) FROM stop_time WHERE stop_id=8509999").fetchone(), (0,))
        # its service is gone as well
        self.assertEqual(db.execute("SELECT count(*) FROM service WHERE \"end\"=20260301").fetchone(), (0,))

    def test_only_added_exception_after_build_date_is_kept(self):
        db = self.run_build("20260401")
        self.assertIn("Ajout", self.headsigns(db))
        self.assertEqual(db.execute("SELECT count(*) FROM service_exception WHERE date=20260901 AND type=1").fetchone(), (1,))

    def test_service_active_day_before_build_date_is_kept(self):
        # TEDGE ends 2026-03-31: still needed for night buses on 2026-04-01 00:30
        self.assertIn("Marge", self.headsigns(self.run_build("20260401")))
        self.assertNotIn("Marge", self.headsigns(self.run_build("20260402")))

    def test_valid_range_computed_over_remaining_services(self):
        db = self.run_build("20260402")
        meta = dict(db.execute("SELECT key, value FROM meta"))
        self.assertEqual(meta["valid_from"], "20251214")
        self.assertEqual(meta["valid_to"], "20271211")
        db = self.run_build("20261216")  # feed-1 services are over, only feed 2 remains
        meta = dict(db.execute("SELECT key, value FROM meta"))
        self.assertEqual(meta["valid_from"], "20261213")


if __name__ == "__main__":
    unittest.main()

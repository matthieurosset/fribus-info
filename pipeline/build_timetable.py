"""GTFS (Swiss feed) -> compact SQLite timetable for FriBus Info.

Usage:
  python pipeline/build_timetable.py --gtfs A.zip [--gtfs B.zip] \
      --lines pipeline/lines.json --out timetable.db --version-out version.json
"""
import argparse
import csv
import datetime
import hashlib
import io
import json
import os
import sqlite3
import sys
import zipfile

SCHEMA = """
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE stop(id INTEGER PRIMARY KEY,
  name TEXT NOT NULL, commune TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE TABLE line(id INTEGER PRIMARY KEY, label TEXT NOT NULL,
  bg INTEGER NOT NULL, fg INTEGER NOT NULL, bordered INTEGER NOT NULL, sort INTEGER NOT NULL);
CREATE TABLE service(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, "end" INTEGER NOT NULL, days INTEGER NOT NULL);
CREATE TABLE service_exception(service_id INTEGER NOT NULL, date INTEGER NOT NULL, type INTEGER NOT NULL);
CREATE TABLE trip(id INTEGER PRIMARY KEY, line_id INTEGER NOT NULL, service_id INTEGER NOT NULL,
  headsign TEXT NOT NULL, headsign_commune TEXT NOT NULL);
CREATE TABLE stop_time(trip_id INTEGER NOT NULL, seq INTEGER NOT NULL, stop_id INTEGER NOT NULL,
  dep INTEGER NOT NULL);
CREATE TABLE stop_line(stop_id INTEGER NOT NULL, line_id INTEGER NOT NULL, PRIMARY KEY(stop_id, line_id));
"""
INDEXES = """
CREATE INDEX st_stop ON stop_time(stop_id, dep);
CREATE INDEX st_trip ON stop_time(trip_id, seq);
CREATE INDEX se_svc ON service_exception(service_id, date);
"""
DAY_COLUMNS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
BATCH = 50000


def split_name(full):
    """'Fribourg, Tilleul/Cathedrale' -> ('Tilleul/Cathedrale', 'Fribourg').
    Split on the first comma, trim both sides; no comma -> (full, full)."""
    if "," not in full:
        return full, full
    commune, name = full.split(",", 1)
    return name.strip(), commune.strip()


def parse_time(s):
    h, m, sec = s.split(":")
    return int(h) * 3600 + int(m) * 60 + int(sec)


def _rows(zf, name):
    """Stream a GTFS file as dicts (header-keyed)."""
    with zf.open(name) as raw:
        yield from csv.DictReader(io.TextIOWrapper(raw, "utf-8-sig", newline=""))


def _color(s):
    return int(s.lstrip("#"), 16)


def _resolve_stops(zf):
    """stop_id -> (didok, name, lat, lon), platforms collapsed on their parent station."""
    rows = {r["stop_id"]: r for r in _rows(zf, "stops.txt")}
    didok_of = {}
    for sid, r in rows.items():
        if r.get("didok"):
            didok_of.setdefault(r.get("parent_station") or sid, r["didok"])
    out = {}
    for sid, r in rows.items():
        key = r.get("parent_station") or sid
        if key not in didok_of:
            continue
        ref = rows.get(key, r)
        out[sid] = (int(didok_of[key]), ref["stop_name"], float(ref["stop_lat"]), float(ref["stop_lon"]))
    return out


def build(gtfs_paths, lines_cfg, out_path):
    agencies = set(lines_cfg["agencies"])
    line_by_gtfs = {l["gtfs"]: i for i, l in enumerate(lines_cfg["lines"])}

    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.execute("PRAGMA journal_mode=OFF")
    db.execute("PRAGMA synchronous=OFF")
    db.executescript(SCHEMA)

    stops = {}            # didok -> (full name, lat, lon); later feeds win
    used_lines = set()    # config indexes
    stop_lines = set()    # (didok, config index)
    next_trip = next_service = 1
    starts, ends = [], []

    with db:
        for path in gtfs_paths:
            # ids are remapped per feed: service_map / trip_map are local to each feed
            with zipfile.ZipFile(path) as zf:
                stop_map = _resolve_stops(zf)

                route_line = {}
                for r in _rows(zf, "routes.txt"):
                    if r["agency_id"] in agencies and r["route_short_name"] in line_by_gtfs:
                        route_line[r["route_id"]] = line_by_gtfs[r["route_short_name"]]

                trip_map = {}      # gtfs trip_id -> (int id, line index)
                service_map = {}   # gtfs service_id -> int id
                batch = []
                for r in _rows(zf, "trips.txt"):
                    line = route_line.get(r["route_id"])
                    if line is None:
                        continue
                    sid = service_map.get(r["service_id"])
                    if sid is None:
                        sid = service_map[r["service_id"]] = next_service
                        next_service += 1
                    headsign, hs_commune = split_name(r["trip_headsign"])
                    trip_map[r["trip_id"]] = (next_trip, line)
                    batch.append((next_trip, line + 1, sid, headsign, hs_commune))
                    next_trip += 1
                db.executemany("INSERT INTO trip VALUES(?,?,?,?,?)", batch)

                seen_cal = set()
                for r in _rows(zf, "calendar.txt"):
                    sid = service_map.get(r["service_id"])
                    if sid is None:
                        continue
                    seen_cal.add(r["service_id"])
                    days = sum(1 << i for i, c in enumerate(DAY_COLUMNS) if r[c] == "1")
                    start, end = int(r["start_date"]), int(r["end_date"])
                    db.execute("INSERT INTO service VALUES(?,?,?,?)", (sid, start, end, days))
                    starts.append(start)
                    ends.append(end)

                exc = {}
                for r in _rows(zf, "calendar_dates.txt"):
                    if r["service_id"] in service_map:
                        exc.setdefault(r["service_id"], []).append(
                            (int(r["date"]), int(r["exception_type"])))
                for gid, sid in service_map.items():
                    dates = exc.get(gid, [])
                    for date, typ in dates:
                        db.execute("INSERT INTO service_exception VALUES(?,?,?)", (sid, date, typ))
                    if gid not in seen_cal and dates:  # service defined by calendar_dates only
                        lo, hi = min(d for d, _ in dates), max(d for d, _ in dates)
                        db.execute("INSERT INTO service VALUES(?,?,?,?)", (sid, lo, hi, 0))
                        starts.append(lo)
                        ends.append(hi)

                # stop_times: streamed, never held in memory
                with zf.open("stop_times.txt") as raw:
                    reader = csv.reader(io.TextIOWrapper(raw, "utf-8-sig", newline=""))
                    head = next(reader)
                    i_trip, i_stop, i_seq = head.index("trip_id"), head.index("stop_id"), head.index("stop_sequence")
                    i_dep, i_arr = head.index("departure_time"), head.index("arrival_time")
                    batch = []
                    for row in reader:
                        t = trip_map.get(row[i_trip])
                        if t is None:
                            continue
                        stop = stop_map.get(row[i_stop])
                        time = row[i_dep] or row[i_arr]
                        if stop is None or not time:
                            continue
                        didok = stop[0]
                        stops[didok] = stop[1:]
                        used_lines.add(t[1])
                        stop_lines.add((didok, t[1]))
                        batch.append((t[0], int(row[i_seq]), didok, parse_time(time)))
                        if len(batch) >= BATCH:
                            db.executemany("INSERT INTO stop_time VALUES(?,?,?,?)", batch)
                            batch = []
                    db.executemany("INSERT INTO stop_time VALUES(?,?,?,?)", batch)

        for didok, (full, lat, lon) in stops.items():
            name, commune = split_name(full)
            db.execute("INSERT INTO stop VALUES(?,?,?,?,?)", (didok, name, commune, lat, lon))
        for idx in sorted(used_lines):
            l = lines_cfg["lines"][idx]
            db.execute("INSERT INTO line VALUES(?,?,?,?,?,?)",
                       (idx + 1, l["label"], _color(l["bg"]), _color(l["fg"]), int(l["bordered"]), idx))
        db.executemany("INSERT INTO stop_line VALUES(?,?)", [(s, l + 1) for s, l in sorted(stop_lines)])

        version = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d%H%M")
        valid_from, valid_to = str(min(starts)), str(max(ends))
        n_trips = db.execute("SELECT count(*) FROM trip").fetchone()[0]
        db.executemany("INSERT INTO meta VALUES(?,?)",
                       [("version", version), ("valid_from", valid_from), ("valid_to", valid_to)])
        db.executescript(INDEXES)
    db.execute("VACUUM")
    db.close()
    return {"version": version, "valid_from": valid_from, "valid_to": valid_to,
            "stops": len(stops), "trips": n_trips, "bytes": os.path.getsize(out_path)}


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--gtfs", action="append", required=True, help="GTFS zip (repeat for a second feed)")
    p.add_argument("--lines", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--version-out", required=True)
    args = p.parse_args(argv)
    with open(args.lines, encoding="utf-8") as f:
        cfg = json.load(f)
    info = build(args.gtfs, cfg, args.out)
    with open(args.out, "rb") as f:
        info["sha256"] = hashlib.sha256(f.read()).hexdigest()
    with open(args.version_out, "w", encoding="utf-8") as f:
        json.dump(info, f, indent=1)
    print(json.dumps(info))


if __name__ == "__main__":
    sys.exit(main())

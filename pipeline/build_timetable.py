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


def today_zurich():
    try:
        from zoneinfo import ZoneInfo
        return datetime.datetime.now(ZoneInfo("Europe/Zurich")).date()
    except Exception:  # no tz database available
        return datetime.date.today()


def _is_live(cal, exceptions, cutoff):
    """True if the service runs on at least one date >= cutoff (a date)."""
    c = int(cutoff.strftime("%Y%m%d"))
    removed = {d for d, t in exceptions if t == 2}
    if any(t == 1 and d >= c for d, t in exceptions):
        return True
    if cal:
        start, end, days = cal
        d = max(datetime.datetime.strptime(str(start), "%Y%m%d").date(), cutoff)
        last = datetime.datetime.strptime(str(end), "%Y%m%d").date()
        while d <= last:
            if days >> d.weekday() & 1 and int(d.strftime("%Y%m%d")) not in removed:
                return True
            d += datetime.timedelta(days=1)
    return False


def build(gtfs_paths, lines_cfg, out_path, build_date=None):
    """build_date: 'YYYYMMDD' (default: today in Europe/Zurich). Trips whose service has no
    active date on or after build_date - 1 day are dropped."""
    day = (datetime.datetime.strptime(build_date, "%Y%m%d").date() if build_date
           else today_zurich())
    cutoff = day - datetime.timedelta(days=1)
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

                # 1. trips of kept routes (gtfs ids, still unfiltered)
                cand = []
                for r in _rows(zf, "trips.txt"):
                    line = route_line.get(r["route_id"])
                    if line is not None:
                        cand.append((r["trip_id"], line, r["service_id"], r["trip_headsign"]))
                wanted = {c[2] for c in cand}

                # 2. calendars of those services, then which are still running
                cal = {}
                for r in _rows(zf, "calendar.txt"):
                    if r["service_id"] in wanted:
                        days = sum(1 << i for i, c in enumerate(DAY_COLUMNS) if r[c] == "1")
                        cal[r["service_id"]] = (int(r["start_date"]), int(r["end_date"]), days)
                exc = {}
                for r in _rows(zf, "calendar_dates.txt"):
                    if r["service_id"] in wanted:
                        exc.setdefault(r["service_id"], []).append(
                            (int(r["date"]), int(r["exception_type"])))
                live = {g for g in wanted if _is_live(cal.get(g), exc.get(g, []), cutoff)}

                # 3. keep trips of live services
                trip_map = {}      # gtfs trip_id -> (int id, line index)
                kept = []
                for gid, line, sgid, headsign in cand:
                    if sgid in live:
                        trip_map[gid] = (next_trip, line)
                        kept.append((next_trip, line, sgid, headsign))
                        next_trip += 1

                # stop_times: streamed, never held in memory
                with zf.open("stop_times.txt") as raw:
                    reader = csv.reader(io.TextIOWrapper(raw, "utf-8-sig", newline=""))
                    head = next(reader)
                    i_trip, i_stop, i_seq = head.index("trip_id"), head.index("stop_id"), head.index("stop_sequence")
                    i_dep, i_arr = head.index("departure_time"), head.index("arrival_time")
                    batch = []
                    with_times = set()
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
                        with_times.add(t[0])
                        used_lines.add(t[1])
                        stop_lines.add((didok, t[1]))
                        batch.append((t[0], int(row[i_seq]), didok, parse_time(time)))
                        if len(batch) >= BATCH:
                            db.executemany("INSERT INTO stop_time VALUES(?,?,?,?)", batch)
                            batch = []
                    db.executemany("INSERT INTO stop_time VALUES(?,?,?,?)", batch)

                # 5. trips that kept stop_times, with the services they use
                service_map = {}
                for tid, line, sgid, headsign in kept:
                    if tid not in with_times:
                        continue
                    sid = service_map.get(sgid)
                    if sid is None:
                        sid = service_map[sgid] = next_service
                        next_service += 1
                        if sgid in cal:
                            start, end, days = cal[sgid]
                        else:  # defined by calendar_dates only
                            dates = [d for d, _ in exc[sgid]]
                            start, end, days = min(dates), max(dates), 0
                        db.execute("INSERT INTO service VALUES(?,?,?,?)", (sid, start, end, days))
                        starts.append(start)
                        ends.append(end)
                        db.executemany("INSERT INTO service_exception VALUES(?,?,?)",
                                       [(sid, d, t) for d, t in exc.get(sgid, [])])
                    name, commune = split_name(headsign)
                    db.execute("INSERT INTO trip VALUES(?,?,?,?,?)", (tid, line + 1, sid, name, commune))

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
    p.add_argument("--build-date", help="YYYYMMDD, default today (Europe/Zurich)")
    args = p.parse_args(argv)
    with open(args.lines, encoding="utf-8") as f:
        cfg = json.load(f)
    info = build(args.gtfs, cfg, args.out, args.build_date)
    with open(args.out, "rb") as f:
        info["sha256"] = hashlib.sha256(f.read()).hexdigest()
    with open(args.version_out, "w", encoding="utf-8") as f:
        json.dump(info, f, indent=1)
    print(json.dumps(info))


if __name__ == "__main__":
    sys.exit(main())

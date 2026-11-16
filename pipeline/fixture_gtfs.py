"""Small synthetic GTFS feeds for tests (pipeline and, later, Android test DB)."""
import csv
import io
import zipfile

STOPS_HEADER = ["stop_id", "stop_name", "stop_lat", "stop_lon", "location_type",
                "parent_station", "platform_code", "original_stop_id", "didok"]
ROUTES_HEADER = ["route_id", "agency_id", "route_short_name", "route_long_name",
                 "route_desc", "route_type"]
TRIPS_HEADER = ["route_id", "service_id", "trip_id", "trip_headsign", "trip_short_name",
                "direction_id", "block_id", "original_trip_id", "hints"]
STOP_TIMES_HEADER = ["trip_id", "arrival_time", "departure_time", "stop_id",
                     "stop_sequence", "pickup_type", "drop_off_type"]
CALENDAR_HEADER = ["service_id", "monday", "tuesday", "wednesday", "thursday", "friday",
                   "saturday", "sunday", "start_date", "end_date"]
CALENDAR_DATES_HEADER = ["service_id", "date", "exception_type"]

# (stop_id, name, lat, lon, location_type, parent, platform, didok); original_stop_id is left empty
STOPS = [
    ("Parentch:1:sloid:87255", "Fribourg, Tilleul/Cathédrale", 46.8060, 7.1530, 1, "", "", ""),
    ("8587255:0:1", "Fribourg, Tilleul/Cathédrale", 46.8061, 7.1531, 0, "Parentch:1:sloid:87255", "1", "8587255"),
    ("8587255:0:2", "Fribourg, Tilleul/Cathédrale", 46.8059, 7.1529, 0, "Parentch:1:sloid:87255", "2", "8587255"),
    ("Parentch:1:sloid:92385", "Fribourg, Neuveville/Motta", 46.8089, 7.1604, 1, "", "", ""),
    ("8592385:0:1", "Fribourg, Neuveville/Motta", 46.8090, 7.1605, 0, "Parentch:1:sloid:92385", "1", "8592385"),
    ("Parentch:1:sloid:30013", "Fribourg-Neuveville/Motta", 46.8091, 7.1606, 1, "", "", ""),
    ("8530013:0:1", "Fribourg-Neuveville/Motta", 46.8091, 7.1606, 0, "Parentch:1:sloid:30013", "1", "8530013"),
    ("Parentch:1:sloid:04100", "Villars-sur-Glâne,Petit-Moncor", 46.7900, 7.1200, 1, "", "", ""),
    ("8504100:0:1", "Villars-sur-Glâne,Petit-Moncor", 46.7901, 7.1201, 0, "Parentch:1:sloid:04100", "1", "8504100"),
    ("8507000:0:1", "Bern, Bahnhof", 46.9488, 7.4391, 0, "", "", "8507000"),
    ("8500999:0:1", "Posieux, Ecole", 46.7500, 7.1000, 0, "", "", "8500999"),
    ("8509999:0:1", "Fribourg, Ancien", 46.8000, 7.1500, 0, "", "", "8509999"),
]

ROUTES = [
    ("r1", "834", "1", "", "B", 700),
    ("r2", "834", "2", "", "B", 700),
    ("r6", "834", "6", "", "B", 700),
    ("r124", "834", "124", "", "B", 700),
    ("rbern2", "11", "2", "", "B", 700),
    ("rfun", "3004", "2030", "", "FUN", 1400),
]

# Feed 1: trip_id, route, service, headsign, [(stop_id, "arr/dep", seq)]
# Feed 2 reuses the very same service_id and trip_id strings with other content.


def _feed(n, extras):
    d = 0 if n == 1 else 600  # feed 2 runs 10 minutes later
    def hm(base_h, base_m, extra=0):
        s = base_h * 3600 + base_m * 60 + d + extra
        return "%02d:%02d:00" % (s // 3600, (s % 3600) // 60)
    hs = "Fribourg, Marly Gare" if n == 1 else "Fribourg, Marly Centre"
    trips = [
        ("r2", "TA", "T2", hs,
         [("8587255:0:1", hm(8, 0), 1), ("8592385:0:1", hm(8, 5), 2),
          ("8504100:0:1", "24:45:00" if n == 1 else "24:55:00", 3)]),
        ("r1", "TA", "T1", "Villars-sur-Glâne,Petit-Moncor",
         [("8587255:0:2", hm(9, 0), 1), ("8504100:0:1", hm(9, 10), 2)]),
        ("r6", "TA", "T6", "Fribourg, Tilleul/Cathédrale",
         [("8504100:0:1", hm(10, 0), 1), ("8587255:0:1", hm(10, 9), 2)]),
        ("rfun", "TA", "TF", "Fribourg, Pérolles",
         [("8530013:0:1", hm(11, 0), 1)]),
        ("r124", "TA", "T124", "Posieux", [("8500999:0:1", hm(12, 0), 1)]),
        ("rbern2", "TA", "TBERN", "Bern", [("8507000:0:1", hm(13, 0), 1)]),
    ]
    if n == 1 and extras:
        # services for the build-date filter: expired, ending the day before 2026-04-01,
        # and one whose only active date is an added exception (2026-09-01)
        trips += [
            ("r1", "TOLD", "TOLDT", "Fribourg, Ancien",
             [("8509999:0:1", hm(14, 0), 1), ("8587255:0:1", hm(14, 5), 2)]),
            ("r1", "TEDGE", "TEDGET", "Fribourg, Marge", [("8587255:0:1", hm(15, 0), 1)]),
            ("r1", "TADD", "TADDT", "Fribourg, Ajout", [("8587255:0:1", hm(16, 0), 1)]),
        ]
    if n == 1:
        calendar = [("TA", 1, 1, 1, 1, 1, 0, 0, 20251214, 20261212),
                    ("TZ", 1, 1, 1, 1, 1, 1, 1, 20200101, 20300101)]
        dates = [("TA", 20261224, 2), ("TZ", 20260101, 2)]
        if extras:
            calendar += [("TOLD", 1, 1, 1, 1, 1, 1, 1, 20250101, 20260301),
                         ("TEDGE", 1, 1, 1, 1, 1, 1, 1, 20260101, 20260331),
                         ("TADD", 0, 0, 0, 0, 0, 0, 0, 20260101, 20261231)]
            dates.append(("TADD", 20260901, 1))
    else:
        calendar = [("TA", 0, 0, 0, 0, 0, 1, 1, 20261213, 20271211)]
        dates = [("TA", 20271225, 1)]
    return trips, calendar, dates


def _csv(header, rows):
    buf = io.StringIO(newline="")
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(header)
    w.writerows(rows)
    return ("﻿" + buf.getvalue()).encode("utf-8")


def write_fixture_gtfs(path, feed=1, extras=False):
    """Write a small GTFS zip. feed=1 is the current year, feed=2 the next one
    (same service_id / trip_id strings, different calendars and times).
    extras=True adds three feed-1 trips with services that expire at different dates."""
    trips, calendar, dates = _feed(feed, extras)
    trip_rows, st_rows = [], []
    for route, service, tid, headsign, sts in trips:
        trip_rows.append((route, service, tid, headsign, "", 0, "", "", ""))
        for stop_id, t, seq in sts:
            st_rows.append((tid, t, t, stop_id, seq, 0, 0))
    files = {
        "stops.txt": _csv(STOPS_HEADER, [r[:7] + ("",) + r[7:] for r in STOPS]),
        "routes.txt": _csv(ROUTES_HEADER, ROUTES),
        "trips.txt": _csv(TRIPS_HEADER, trip_rows),
        "stop_times.txt": _csv(STOP_TIMES_HEADER, st_rows),
        "calendar.txt": _csv(CALENDAR_HEADER, calendar),
        "calendar_dates.txt": _csv(CALENDAR_DATES_HEADER, dates),
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in files.items():
            z.writestr(name, data)

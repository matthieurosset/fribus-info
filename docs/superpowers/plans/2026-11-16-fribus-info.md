# FriBus Info Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that reproduces the old « TPF Info » app (nearby stops → departures → trip), limited to Fribourg urban lines, fed by a weekly-rebuilt SQLite timetable and live delays from transport.opendata.ch.

**Architecture:** A Python pipeline (stdlib only) turns the Swiss GTFS feed into a small SQLite file published as a GitHub release asset; GitHub Actions rebuilds it weekly and builds the signed APK. The app bundles one copy of that database, replaces it when a newer one is published, reads it directly with `android.database.sqlite`, and overlays real-time delays for the stop on screen.

**Tech Stack:** Python 3.12 (csv, zipfile, sqlite3, unittest) · Kotlin 2.1.0, AGP 8.8.0, Gradle 8.12, Jetpack Compose (BOM 2025.01.00), Navigation Compose 2.8.5, kotlinx-coroutines 1.9.0, compileSdk/targetSdk 35, minSdk 26, JDK 21 · `android.location.LocationManager` (no Play services) · `HttpURLConnection` + `org.json` (no HTTP library) · GitHub Actions + `gh`.

**Spec:** `docs/superpowers/specs/2026-11-16-fribus-info-design.md`

## Global Constraints

- App name « FriBus Info »; package `ch.fribusinfo`; UI in French only. No « TPF » name or logo anywhere in the UI, icon or store text.
- Lines kept (spec §5): `1 2 3 4 5 6 8 9 11 12 13 542 N1 N2 N3 N5` + funicular (GTFS `route_short_name` `2030`, displayed `FUN`). TPF agencies `53`, `834`, `3004` only.
- Screens: Nearby (home), Departures, Trip, Search, Favorites. Drawer: exactly « À proximité », « Rechercher », « Favoris ». Stop menu ⋮: « Ajouter aux favoris »/« Retirer des favoris », « Afficher l'heure »/« Afficher les minutes ». Nothing else (spec §3, §6).
- No offline banner, no ticket button, no settings, no about page, no analytics/ads/trackers, no API key, no NAS.
- No network → theoretical timetable, no error message. Realtime only for the stop on screen, ~30 s cache, only while visible.
- Repo public on GitHub (`matthieurosset/fribus-info`); timetable DB + APK downloadable without login from releases.
- Colours (sampled from the old app): top bar `#A80231`, minutes button `#018BC1` with white text, dividers `#E0E0E0`, grey pin/secondary `#858585`, pearl-chain line `#999696`, current-stop row `#7289BA` (white text), terminus row `#D4DAE7`. Line colours: spec §8 table.

## Review Focus

1. **Departures after midnight** — GTFS times ≥ 24:00:00 (night lines N1–N5, last buses) belong to the previous service day; at 00:30 the list must show a bus scheduled `24:45:00` of yesterday's service. Test in Task 4.
2. **December timetable change** — two GTFS feeds (2026 + 2027) reuse the same `service_id`/`trip_id` strings; merging them must not mix calendars. Test in Task 1.
3. **Stop with two platforms / same name twice** — `Fribourg, Neuveville/Motta` (bus, 8592385) and `Fribourg-Neuveville/Motta` (funicular, 8530013) are distinct stops, like in the old app; platforms of one parent station collapse into one stop. Test in Task 1.
4. **Realtime mismatch** — API entry whose destination text differs from the GTFS headsign, or a cancelled/unknown trip: never shift the wrong bus; unmatched realtime is ignored. Test in Task 9.
5. **Location missing or outside Fribourg** — permission refused, GPS off, or no stop within 2 km → centred text, drawer and search still usable, never an endless spinner. Test in Task 5.

---

## File Structure

```
pipeline/
  lines.json                 whitelist, display labels, colours, sort order
  build_timetable.py         GTFS zip(s) -> timetable.db (+ version.json)
  test_build_timetable.py    unittest with an in-memory fixture GTFS
.github/workflows/
  timetable.yml              weekly + manual: build DB, publish to release tag `timetable`
  apk.yml                    on tag v*: fetch DB, build signed APK, attach to release
app/                         single Android module, package ch.fribusinfo
  src/main/assets/timetable.db            bundled DB (not committed; fetched by build)
  src/main/java/ch/fribusinfo/
    MainActivity.kt          Compose host, NavHost, drawer
    data/Models.kt           Stop, Line, Departure, TripStop
    data/ServiceCalendar.kt  pure: is a service active on a date
    data/StopNames.kt        pure: split "Commune, Nom"
    data/TimetableDb.kt      install/open the SQLite file
    data/TimetableRepository.kt  queries
    data/Favorites.kt        SharedPreferences set of stop ids + time-format toggle
    location/LocationSource.kt   LocationManager wrapper (Flow<Location?>)
    realtime/StationboardClient.kt  HTTP + JSON parse
    realtime/RealtimeMatcher.kt     pure: local departures × API -> delays
    realtime/RealtimeCache.kt       30 s TTL, 429 back-off
    update/TimetableUpdater.kt      daily check + atomic DB swap
    ui/Theme.kt  ui/LineBadge.kt  ui/StopRow.kt
    ui/NearbyScreen.kt  ui/DeparturesScreen.kt  ui/TripScreen.kt
    ui/SearchScreen.kt  ui/FavoritesScreen.kt
  src/test/...               JVM unit tests (pure logic)
  src/androidTest/...        instrumented tests on the emulator (DB queries, UI smoke)
```

## Database contract (shared by pipeline and app)

```sql
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);          -- version (yyyymmddHHMM), valid_from, valid_to (yyyymmdd)
CREATE TABLE stop(id INTEGER PRIMARY KEY,                      -- DIDOK number, e.g. 8587255
  name TEXT NOT NULL, commune TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE TABLE line(id INTEGER PRIMARY KEY, label TEXT NOT NULL, -- "2", "N1", "FUN"
  bg INTEGER NOT NULL, fg INTEGER NOT NULL, bordered INTEGER NOT NULL, sort INTEGER NOT NULL); -- colours as 0xRRGGBB
CREATE TABLE service(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, "end" INTEGER NOT NULL, days INTEGER NOT NULL); -- days bit0=Mon..bit6=Sun
CREATE TABLE service_exception(service_id INTEGER NOT NULL, date INTEGER NOT NULL, type INTEGER NOT NULL); -- 1 added, 2 removed
CREATE TABLE trip(id INTEGER PRIMARY KEY, line_id INTEGER NOT NULL, service_id INTEGER NOT NULL,
  headsign TEXT NOT NULL, headsign_commune TEXT NOT NULL);
CREATE TABLE stop_time(trip_id INTEGER NOT NULL, seq INTEGER NOT NULL, stop_id INTEGER NOT NULL,
  dep INTEGER NOT NULL);                                       -- seconds after service-day midnight, may exceed 86400
CREATE TABLE stop_line(stop_id INTEGER NOT NULL, line_id INTEGER NOT NULL, PRIMARY KEY(stop_id, line_id));
CREATE INDEX st_stop ON stop_time(stop_id, dep);
CREATE INDEX st_trip ON stop_time(trip_id, seq);
CREATE INDEX se_svc ON service_exception(service_id, date);
```

Name rule (pipeline and app agree): `"Fribourg, Tilleul/Cathédrale"` → name `Tilleul/Cathédrale`, commune `Fribourg`; no comma → name and commune both the full string (old app behaviour). Stops are grouped by GTFS parent station; `stop.id` = the `didok` column.

---

### Task 1: Timetable pipeline

**Files:**
- Create: `pipeline/lines.json`, `pipeline/build_timetable.py`, `pipeline/test_build_timetable.py`, `.gitignore` (`*.db`, `*.zip`, `keystore.properties`, `*.keystore`, `build/`, `.gradle/`, `local.properties`, `app/src/main/assets/timetable.db`)

**Interfaces:**
- Produces: CLI `python pipeline/build_timetable.py --gtfs A.zip [--gtfs B.zip] --lines pipeline/lines.json --out timetable.db --version-out version.json`; function `build(gtfs_paths: list[str], lines_cfg: dict, out_path: str) -> dict` returning `{"version", "valid_from", "valid_to", "stops", "trips", "bytes"}`. `version.json` = that dict plus `"sha256"` of the DB file.

`lines.json` shape: `{"agencies": ["53","834","3004"], "lines": [{"gtfs": "1", "label": "1", "bg": "#8CA029", "fg": "#FFFFFF", "bordered": false}, …, {"gtfs": "2030", "label": "FUN", "bg": "#FFFFFF", "fg": "#000000", "bordered": true}]}` — array order is the `sort` value; colours from spec §8 (13 = same as 12).

- [ ] **Step 1: Write failing tests** (fixture GTFS zips built in `setUp` with `zipfile`, two feeds):
  - `test_keeps_only_whitelisted_tpf_lines`: routes `2` (agency 834), `124` (834), `2` (agency 11 = not TPF) → `line` has exactly label `2`.
  - `test_funicular_label_and_border`: route `2030` → `line.label == "FUN"`, `bordered == 1`.
  - `test_platforms_collapse_to_parent_didok`: two platform stops with parent `Parentch:1:sloid:87255`, didok `8587255` → one `stop` row id `8587255`, name `Tilleul/Cathédrale`, commune `Fribourg`.
  - `test_distinct_didok_same_place_stay_distinct`: `8592385` "Fribourg, Neuveville/Motta" and `8530013` "Fribourg-Neuveville/Motta" → two rows; the second has name == commune == `Fribourg-Neuveville/Motta`.
  - `test_times_after_midnight_kept_as_seconds`: stop_time `24:45:00` → `dep == 89100`.
  - `test_two_feeds_do_not_mix_services`: both feeds define service `TA` with different weekdays; resulting trips from feed 2 reference a `service` row with feed 2's days.
  - `test_stop_line_lists_lines_per_stop` and `test_only_stops_served_by_kept_lines`.
  - `test_meta_valid_range` = min start / max end over kept services.
- [ ] **Step 2:** `python -I -m unittest pipeline/test_build_timetable.py -v` → FAIL (module missing).
- [ ] **Step 3: Implement `build(...)`.** Stream every CSV with `csv.DictReader(io.TextIOWrapper(zip.open(name), "utf-8-sig"))`; never load `stop_times.txt` into memory (3.7 GB) — keep a dict `trip_id -> new int id` for kept trips and insert matching rows as they stream. Remap all GTFS string ids to integers **per feed** (key = `(feed_index, gtfs_id)`). Only copy `calendar_dates` rows whose service is used. Headsign split with the name rule. Write with one transaction, then `VACUUM`.
- [ ] **Step 4:** Tests PASS.
- [ ] **Step 5: Real run.** Download `https://data.opentransportdata.swiss/de/dataset/timetable-2026-gtfs2020/permalink` (and `…timetable-2027-gtfs2020/permalink`) to a scratch dir, run the CLI on both. Expected: finishes, DB < 15 MB, `SELECT label FROM line ORDER BY sort` lists the 17 labels, `SELECT count(*) FROM stop` between 150 and 450, stop `8587255` has lines `1 2 6` among others.
- [ ] **Step 6: Commit** `pipeline/ .gitignore` — « Pipeline : horaire GTFS → base SQLite ».

### Task 2: GitHub repo and weekly timetable publication

**Files:**
- Create: `.github/workflows/timetable.yml`, `README.md` (French: what it is, install the APK from Releases, data sources opentransportdata.swiss + transport.opendata.ch, not affiliated with any transport company)

**Interfaces:**
- Produces: release tag `timetable` holding `timetable.db` and `version.json` at
  `https://github.com/matthieurosset/fribus-info/releases/download/timetable/version.json` and `…/timetable/timetable.db`.

- [ ] **Step 1:** `timetable.yml`: `schedule: cron "0 4 * * 1"` + `workflow_dispatch`; ubuntu-latest, Python 3.12; download current-year permalink (`timetable-$(date +%Y)-gtfs2020`) and next-year one if it answers 302/200; run `python -I -m unittest` then the CLI; `gh release create timetable --title "Horaires" --notes "Base d'horaires reconstruite chaque semaine." || true`; `gh release upload timetable timetable.db version.json --clobber`. Permissions `contents: write`.
- [ ] **Step 2:** Create the public repo and push: `gh repo create matthieurosset/fribus-info --public --source C:/git/fribus-info --push`.
- [ ] **Step 3:** `gh workflow run timetable.yml`, wait, then `curl -sL …/releases/download/timetable/version.json` → JSON with `version` and `sha256`; `curl -sIL …/timetable.db` → 200 without auth.
- [ ] **Step 4: Commit** — « Publication hebdomadaire de la base d'horaires ».

### Task 3: Android skeleton, theme, signed build

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper 8.12, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION; portrait), `MainActivity.kt`, `ui/Theme.kt`, launcher icon (white bus pictogram on `#A80231`, no TPF mark), `scripts/fetch_timetable.sh` (downloads the release DB into `app/src/main/assets/timetable.db`), `.github/workflows/apk.yml`
- Local only (gitignored): `release.keystore`, `keystore.properties` (storeFile, storePassword, keyAlias=`fribus`, keyPassword — random 24-char passwords)

**Interfaces:**
- Produces: `FriBusTheme { }` composable; `object Colors { Bar, MinutesButton, Divider, Secondary, PearlLine, PearlCurrent, PearlEnd }` with the Global Constraints values; nav routes `"nearby"`, `"search"`, `"favorites"`, `"stop/{stopId}"`, `"trip/{tripId}/{serviceDate}/{fromSeq}"`; `@Composable fun AppScaffold(title: String, navController, actions: @Composable RowScope.() -> Unit, content)` (bar + hamburger + drawer with the three entries).

- [ ] **Step 1:** Write the Gradle files; `app/build.gradle.kts` reads `keystore.properties` when present, else env vars `FRIBUS_STORE_PASSWORD`, `FRIBUS_KEY_PASSWORD`, `FRIBUS_KEYSTORE` (CI). `versionName` from git tag, default `0.1.0`.
- [ ] **Step 2:** Generate the keystore with `keytool -genkeypair -alias fribus -keyalg RSA -keysize 4096 -validity 36500`; copy it to `C:\apps\FriBusInfo\` as backup; `gh secret set FRIBUS_KEYSTORE_B64`, `FRIBUS_STORE_PASSWORD`, `FRIBUS_KEY_PASSWORD`.
- [ ] **Step 3:** Drawer + bar + empty screens per route. Read the terms of use of opentransportdata.swiss and transport.opendata.ch; if either requires naming the source, add one small grey line at the bottom of the drawer: « Données : opentransportdata.swiss · transport.opendata.ch » (spec §9), otherwise nothing. `./gradlew assembleDebug` (Git Bash, `ANDROID_HOME=/c/Users/Matthieu/AppData/Local/Android/Sdk`) → BUILD SUCCESSFUL; install on AVD `mission_geo_phone`, screenshot: bordeaux bar « FriBus Info », drawer shows exactly the three entries.
- [ ] **Step 4:** `apk.yml`: on push tag `v*`: JDK 21, `scripts/fetch_timetable.sh`, decode keystore, `./gradlew assembleRelease testDebugUnitTest`, rename to `FriBusInfo-${tag}.apk`, `gh release create $tag FriBusInfo-$tag.apk`.
- [ ] **Step 5: Commit** — « Squelette Android, thème et build signé ».

### Task 4: Data layer

**Files:**
- Create: `data/Models.kt`, `data/ServiceCalendar.kt`, `data/StopNames.kt`, `data/TimetableDb.kt`, `data/TimetableRepository.kt`, `data/Favorites.kt`
- Test: `app/src/test/java/ch/fribusinfo/data/ServiceCalendarTest.kt`, `StopNamesTest.kt`; `app/src/androidTest/java/ch/fribusinfo/data/TimetableRepositoryTest.kt` (uses a small DB built by the pipeline fixture, copied to `androidTest/assets/test.db`)

**Interfaces:**
- Produces:
  - `data class Stop(val id: Long, val name: String, val commune: String, val lat: Double, val lon: Double)`
  - `data class Line(val id: Int, val label: String, val bg: Int, val fg: Int, val bordered: Boolean)`
  - `data class StopWithLines(val stop: Stop, val lines: List<Line>, val distanceMeters: Int?)`
  - `data class Departure(val tripId: Long, val line: Line, val headsign: String, val headsignCommune: String, val serviceDate: LocalDate, val seq: Int, val scheduled: ZonedDateTime)`
  - `data class TripStop(val stop: StopWithLines, val seq: Int, val scheduled: ZonedDateTime)`
  - `class ServiceCalendar(…) { fun isActive(serviceId: Long, date: LocalDate): Boolean }`
  - `fun splitStopName(raw: String): Pair<String, String>` (name, commune)
  - `class TimetableRepository(db: SQLiteDatabase, zone: ZoneId = ZoneId.of("Europe/Zurich"))` with `nearby(lat: Double, lon: Double, maxMeters: Int = 2000, limit: Int = 15): List<StopWithLines>`, `search(query: String, limit: Int = 50): List<StopWithLines>`, `stop(id: Long): StopWithLines?`, `departures(stopId: Long, from: ZonedDateTime, limit: Int = 60, horizon: Duration = Duration.ofHours(6)): List<Departure>`, `trip(tripId: Long, serviceDate: LocalDate): List<TripStop>`
  - `object TimetableDb { fun open(context: Context): SQLiteDatabase }` — copies `assets/timetable.db` to `filesDir/timetable.db` on first run or when the bundled `meta.version` is newer, opens read-only.
  - `class Favorites(context)`: `ids: StateFlow<Set<Long>>`, `toggle(id: Long)`, `absoluteTime: StateFlow<Boolean>`, `setAbsoluteTime(Boolean)`.

- [ ] **Step 1: Failing tests.** `ServiceCalendarTest`: weekday bit set and in range → true; out of range → false; exception type 2 on an active date → false; type 1 on an inactive date → true. `StopNamesTest`: `"Fribourg, Tilleul/Cathédrale"` → (`Tilleul/Cathédrale`, `Fribourg`); `"Villars-sur-Glâne,Petit-Moncor"` (no space) → (`Petit-Moncor`, `Villars-sur-Glâne`); `"Fribourg-Neuveville/Motta"` → (same, same). `TimetableRepositoryTest`: `departures` at 00:30 includes a trip at `dep = 89100` of the previous service date with `scheduled` = 00:45 today; `nearby` is sorted by distance and excludes stops > 2000 m; `search("tilleul")` and `search("cathedrale")` (accent-insensitive) both find 8587255; lines in `StopWithLines` follow `line.sort`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest` → FAIL.
- [ ] **Step 3: Implement.** Departures query runs for service dates *yesterday* (dep ≥ 86400 only) and *today*, filters with `ServiceCalendar`, merges, sorts by `scheduled`. Nearby: bounding-box SQL prefilter then haversine in Kotlin. Accent-insensitive search: normalise with `java.text.Normalizer` NFD + strip marks on both sides (query in Kotlin over all stops; the table is a few hundred rows).
- [ ] **Step 4:** `./gradlew testDebugUnitTest connectedDebugAndroidTest` (emulator running) → PASS.
- [ ] **Step 5: Commit** — « Couche données : arrêts, départs, courses ».

### Task 5: Nearby screen (home)

**Files:**
- Create: `location/LocationSource.kt`, `ui/LineBadge.kt`, `ui/StopRow.kt`, `ui/NearbyScreen.kt`
- Test: `app/src/androidTest/java/ch/fribusinfo/ui/NearbyScreenTest.kt`

**Interfaces:**
- Consumes: `TimetableRepository.nearby`, `StopWithLines`, `Line`.
- Produces: `@Composable fun LineBadge(line: Line, small: Boolean = false)`; `@Composable fun StopRow(item: StopWithLines, onClick: () -> Unit)` (reused by Search and Favorites; distance column hidden when `distanceMeters == null`); `class LocationSource(context) { fun updates(): Flow<Location?> }` (null = unavailable).

- [ ] **Step 1: Failing UI tests:** with a fake location at 46.8032/7.1597 the first row is « Neuveville-Motta » with distance text `125 m`-style (rounded to 25 m like the old app); permission denied → text « Position indisponible » visible and drawer opens; location 47.3769/8.5417 (Zürich) → « Aucun arrêt à proximité ».
- [ ] **Step 2:** `./gradlew connectedDebugAndroidTest` → FAIL.
- [ ] **Step 3: Implement** to match `docs/reference/ancienne-app/1-arrets-proches.png`: grey pin `#858585` + distance under it on the left, bold name, grey commune below, badges right-aligned, max 4 per row × 2 rows, 8th+ replaced by « … »; divider `#E0E0E0`. Bar title « À proximité », actions loupe → `search`, target icon → re-request location. Ask runtime permission on first open. `LocationManager` network + GPS providers, last known location first.
- [ ] **Step 4:** Tests PASS; screenshot on the AVD next to the reference.
- [ ] **Step 5: Commit** — « Écran arrêts à proximité ».

### Task 6: Departures screen

**Files:**
- Create: `ui/DeparturesScreen.kt`
- Test: `app/src/androidTest/java/ch/fribusinfo/ui/DeparturesScreenTest.kt`

**Interfaces:**
- Consumes: `TimetableRepository.departures/stop`, `Favorites`, `LineBadge`.
- Produces: `@Composable fun DepartureRow(dep: Departure, now: ZonedDateTime, absolute: Boolean, delay: Duration?, onClick: () -> Unit)` (Task 9 passes `delay`).

- [ ] **Step 1: Failing tests:** row shows badge, headsign bold, commune grey, button text `7'` for a departure 7 min ahead and `0'` when < 1 min; ⋮ → « Afficher l'heure » switches to `HH:mm` and persists after reopening; « Ajouter aux favoris » adds the stop to `Favorites.ids`; list re-ticks every 30 s (departed rows disappear).
- [ ] **Step 2:** FAIL. **Step 3:** Implement per `2-departs-arret.png` / `3-menu-arret.png`: square badge left (large), minutes in a rounded `#018BC1` button with white bold text, bar title = stop name, actions loupe + target + ⋮ (no ticket icon, no red banner). Tap row → `trip/{tripId}/{serviceDate}/{seq}`.
- [ ] **Step 4:** PASS + screenshot vs reference. **Step 5: Commit** — « Écran des départs d'un arrêt ».

### Task 7: Trip screen

**Files:**
- Create: `ui/TripScreen.kt`
- Test: `app/src/androidTest/java/ch/fribusinfo/ui/TripScreenTest.kt`

**Interfaces:**
- Consumes: `TimetableRepository.trip`, `StopRow` pieces, `LineBadge`.
- Produces: `@Composable fun TripScreen(tripId: Long, serviceDate: LocalDate, fromSeq: Int, delay: Duration?)`.

- [ ] **Step 1: Failing tests:** minutes column counts from the chosen stop (`0'` at it, previous stops show no minutes like the old app shows them scrolled above); initial scroll centres the chosen stop; chosen row background `#7289BA` with white text; terminus row `#D4DAE7` with filled red dot; title shows badge + headsign.
- [ ] **Step 2:** FAIL. **Step 3:** Implement per `4-trajet-course.png`: vertical line `#999696` through hollow circles, badges of the kept lines at each stop (max 2 rows + « … »). **Step 4:** PASS + screenshot. **Step 5: Commit** — « Écran trajet d'une course ».

### Task 8: Search and Favorites

**Files:**
- Create: `ui/SearchScreen.kt`, `ui/FavoritesScreen.kt`
- Test: `app/src/androidTest/java/ch/fribusinfo/ui/SearchAndFavoritesTest.kt`

**Interfaces:**
- Consumes: `TimetableRepository.search/stop`, `Favorites`, `StopRow`.

- [ ] **Step 1: Failing tests:** typing `gare` lists matching stops as you type (no distance column), back arrow and ✕ clear like `5-recherche.png`; Favorites lists favourite stops in name order, empty state text « Aucun arrêt favori ».
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5: Commit** — « Recherche et favoris ».

### Task 9: Real-time delays

**Files:**
- Create: `realtime/StationboardClient.kt`, `realtime/RealtimeMatcher.kt`, `realtime/RealtimeCache.kt`; modify `ui/DeparturesScreen.kt`, `ui/TripScreen.kt`, `MainActivity.kt` (pass delay to trip route state)
- Test: `app/src/test/java/ch/fribusinfo/realtime/StationboardParseTest.kt` (fixture: a saved real response in `src/test/resources/stationboard_8587255.json`), `RealtimeMatcherTest.kt`, `RealtimeCacheTest.kt`; `testImplementation("org.json:json:20240303")`

**Interfaces:**
- Produces: `data class RtDeparture(val line: String, val to: String, val scheduled: ZonedDateTime, val expected: ZonedDateTime?)`; `class StationboardClient(baseUrl: String = "https://transport.opendata.ch/v1/") { suspend fun fetch(stopId: Long, limit: Int = 60): Result<List<RtDeparture>>; class TooManyRequests }` (GET `stationboard?id=<stopId>&limit=<limit>`, 8 s timeouts); `fun parseStationboard(json: String): List<RtDeparture>` (uses `number`, `to`, `stop.departure`, `stop.prognosis.departure`); `object RealtimeMatcher { fun match(local: List<Departure>, rt: List<RtDeparture>): Map<Long, Duration> }` keyed by `tripId`; `class RealtimeCache(client, clock) { fun delays(stopId: Long, local: List<Departure>): Flow<Map<Long, Duration>> }`.

- [ ] **Step 1: Failing tests:**
  - parse: saved fixture → entries with line `2`, `to`, scheduled and expected times in Europe/Zurich.
  - matcher: same line + same scheduled minute + normalised destination equal → delay = expected − scheduled; destination differs but line+minute unique → still matched; two candidates with same line+minute and different destinations and no destination match → no match; `expected == null` → no entry; funicular local label `FUN` matches API entries whose `category` is funicular (check the real API label during Step 3 and pin it in the test).
  - cache: second call within 30 s doesn't hit the client; after `TooManyRequests`, no call for 5 min and the flow emits an empty map (theoretical timetable).
- [ ] **Step 2:** `./gradlew testDebugUnitTest` → FAIL.
- [ ] **Step 3: Implement.** Destination normalisation: lowercase, NFD strip accents, drop `fribourg/freiburg, ` style commune prefix, collapse punctuation. Collect only while the screen is in `STARTED` (`repeatOnLifecycle`). UI: minutes button shows `scheduled + delay`; when delay ≥ 1 min, a small yellow label (`#FFD100`, black bold text) « +N' » sits left of the button — the old app's real-time label was yellow; this is our closest rendering. Trip screen shifts the times of stops after the chosen one by the same delay. Any error → render without delays, no message.
- [ ] **Step 4:** Unit tests PASS; on the AVD with network, open Tilleul/Cathédrale and compare 5 departures with `curl "https://transport.opendata.ch/v1/stationboard?id=8587255&limit=10"`. Airplane mode → departures still shown, no error.
- [ ] **Step 5: Commit** — « Retards en temps réel ».

### Task 10: Timetable updater

**Files:**
- Create: `update/TimetableUpdater.kt`; modify `MainActivity.kt` (launch on start)
- Test: `app/src/test/java/ch/fribusinfo/update/TimetableUpdaterTest.kt` (fake HTTP via an interface `Fetcher { fun get(url: String): ByteArray }`)

**Interfaces:**
- Consumes: `TimetableDb`.
- Produces: `class TimetableUpdater(context, fetcher: Fetcher, clock: Clock) { suspend fun checkIfDue(): Boolean }` — URLs from Task 2.

- [ ] **Step 1: Failing tests:** no check if the last one was < 24 h ago; newer `version` → downloads, verifies `sha256`, writes `timetable.db.tmp`, opens it and reads `meta.version`, then renames over the live file and signals the repository to reopen; bad hash or corrupt file → old DB untouched; same version → no download; network error → returns false silently.
- [ ] **Step 2:** FAIL. **Step 3:** Implement (`MessageDigest`, `File.renameTo` after closing the old DB; repository held behind a `StateFlow<TimetableRepository>` so screens recompose on swap). **Step 4:** PASS. **Step 5: Commit** — « Mise à jour automatique des horaires ».

### Task 11: First release

- [ ] **Step 1:** `./gradlew testDebugUnitTest connectedDebugAndroidTest` → all PASS.
- [ ] **Step 2:** `git tag v1.0.0 && git push --tags`; wait for `apk.yml`; download `FriBusInfo-v1.0.0.apk` from the release page without being logged in (`curl -sIL` → 200).
- [ ] **Step 3:** Install that APK on the AVD, cold start in airplane mode → nearby list from the bundled DB; back online → no crash, updater runs.

### Task 12: Final comparison round (spec §11)

- [ ] **Step 1:** On AVD `mission_geo_phone`: install `C:\Users\Matthieu\Downloads\TPF+Info_2.9.10_APKPure.apk`, `adb root`, `settings put global auto_time 0`, `date 061514002021.00`, grant location, `geo fix 7.1597 46.8032`; capture: nearby, departures Tilleul-Cathédrale, its ⋮ menu, trip of the first line 2, search « gare », drawer. Then restore: uninstall, `auto_time 1`, `adb unroot`.
- [ ] **Step 2:** Same position, same screens in FriBus Info (current date).
- [ ] **Step 3:** Write `docs/recette/2026-xx-xx-comparaison.md`: one table per screen with both screenshots (`docs/recette/*.png`) and every difference, each tagged *voulu (spec §x)* or *corrigé (commit)*. Expected differences: no TPF logo, French labels, 3-entry drawer, no ticket icon, no red banner, only kept lines, 2026 data, delay label. Anything else → fix, recapture.
- [ ] **Step 4:** Conclude « aucun écart non souhaité », commit, report the table to Matthieu.

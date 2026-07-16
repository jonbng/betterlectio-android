# BetterLectio Android — Debug Session Report

**Date:** 2026-07-16  
**Device:** Pixel 8 Pro (`husky`, Android 17) via wireless ADB (phone hotspot)  
**Build:** `1.0.0-debug` (versionCode 7, `DEBUGGABLE`)  
**Package:** `dk.betterlectio.android`  
**Session window:** ~13:51:20 – 13:55:06 CEST (app launched after debug install)  
**Raw logs:** `debug-logs/full-logcat.txt` (~38k lines)  
**Crash excerpts:** `debug-logs/crashes/`  

> Observation only — **no fixes applied** in this session.

---

## Summary

| Severity | Count | Theme |
|----------|------:|-------|
| **Process crashes (FATAL)** | **3** | Duplicate Compose `LazyColumn` keys |
| **Load failures (Lectio HTTP)** | **~22** message list requests | `beskeder2.aspx?type=liste` → Lectio error page |
| **Backend / analytics errors** | Repeated | PostHog empty API key; Supabase RLS on schedule sync |
| **Successful areas** | — | Login (MitID), schedule, homework, assignments, student card, activity detail |

**Root theme of all 3 crashes:** `java.lang.IllegalArgumentException` — Compose Lazy list item keys are not unique within the same `LazyColumn`.

---

## Process crashes (FATAL EXCEPTION)

### Crash 1 — Grades screen

| Field | Value |
|-------|--------|
| **Time** | 13:53:02.715 |
| **PID** | 20510 |
| **Trigger** | Opened grades (`grades_viewed` event + GET `grades/grade_report.aspx` → 200) |
| **Exception** | `java.lang.IllegalArgumentException` |

**Message:**

```text
Key "Tysk fortsættersprog B1g Ty 4FIRST_STANDPOINT" was already used.
If you are using LazyColumn/Row please make sure you provide a unique key for each item.
```

**Likely source (code):**

```298:298:app/src/main/java/dk/betterlectio/android/ui/screens/more/MoreScreen.kt
items(visible, key = { it.subject + it.team + gradeType.name }) { g ->
```

Duplicate grades share the same `subject + team + gradeType`, so the LazyColumn key collides (e.g. two “Tysk…” rows with the same standpoint type).

**Stack (top):**

```text
at androidx.compose.ui.layout.LayoutNodeSubcompositionsState.subcompose(SubcomposeLayout.kt:1592)
at androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScopeImpl.compose(...)
at androidx.compose.foundation.lazy.LazyListMeasureKt.measureLazyList-pIk1_oM(...)
… (main thread, measure/layout)
```

**Excerpt:** `debug-logs/crashes/crash-01-grades.txt`

---

### Crash 2 — Studieplan (study plans) list

| Field | Value |
|-------|--------|
| **Time** | 13:53:20.129 |
| **PID** | 21235 (restart after crash 1) |
| **Trigger** | GET `studieplan.aspx` → 200, then render list |
| **Exception** | `java.lang.IllegalArgumentException` |

**Message:**

```text
Key "/lectio/94/studieplan.aspx?displaytype=ugeteksttabel&elevid=72721772841" was already used.
If you are using LazyColumn/Row please make sure you provide a unique key for each item.
```

**Likely source (code):**

```777:777:app/src/main/java/dk/betterlectio/android/ui/screens/more/MoreScreen.kt
items(state.plans, key = { it.id }) { p ->
```

Multiple plan rows resolve to the same `id` (here a full Lectio URL), so the key is not unique.

**Excerpt:** `debug-logs/crashes/crash-02-studieplan.txt`

---

### Crash 3 — Settings screen

| Field | Value |
|-------|--------|
| **Time** | 13:54:07.114 |
| **PID** | 21398 |
| **Trigger** | Settings UI composition (no new Lectio request immediately before crash) |
| **Exception** | `java.lang.IllegalArgumentException` |

**Message:**

```text
Key "SYSTEM" was already used.
If you are using LazyColumn/Row please make sure you provide a unique key for each item.
```

**Likely source (code):** Single Settings `LazyColumn` uses enum `.name` as keys for two different enums that both contain `SYSTEM`:

```835:835:app/src/main/java/dk/betterlectio/android/ui/screens/more/MoreScreen.kt
items(AppearanceMode.entries.toList(), key = { it.name }) { mode ->
```

```859:859:app/src/main/java/dk/betterlectio/android/ui/screens/more/MoreScreen.kt
items(AppLanguage.entries.toList(), key = { it.name }) { lang ->
```

`AppearanceMode.SYSTEM` and `AppLanguage.SYSTEM` both produce key `"SYSTEM"` in the **same** LazyColumn.

**Excerpt:** `debug-logs/crashes/crash-03-system-key.txt`

---

## Load / functional failures (no process death)

### 1. Messages — Lectio rejects `type=liste`

**Every** attempt to load message folders failed during the session.

| Request | Result |
|---------|--------|
| `GET .../beskeder2.aspx?type=liste&mappeid=-40` | **302** → error page |
| `GET .../beskeder2.aspx?type=liste&mappeid=-70` | **302** → error page |
| `GET .../fejlhandled.aspx?title=Fejl&message=Ukendt%20parameter:%20liste` | **200** |

- Occurrences: **14×** mappeid `-40`, **8×** mappeid `-70`  
- Lectio error message (Danish): **“Ukendt parameter: liste”** (Unknown parameter: `liste`)  
- Total hits involving `fejlhandled`: **33** log lines  

**Impact:** Messages list fails to load (appears broken / empty / error), but app does not crash.

---

### 2. PostHog — empty API key (debug build)

Repeated throughout the session after every process start:

| Call | Error |
|------|--------|
| Remote config | `PostHogApiError(statusCode=404)` |
| Feature flags | `PostHogApiError(statusCode=401)` |
| Event batch flush | `API key is not valid: empty` → `PostHogApiError(statusCode=401)` |

Example request payload shows `"api_key":""`.

**Impact:** Analytics / flags / exception export to PostHog do not work in this debug build. Crashes were still visible in logcat.

---

### 3. Supabase schedule sync — RLS on `updates`

After successful Supabase auth (`SupabaseAuth: authentication successful`), schedule week sync often fails:

```text
W SupabaseScheduleService: Supabase schedule sync failed for week 2026-W28
W SupabaseScheduleService: PostgrestRestException:
    new row violates row-level security policy for table "updates"
Code: 42501
URL: .../rest/v1/lessons?...&on_conflict=lesson_key
Http Method: POST
```

- **At least 11** `Supabase schedule sync failed` lines  
- Weeks observed failing include W28, W26, W25, W24, W23, W22, W21, W15, W14, W16, …  
- Some weeks also logged `Synced week … (0 events)` successfully  

**Impact:** Local Lectio schedule still loads (SkemaNy 200); cloud sync for some weeks fails. Error text mentions table `"updates"` while the failing URL is `lessons` (worth clarifying in a fix pass).

---

## Other observations (lower severity)

| Observation | Notes |
|-------------|--------|
| MitID login | Succeeded: Unilogin → Lectio `forside.aspx`; `MitID auth success URL` |
| MitID WebView noise | `MitID WebView error (ignored if mid app-switch): http://burp/favicon.ico code=-1` (proxy/Burp leftover; ignored) |
| WebView Bluetooth | `BLUETOOTH_CONNECT permission is missing` / `getBluetoothAdapter()` (Chromium media; not crash-causing) |
| GC warning | `Cleared Reference was only reachable from finalizer` once on app process |
| Supabase auth | Magic-link path worked; session restored after restarts |
| Homework | `homework fetchStatuses returned 5 statuses` OK |
| Assignments | `OpgaverElev.aspx`, `ElevAflevering.aspx` → 200 |
| Student card | `digitaltStudiekort.aspx` → 200 |
| Schedule | `SkemaNy.aspx`, `FindSkema.aspx`, members prefetch → 200 |

---

## Navigation timeline (condensed)

```text
13:51:24  App start (debug install)
13:51:40  MitID / Unilogin WebView
13:52:17  Auth success → forside.aspx
13:52:20  Schedule load + Supabase auth OK
13:52:21  Messages → fejlhandled (Ukendt parameter: liste)  [repeats on each cold start]
13:52:22  Supabase schedule sync RLS fail (W28)
13:52:40  Homework OK
13:52:42  Assignments OK
13:52:46  Assignment detail OK
13:52:48  Student card OK
13:53:02  ★ CRASH 1 — Grades (duplicate Lazy key)
13:53:07  Process restart
13:53:19  ★ CRASH 2 — Studieplan (duplicate plan id key)
13:53:24  Process restart
13:54:07  ★ CRASH 3 — Settings (key "SYSTEM")
13:54:10  Process restart; schedule browse; more RLS fails
13:54:58  Activity detail (aktivitetforside2) OK
```

---

## Priority for future fixes (not done yet)

1. **P0 — LazyColumn keys (3 crashes, same class of bug)**  
   - Grades: key must be unique per row (index / internal id / more fields).  
   - Studieplan: do not use non-unique `id` (or de-dupe / index-suffix).  
   - Settings: prefix enum keys (`appearance-${name}`, `language-${name}`, …).

2. **P0 — Messages API**  
   - Stop sending `type=liste` (or use Lectio’s current query param).  
   - Validate against live Lectio HTML/API for folder list.

3. **P1 — Supabase RLS**  
   - Align client upsert with policies on `lessons` / `updates` for authenticated users.

4. **P2 — PostHog debug config**  
   - Inject API key for debug builds (or disable SDK when key empty).

---

## Artifacts

| Path | Description |
|------|-------------|
| `debug-logs/full-logcat.txt` | Full `adb logcat -v threadtime` capture |
| `debug-logs/crash-watch.txt` | Filtered watch stream (partial) |
| `debug-logs/crashes/crash-01-grades.txt` | Full stack crash 1 |
| `debug-logs/crashes/crash-02-studieplan.txt` | Full stack crash 2 |
| `debug-logs/crashes/crash-03-system-key.txt` | Full stack crash 3 |
| `debug-logs/session-info.txt` | Device / version snapshot at ready |
| `debug-logs/session-start.txt` / `session-end.txt` | Session timestamps |

---

## Security note

Raw logcat contains **session cookies** (partially redacted in app logs as `session=66QR…BA`) and at least one **full Supabase JWT** in a stack dump for `SupabaseScheduleService`. Do not commit `full-logcat.txt` to a public repo; treat as sensitive.

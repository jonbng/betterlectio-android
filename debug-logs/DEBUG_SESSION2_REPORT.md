# BetterLectio Android — Debug Session 2 Report

**Date:** 2026-07-16  
**Device:** Pixel 8 Pro (`husky`, Android 17) via wireless ADB (phone hotspot)  
**Build:** `1.0.0-debug` (versionCode 7, `DEBUGGABLE`)  
**Package:** `dk.betterlectio.android`  
**App PID:** `24615` for the entire session (no process death)  
**Window:** ~14:07:06 – 14:10:31 CEST  
**Raw log:** `debug-logs/full-logcat-session2.txt` (~25k lines)

> Observation only. Compared against session 1 (`DEBUG_SESSION_REPORT.md`).

---

## Summary

| Severity | Session 1 | Session 2 |
|----------|-----------|-----------|
| Process crashes (FATAL) | **3** | **0** |
| Messages `type=liste` → fejlhandled | Yes (many) | **None** |
| Grades LazyColumn crash | Yes | **Survived** (opened twice) |
| Studieplan LazyColumn crash | Yes | **Survived** |
| Settings `"SYSTEM"` key crash | Yes | Not re-triggered / no crash |
| PostHog empty API key | Yes | **Fixed** (key present, events flushed) |
| Supabase schedule sync | RLS on `updates` | **Different error:** duplicate `lesson_key` in upsert |

**Bottom line:** The three crash classes and the messages list failure from session 1 look fixed. One remaining soft failure is Supabase schedule upsert de-duplication.

---

## Process crashes

**None.**  
No `FATAL EXCEPTION` lines. Same PID (`24615`) from launch to end.

---

## Previously broken areas — re-check

### Messages

- Paths used: `beskeder2.aspx?mappeid=-40`, `?mappeid=-70`, `?type=nybesked`  
- Pattern: GET folder → POST folder (iOS-style); compose via `type=nybesked`  
- HTTP: **200** on list GET/POST  
- **No** `type=liste`, **no** `fejlhandled`  
- PostHog: `message_composed_sent` (×2) — compose path exercised  

### Grades

- `grades_viewed` + `grade_report.aspx` → **200** at 14:08:13 and again ~14:09:31–33  
- Process stayed alive  

### Studieplan

- `studieplan.aspx` → **200** at 14:08:21  
- Process stayed alive  

### Other surfaces hit (no crash)

| Area | Evidence |
|------|----------|
| Schedule | `SkemaNy.aspx`, `FindSkemaAdv.aspx` |
| Absence | `fravaerelev.aspx`, causes |
| Student card | `digitaltStudiekort.aspx` |
| Assignments | `ElevAflevering.aspx`, `ExerciseFileGet.aspx` |
| Activity | `aktivitetforside2.aspx` |
| Private appointment | `privat_aftale.aspx` |
| Exam groups | `proevehold.aspx` |

---

## Remaining / new issues

### 1. Supabase schedule sync — duplicate `lesson_key` in one upsert (P1)

| Field | Value |
|-------|--------|
| Time | 14:10:15.612 |
| Week | `2026-W20` |
| Code | `21000` |
| Exception | `PostgrestRestException` |

**Message:**

```text
ON CONFLICT DO UPDATE command cannot affect row a second time
Hint: Ensure that no rows proposed for insertion within the same command
have duplicate constrained values.
```

**URL:** `POST .../rest/v1/lessons?...&on_conflict=lesson_key`

**Impact:** Soft-fail only (`Timber.w`); local Lectio schedule still works. Cloud sync for that week fails.

**Likely cause:** `SupabaseScheduleService.syncWeek` builds a payload with two events that resolve to the same `ScheduleIdentity.lessonKey(...)` (e.g. same start/title collision). Postgres rejects duplicate conflict targets in a single `UPSERT`.

**Not seen this session:** RLS on table `updates` (session 1 error). May be fixed server-side or not hit on this week.

**Suggested fix (for later):**  
`payload.distinctBy { it.lessonKey }` (or merge duplicates) before upsert.

---

### 2. PostHog — session 1 leftover only

- Early log lines from **old PID 21558** still show empty-key 401 (previous process).  
- Current PID **24615** batches include real key `phc_…` and events (`grades_viewed`, `absence_viewed`, `message_composed_sent`).  

No action needed for the current build.

---

### 3. Noise (ignore)

| Signal | Notes |
|--------|--------|
| SLF4J no providers | One-time at startup |
| `Cleared Reference was only reachable from finalizer` | GC noise |
| `requestCursorUpdates on inactive InputConnection` | IME |

---

## Session 1 vs Session 2 (fixes verification)

| Issue (session 1) | Session 2 result |
|-------------------|------------------|
| LazyColumn grades key | Fixed — grades opened safely |
| LazyColumn studieplan key | Fixed — studieplan opened safely |
| LazyColumn settings `SYSTEM` | No crash observed |
| Messages `type=liste` | Fixed — mappeid + POST only |
| PostHog empty key | Fixed — analytics key in use |
| Supabase RLS `updates` | Not reproduced; **new** duplicate-key upsert error |

---

## Artifacts

| Path | Description |
|------|-------------|
| `debug-logs/full-logcat-session2.txt` | Full logcat for this session |
| `debug-logs/crash-watch-session2.txt` | Filtered watch stream |
| `debug-logs/session2-start.txt` / `session2-end.txt` | Timestamps |
| `debug-logs/DEBUG_SESSION_REPORT.md` | Session 1 (crashes + baseline) |
| `debug-logs/DEBUG_SESSION2_REPORT.md` | This file |

## Security note

Logcat may contain session cookies and Supabase JWTs — do not commit raw logs publicly.

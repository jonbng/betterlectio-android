# PostHog setup report (low-volume observability mode)

PostHog is initialized in `BetterLectioApp.onCreate()` in explicit, low-volume mode. The app emits one `app_active` event per 30-minute inactivity-defined session, one `app_load_completed` startup timing per process, all observed failures, and successful `load_completed` timings for a stable 10% device cohort. Automatic screen, replay, click, deep-link, survey, and feature-flag capture remain disabled. Automatic exceptions are retained, signature-deduplicated, and capped at twenty distinct errors per process.

Authenticated people use the cross-platform `lectio:<studentId>` distinct ID and carry `platform`, `last_platform`, `app_version`, and `app_build`. A one-time alias joins the previous raw mobile ID to the canonical person. `PersonProfiles.IDENTIFIED_ONLY` avoids anonymous profiles.

## Historical event callsites (dropped unless allowlisted above)

| Event name | Description | File |
|---|---|---|
| `login_completed` | User successfully logged in via MitID WebView | `core/lectio/auth/AuthSessionInstaller.kt` |
| `login_with_password_completed` | User successfully logged in via username/password | `ui/auth/LoginViewModel.kt` |
| `login_failed` | User login attempt failed | `ui/auth/LoginViewModel.kt` |
| `demo_entered` | User entered the app in demo mode | `core/lectio/auth/AuthSessionInstaller.kt` |
| `logged_out` | User explicitly logged out | `core/lectio/auth/AuthSessionInstaller.kt` |
| `lesson_detail_viewed` | User tapped a lesson to view its details | `ui/screens/schedule/ScheduleViewModel.kt` |
| `private_event_created` | User created a personal private schedule event | `ui/screens/schedule/ScheduleViewModel.kt` |
| `private_event_deleted` | User deleted a private event from the schedule | `ui/screens/schedule/ScheduleViewModel.kt` |
| `message_thread_opened` | User opened a message thread | `ui/screens/messages/MessagesViewModel.kt` |
| `message_reply_sent` | User sent a reply in a message thread | `ui/screens/messages/MessagesViewModel.kt` |
| `message_composed_sent` | User composed and sent a new message | `ui/screens/messages/MessagesViewModel.kt` |
| `assignment_detail_viewed` | User opened an assignment detail | `ui/screens/assignments/AssignmentsViewModel.kt` |
| `grades_viewed` | User navigated to the grades section | `ui/screens/more/MoreViewModel.kt` |
| `absence_viewed` | User navigated to the absence overview | `ui/screens/more/MoreViewModel.kt` |
| `absence_cause_updated` | User updated the cause for an absence entry | `ui/screens/more/MoreViewModel.kt` |

## Files changed

- `gradle/libs.versions.toml` — added `posthog = "3.31.0"` version and `posthog-android` library entry
- `app/build.gradle.kts` — added `libs.posthog.android` dependency, `POSTHOG_API_KEY` and `POSTHOG_HOST` BuildConfig fields read from `local.properties`
- `local.properties` — added `posthog.apiKey` and `posthog.host` (gitignored)
- `app/.../BetterLectioApp.kt` — initializes PostHog in explicit-only mode and enforces the two-event egress allowlist
- `app/.../MainActivity.kt` — calls `PostHog.identify()` on cold start for already-authenticated users
- `app/.../AuthSessionInstaller.kt` — calls `PostHog.identify()` + `login_completed` on MitID login; `demo_entered` on demo entry; `logged_out` + `PostHog.reset()` on logout
- `app/.../LoginViewModel.kt` — captures `login_with_password_completed` and `login_failed` with login method property
- `app/.../ScheduleViewModel.kt` — captures `lesson_detail_viewed`, `private_event_created`, `private_event_deleted`
- `app/.../MessagesViewModel.kt` — captures `message_thread_opened`, `message_reply_sent`, `message_composed_sent`
- `app/.../AssignmentsViewModel.kt` — captures `assignment_detail_viewed` with assignment status property
- `app/.../MoreViewModel.kt` — captures `grades_viewed`, `absence_viewed`, `absence_cause_updated`

## Next steps

We've built a dashboard with five insights to monitor user behaviour based on the events above:

- [Analytics basics (wizard) — Dashboard](https://eu.posthog.com/project/145688/dashboard/809909)
- [Login events](https://eu.posthog.com/project/145688/insights/59PdmaEh) — daily logins broken down by method (MitID, password, demo)
- [Login → Lesson view funnel](https://eu.posthog.com/project/145688/insights/89iibf38) — conversion from login to first lesson detail view
- [Messaging activity](https://eu.posthog.com/project/145688/insights/scbrzpVV) — weekly threads opened, replies sent, new messages composed
- [Feature engagement by section](https://eu.posthog.com/project/145688/insights/GoLaBBxq) — schedule, assignments, grades, and absence usage side by side
- [Schedule & content creation](https://eu.posthog.com/project/145688/insights/V1e0lWMY) — private events created/deleted and absence causes updated

## Verify before merging

- [ ] Run a full production build (the wizard only verified the files it touched) and fix any lint or type errors introduced by the generated code.
- [ ] Run the test suite — call sites that were rewritten or instrumented may need updated mocks or fixtures.
- [ ] Add `posthog.apiKey` and `posthog.host` to your project's `local.properties.example` (or equivalent onboarding docs) so collaborators know what to set.
- [ ] Confirm the returning-visitor path also calls `identify` — a handler that only identifies on fresh login can leave returning sessions on anonymous distinct IDs. (Already implemented in `MainActivity.onCreate`, but verify it fires correctly by checking PostHog for identified events on a cold-start session.)

### Agent skill

We've left an agent skill folder in your project at `.claude/skills/integration-android/`. You can use this context for further agent development when using Claude Code. This will help ensure the model provides the most up-to-date approaches for integrating PostHog.

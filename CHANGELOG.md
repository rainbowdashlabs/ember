# Changelog

## v26.7.1

### New Features

#### Personal Feed Overhaul (iCal, Atom, RSS)

The biggest piece of this release: every member's personal calendar and notification feed got a top-to-bottom rewrite so feed readers like Thunderbird, Apple Calendar, NetNewsWire, Feedly, and Reeder finally surface the same context that the web UI does — and stations can monitor exactly how their feeds are being consumed.

- **Guardian-aware visibility** — the iCal feed now hides events only when *every* relevant registration is `DECLINED`/`DENIED`. A guardian whose child is going still sees the event; a member who declined themselves but whose managed members are going still sees it. Events with expired registration deadlines and no active registrations are dropped to keep the calendar clean.
- **Rich iCal event descriptions** — every VEVENT now carries category, recurrence label, registration deadline/limit/status, custom event field values, per-managed-member registration breakdown, accepted-count over limit, station-timezone-aware timestamps, and a trailing web link. Cancelled events get a localised `[Cancelled]` prefix in `SUMMARY` plus the standard `STATUS:CANCELLED` so clients strikethrough or hide them; every event also emits the RFC-5545 `URL` property for one-tap web open.
- **New `LOCATION` event field type** — a dedicated `EventFieldType.LOCATION` variant that feeds the standard iCal `LOCATION` property so phones and calendar apps turn it into a tap-to-navigate map link.
- **Rich notification feed entries** — RSS/Atom entries now carry a semantic HTML body (definition list, status badges with Unicode markers `✓ ✗ … ↶`, action button at WCAG 2.5.5 tap-target size), a plain-text fallback for readers that strip HTML, the notification's actor as the entry author, two categories per entry (a localised label for humans plus the stable enum name for scripted filters across locales), and an embedded image with meaningful alt text where applicable.
- **Event context in notification entries** — `NEW_EVENT`, `EVENT_REMINDER`, `EVENT_CANCELLED`, and `EVENT_REGISTRATION_STATUS` entries now also surface the event's localised start/end timestamps and every non-empty custom field value (location, meeting point, notes, …) in the details block, so feed readers carry the same information as the event-detail page without an extra click. News and comment previews (`NEW_NEWS`, `NEWS_COMMENT`) continue to surface the existing description snippet.
- **Rich title format** — feed entry titles now read `News: Q3 schedule published`, `Procurement requested: Hose 25m`, `Lost & Found: Blue jacket`, etc. instead of the bare category. Status-bearing types (`EVENT_REGISTRATION_STATUS`, `EXCHANGE_STATUS_CHANGE`, `LENDING_STATUS_CHANGE`) include the localised status with a Unicode marker (`Registration ✓ Accepted: Open Training`). Plural-bearing types (`NEW_EVENTS_BATCH`, `EVENT_REMINDER`, `REGISTRATION_DEADLINE_EXPIRED`) route via `.one`/`.other`. Long fragments are truncated on a word boundary with an ellipsis so inbox rows stay scannable.
- **Same-day event range merge** — events whose start and end fall on the same calendar day collapse into one `When: 15 Sep 17:00 – 19:00` row instead of separate `Starts:` / `Ends:` rows. Cross-day events keep the split format to preserve the date on each side.
- **Live context lookups** — feed entries enrich themselves with information loaded from the relevant service at render time, so handlers don't have to pre-bake everything into params:
  - `LOST_AND_FOUND_NEW` / `_CLAIMED` — find date (always) and claim date (claimed variant) via `LostAndFoundService`.
  - `LENDING_NEW_REQUEST` / `_STATUS_CHANGE` / `_NEW_MESSAGE` — range-merged `Needed: 5 Oct – 7 Oct` row via `LendingService`.
  - `PROCUREMENT_REQUESTED` / `_FULFILLED` and `EXCHANGE_NEW_REQUEST` / `_STATUS_CHANGE` — inventory ownership flow (`Organisation-owned` / `Member-owned` / `Mixed`) via `InventoryService`.
  - `BOARD_TICKET_UPDATE` — ticket title, assignee, priority via `BoardTicketService`.
  - `PROCEDURE_ASSIGNED` / `_RESOLVED` / `_REOPENED` / `_ITEM_CHECKED` — progress (`Progress: 5 of 12 items`) via `ProcedureService`.
  - `STORAGE_WARNING` — top-5 category breakdown as a bulleted child list via `StorageQuotaService`. Categories localised in their own `storageCategory` bundle namespace.
  Every enrichment silently no-ops on missing id or stale reference; a deleted entity never tanks the feed render.
- **Richer params (Phase C)** — `MemberAddedToGroup` adds `addedByName` so the body reads `By: Alice Müller`. `CommentMention` adds `preview` (first 100 chars of the comment) so feed readers see the surrounding context without opening the source. `NewEventsBatch` adds `firstEventDate` so the body surfaces the first occurrence.
- **Embedded lost-and-found images** — new token-scoped `/public/feed/{token}/lost-and-found/{id}/image` route lets unauthenticated feed readers fetch images without exposing the rest of the API. Cross-station requests look identical to missing items so the token can't be used to probe foreign stations. MediaRSS `<media:thumbnail>` is emitted alongside the inline `<img>` for readers that render previews.
- **Atom is the recommended format** — Atom is featured prominently in the user-facing feed settings with a "Recommended" badge and an inline explainer covering author, categories, and MediaRSS thumbnails. RSS collapses into a `<details>` "emergency fallback" for clients that don't speak Atom. iCal gets its own featured card explaining the calendar-subscription use case.
- **Verbosity presets** — three radio buttons on the feed settings page (Rich / Compact / Minimal) rewrite the copied URL with the matching `verbose` / `images` query parameters. The selection persists in `localStorage`; no backend round-trip. Rich is the default.
- **Conditional GET (ETag + If-Modified-Since)** — every feed endpoint computes a short SHA-256 fingerprint over the freshness inputs (event/registration/notification mutation times, locale, query params) and answers a matching `If-None-Match` or `If-Modified-Since` with `304 Not Modified`. Aggressive pollers (Thunderbird default ~100 min, RSS-Bridge much shorter) drop to near-zero bandwidth on unchanged feeds.
- **Leaky-bucket rate limiting** — feed endpoints now enforce a per-token leaky bucket with 10-request burst capacity and 5/minute sustained refill. Realistic burst traffic passes cleanly while runaway readers get a `429` with `Retry-After`. The token-scoped image endpoint is intentionally exempt. The bucket is implemented in a new reusable `dev.chojo.ember.util.LeakyBucket` utility so future features can drop in the same shape.
- **Body size caps** — RSS/Atom truncates to the last 100 notifications; iCal limits non-recurring events to a `[now − 7 days, now + 1 year]` window, keeping payloads bounded even on noisy stations.
- **Privacy hardening** — `Referrer-Policy: no-referrer` and `X-Robots-Tag: noindex` are emitted on every response (200, 304, 429, 404) so the feed token can never leak via `Referer` and leaked URLs can't be picked up by search engines. The "Regenerate token" and "Revoke token" buttons now show a confirmation modal that warns the action breaks every subscribed reader on the spot.
- **Per-entry failure isolation** — one malformed notification or event no longer 500s the entire feed. Failures are logged and skipped; the rest of the feed renders.
- **Accessibility** — feed HTML uses semantic `<dl>/<dt>/<dd>`, `lang` + `dir="auto"` for proper screen-reader pronunciation, persistent `text-decoration:underline` on links (most readers strip `:hover`), 44px tap targets on action buttons, and Unicode status symbols so meaning survives monochrome rendering and colour-blindness. WCAG AA contrast budget documented in the concept.

#### Notification Pipeline Improvements

- **Aggregate batch event notifications** — bulk-created events now produce one `NEW_EVENTS_BATCH` notification per recipient instead of one per row. A new `EventsBatchCreated` domain event drives the aggregation; `BatchEventService` publishes it after the bulk create.
- **Complete i18n coverage** — every `NotificationType` now has a localised category label and message in both bundles (English and German). Previously 12 of 29 types were falling back to bare keys.
- **Plural-split messages** — `newEventsBatch`, `eventReminder`, and `registrationDeadlineExpired` now route to `.one` / `.other` variants based on integer count/daysBefore/pendingCount params so the singular form reads correctly across locales. The email digest subject ("X new notifications") uses the same mechanism.
- **Required notification links** — every public `notify*` method on `NotificationService` now enforces that the `NotificationData` carries a `NotificationLink`. A future handler that forgets one fails fast with `IllegalArgumentException` instead of producing a notification that can't be opened.

#### Recurring-Event Date Context

Reminders for recurring events used to deep-link to `/station/events/{id}` regardless of which occurrence the reminder was about. Comments on recurring events merged across every occurrence. Both are now occurrence-aware end-to-end.

- **Date-aware deep links** — new `event-detail-date` route template (`/station/events/{id}/{date}`) carries the occurrence date as a path segment. `EventReminderChecker` emits the date so weekly reminders land on the right week. The frontend route accepts the optional `:date` param.
- **Detail view bound to a single date** — the event detail view now derives its start/end timestamps from the URL path date (or, when no path date is provided, from the next occurrence for recurring events / the event's start for one-time events). The redundant "Next date" container is gone; the date is shown directly in the general info as the `Start` / `End` rows. Absences are loaded for the bound date.
- **Date-aware navigation across the app** — the upcoming-events page, dashboard's upcoming-events panel, registrations panel, and admin event-by-category list all route recurring events to `event-detail-date` with the relevant occurrence date so members never land on the wrong instance.
- **List ↔ calendar toggle on `/station/events/upcoming`** — new month-grid view, always 7 columns and 5 or 6 rows depending on whether the last week would be entirely next-month dates (no all-ghost trailing row). Occurrences are computed client-side from the full event list so any month can be browsed without an extra fetch. The cell chips deep-link via `event-detail-date` for recurring events and `event-detail` for one-time events. The user's choice between list and calendar persists in `localStorage` so the same view returns on the next visit.
- **Mobile-tight calendar layout** — calendar wrapper drops to 4 px outer padding on mobile (vs. 16 px on `sm+`), cell padding shrinks to 2 px, and the new `EventChipButton` hard-clips long event names without an ellipsis so a 35-px-wide mobile cell uses every available character. Reclaims roughly 60 px of horizontal space on a 360-px viewport (~21 % wider cells).
- **Per-occurrence comment threads** — `event_comment` gains a nullable `event_date` column. One-time events and whole-event comments keep `event_date = NULL`; comments on a specific occurrence of a recurring event carry the ISO date. `CommentSection.vue` honours the focused date — list filters and new comments stay scoped to that occurrence. The `/events/{id}/comments` GET endpoint accepts an optional `?date=` query parameter for filtering.
- **Federation parity** — the `RemoteCommentRequest` payload across federation now carries `eventDate`. Older peers that omit it map to `null` (whole-event comment) on the receiver, preserving backwards compatibility.
- **Typed date fields** — `EventCommentRoutes.CreateCommentRequest.eventDate`, `CommentResponse.eventDate`, and `NotificationParams.EventReminder.eventDate` are now `LocalDate` instead of `String`. Jackson serialises as ISO `yyyy-MM-dd`. The manual `LocalDate.parse(...)` round-trips disappear and date formatting in the feed renderer can finally honour locale.

#### Feed Telemetry (Admin)

A new admin observability panel under "Monitoring → Feed-Telemetrie" charts feed usage and performance.

- **Persisted daily metrics** — new `feed_metric_daily` table stores one row per `(day, type, status)` with request count, total/total entries, and a five-bucket duration histogram (<50ms, 50–200ms, 200ms–1s, 1–5s, ≥5s). Upserted on every feed render.
- **Global user-agent aggregate** — new `feed_user_agent_stat` table tracks distinct reader user-agents with request counts and first/last-seen timestamps. **No per-token attribution by design** — a station admin with DB access cannot derive which member uses which reader.
- **Async, non-blocking writes** — telemetry persists on a dedicated executor so a slow DB never delays a feed render. All writes are best-effort with logged warnings on failure.
- **Configurable retention** — new top-level `metrics` config block with two windows: `requestStatsRetentionDays` (default 3, applies to the existing `api_request_log`) and `feedStatsRetentionDays` (default 90, applies to the new feed tables). A daily prune job enforces both.
- **Admin dashboard** — four summary cards (total requests, fully rendered, 304 cache hits, average render duration), three ECharts diagrams (requests-by-type line chart, latency histogram bar chart with green→red colour coding, daily volume bar chart), a status-code breakdown table, and the global reader leaderboard.
- **Help center article** — full article explaining every chart, the histogram bucket colour code, the relevant HTTP status codes (200/304/429/500), and the user-agent leaderboard's privacy posture.

#### Helpers and Reusable Components

- **`LeakyBucket` utility** — extracted from the feed rate limiter into `dev.chojo.ember.util.LeakyBucket` so future features that need per-key rate limiting can reuse it.
- **`HelpCenterHint` Vue component** — small standalone affordance using the animated ember mascot (FAQ variant with auto-blink + gaze) linking to a named help-center route. Used in the feed settings card and the admin Feed Metrics panel; can be dropped in anywhere.

### Improvements

- **Bulk-friendly registration lookups** — new `EventRepository.findRegistrationsByMembers(Collection<Integer>)` loads an owner's + their managed members' registrations in one query instead of N. Cuts query count on the iCal feed for guardian accounts.
- **Event modification timestamp** — `station_event.updated_at` column + tracking through every UPDATE statement so the iCal ETag invalidates on any event edit.
- **`StationEvent` URL property in iCal** — explicit RFC-5545 `URL` property in every VEVENT for one-tap "open on website" in calendar clients.

### Frontend

- **`helpcenter-admin` layout** — the admin section pages now use a dedicated layout instead of the generic `helpcenter` layout, decoupling the two sidebars.

### Internal / Infrastructure

- **Frontend lint infrastructure fixes** — `scripts/lint-conventions.mjs` had two argument-order bugs (rule 3b "raw badge span" and rule 6b "inline ref<> object type") that swapped the category and file/line arguments, so the lint output showed `error Raw element usage: 149` instead of `error src/views/.../File.vue:149: <span> with rounded-full + padding ...`. The actual offending file (a "Recommended" pill on the feed settings card) was effectively invisible. Fixed both call sites; the only remaining error after the fix was an obvious `<span>` to `PrimaryBadge` swap.
- **`npm run build` now runs the linters** — `scripts/build.mjs` previously spawned `nuxi build` and exited 0 on success without ever invoking `lint-icons` / `lint-conventions` / `lint-helpcenter` / `lint-locales`. Per the project spec in `CLAUDE.md` (and to match `build:spa`), the build script now runs the four linters in sequence before `nuxi build` and exits non-zero on the first failure. Convention violations can no longer slip past a green build.
- **Schema migrations** — patch_11 adds `station_event.updated_at`. patch_12 introduces `feed_metric_daily`, `feed_user_agent_stat`, and `event_comment.event_date`.
- **rome-modules dependency** — pulled in for MediaRSS support on RSS/Atom feeds.
- **Test infrastructure** — new `UserFeedRoutesIntegrationTest` exercises the route handlers end-to-end (conditional GET roundtrips, rate-limit short-circuit, privacy headers on every status path) via a hand-rolled `RecordingContext` Javalin stub. `LeakyBucketTest` covers the algorithm in isolation. `FeedFingerprintTest`, `FeedRateLimiterTest`, `IcalEventRendererTest`, `NotificationFeedRendererTest`, and `FeedMetricsRepositoryTest` round out the new feed surface. Existing `NotificationServiceTest` gained pluralisation coverage and the `notifyRejectsDataWithoutLink` regression for the link guard.

### Bug fixes

- **Notification feed coverage** — every `NotificationType` switch branch in `NotificationService.resolveDetail` and `resolveFeedBody` is now exercised by tests; previously several types fell through to default rendering at runtime.
- **Lost-and-found notification deep links** — `LostAndFoundService.create` and `.claim` now pass the item id in the notification link's `routeParams`. Previously the link was issued without an id, which silently broke both the deep link (fallback to dashboard) and the token-scoped image lookup (no embedded image).
- **Board ticket notification deep links** — `BoardTicketChanged` now carries `boardKey` + `ticketNumber` so the `ticket-detail` route (`/station/boards/{boardKey}/tickets/{ticketNumber}`) actually resolves. Previously the link carried numeric `boardId` / `ticketId` and silently fell back to the dashboard because `ticket-detail` was unregistered in `NotificationService.ROUTE_PATHS`.
- **News notification preview** — `NewsCreatedHandler` previously passed `null` as the preview field, so every `NEW_NEWS` notification reached the atom feed without any article content in the body. The handler now receives a markdown-stripped preview computed by `NewsService.create` and the feed body surfaces it.
- **Event notification body too sparse** — `EventCreatedHandler` cut the description at a fixed 80 chars with a `"..."` ellipsis even when the body had room for more, and the feed enrichment only loaded start/end + custom fields. The handler now passes the full description through (word-boundary truncation lives in the renderer) and `enrichWithEventContext` additionally surfaces the recurrence label, registration deadline, and attendee limit when available.
- **Demo notifications use real services** — `DemoNotificationSeeder` previously hand-rolled notifications with stale link metadata (e.g. `PROCUREMENT_REQUESTED` linked to the dashboard instead of the procurement page, `EXCHANGE_NEW_REQUEST` link missing the inventory id). The seeder now relies on the other demo seeders' service calls to produce notifications organically — `NewsService`, `EventService`, `ExchangeService`, `ProcurementService`, `LendingService`, etc. — and additionally seeds one showcase notification of every `NotificationType` for the demo admin so all 29 categories can be reviewed at a glance. New `DemoLostAndFoundSeeder` calls `LostAndFoundService.create`/`.claim` so the demo fires real `LOST_AND_FOUND_NEW` / `LOST_AND_FOUND_CLAIMED` notifications and the showcase context can deep-link to a real item id. The showcase task runs after the parallel-seed block so live-lookup enrichment (board ticket title / assignee / priority, procedure progress, lending date range, inventory ownership, lost-and-found dates) hits real entities seeded by the other modules.
- **Atom `<summary>` / `<content>` swap** — ROME's `entry.setDescription` maps to Atom's `<summary>` and `setContents` to `<content>`, but we had them reversed: the rich HTML body lived in `<summary>` (too much for inbox rows) and the multi-line plain text lived in `<content>` (HTML readers ignored it). Swapped so the short headline goes in `<summary>` (one-line preview for inbox rows) and the rich HTML lives in `<content type="html">` (the full body on expand).
- **Duplicate-`term` categories disambiguated** — entries used to expose two `<category term="…"/>` elements with no `scheme` attribute (one localised, one raw enum). Feed readers that collapse same-`term` entries dropped the localised one and showed the technical enum name. The enum-name category now carries `scheme="urn:ember:notification-type"` so readers preserve both and the localised one stays primary in the row.

## v26.7.0

### New Features

#### Storage Monitoring & Quota System
- **Per-station storage tracking** — tracks file storage usage across 5 categories: KB files, board attachments, page images, avatars, and other images
- **Quota enforcement** — configurable per-category and total storage limits with rejection on exceed (HTTP 413)
- **Quota presets** — reusable named profiles (e.g. Small, Standard, Premium) that can be applied to stations in bulk
- **Per-station overrides** — stations can have custom quotas or use instance defaults from config
- **Warning notifications** — domain event notifies station managers when usage crosses the configurable threshold (default 80%)
- **Automatic reconciliation** — background job recalculates actual usage from DB and filesystem on startup and at configurable intervals
- **Presentation compression** — lossless ZIP recompression of PPTX/ODP files, saving 10-30% for files above the threshold
- **Admin dashboard** — storage overview with summary stats, stacked bar charts per station, category pie chart, sortable station table with status badges and preset assignment
- **Station storage view** — read-only usage view for station managers with bar chart and per-category breakdown
- **Preset management** — CRUD UI with size inputs (number + MiB/GiB/TiB dropdown), apply to multiple stations, delete with confirmation
- **Config** — `storage` section in config.yaml with defaults for all quotas, compression, warning threshold, and reconciliation interval
- **Help center** — help articles for both admin and station storage views

#### Federation Version Broadcasting
- **Startup broadcast** — on boot, pings all remote federation partners to exchange version information
- **Version ping endpoint** — new `/remote/federation/ping` returns the current federation version hash
- **Version backfill** — partners created before version tracking get updated on startup
- **Version at creation** — new partners are created with the current federation version instead of placeholder '0'
- **DTO tracking** — federation version hash now includes inner record DTOs from FederationRemoteRoutes, FederationRoutes, LendingRoutes, and BoardRoutes

#### Public Pages (Layout Editor)
- **Page builder** — stations can create public pages using a lightweight layout editor inspired by WordPress/Elementor
- **Row-based layout** — pages are built from horizontal rows, each containing 1-4 columns with free-form percentage widths
- **Content types** — cells support rich markdown (WYSIWYG TipTap editor), images (upload with fit/sizing), and videos (YouTube embeds or direct URLs)
- **Responsive design** — horizontal rows automatically stack vertically on mobile
- **Page hierarchy** — pages support up to 3 levels of nesting with nested URL paths (e.g., `/page/about/team`)
- **Landing page** — one page can be designated as the station landing page, shown first in the sidebar
- **Station slug** — stations get a human-readable URL slug (auto-generated from name, editable) as alternative to UUID
- **SEO metadata** — per-page meta description and OG image, with auto-generation from content
- **Markdown rendering** — server-side commonmark rendering for public pages
- **Station theming** — public pages display the station's configured theme (colors, feel)
- **Image management** — per-page image upload (max 5 MB), orphaned images auto-cleaned on save
- **Copy/cut/paste** — clipboard for rows and cells with paste buttons between rows
- **Column controls** — visual column split buttons, swap button between columns, free-form resize handles
- **Move up/down** — row reordering via buttons
- **Preview mode** — toggle between edit and preview in the editor
- **Page duplication** — duplicate pages with full row/cell tree
- **Publish/unpublish** — PAGE_MANAGER permission for publishing, unpublished parents hide children
- **Help center** — article explaining page management
- **Demo data** — 4 sample pages (Willkommen, Über uns, Unser Team, Ausrüstung, Mitmachen) with hierarchy

#### Station Public URL
- **Public slug** — stations have a customizable URL slug (e.g., `/public/station/jugendfeuerwehr-musterstadt`)
- **Auto-generated** — slugs created from station name on creation, with dedup
- **UUID redirect** — UUID-based URLs automatically redirect to the slug version
- **Discovery links** — station discovery uses slugs for cleaner URLs
- **Settings UI** — editable slug in federation settings with duplicate detection

#### Public Waitlist Registration
- **Public waitlists** — per-waitlist `isPublic` toggle allows external registration without login
- **Per-field visibility** — each waitlist field can be marked as public or hidden from the registration form
- **Email verification** — registrants receive a verification email; token expires after 24 hours
- **Pending approval** — verified registrations get `PENDING` status, requiring WAITLIST_EDIT approval
- **Approve/reject** — expandable pending entries in the waitlist detail view with approve/reject actions showing full registration details
- **Notifications** — WAITLIST_EDIT users are notified when a new public registration arrives
- **Station toggle** — `publicWaitlistEnabled` station setting controls whether public waitlists are available
- **Public registration page** — list selection, form with public fields, guardian inputs, and email verification flow
- **Verification page** — standalone page at `/public/waitlist/verify/{token}` confirming email
- **Public sidebar** — waitlist link in the public station sidebar when enabled
- **Guardian name split** — guardians now have separate firstname + lastname fields for direct account conversion

#### Public Blog
- **Blog entries** — news articles can be flagged as blog posts via a toggle in the editor
- **Blog badge** — blog entries show a "Blog" badge in the internal news list
- **Public blog page** — blog list with title, excerpt, author, and date; detail view with full HTML content
- **Landing fallback** — blog becomes the default landing page when no custom page is set
- **Station toggle** — `publicBlogEnabled` setting controls whether the blog is available
- **Public sidebar** — blog link appears after landing page, before calendar

#### Station Settings UX
- **Reactive save** — federation settings now auto-save on change (debounced 600ms) instead of requiring a save button
- **Save indicator** — shows "Speichern…" spinner and "Gespeichert" checkmark

#### Knowledge Base: Presentation Support
- **Presentation uploads** — upload PowerPoint (.pptx, .ppt) and OpenDocument (.odp) presentations to the knowledge base
- **Automatic PDF conversion** — presentations are converted to PDF server-side via LibreOffice headless for in-browser viewing
- **Async conversion** — upload returns immediately, conversion runs in the background with status tracking (pending/success/failed)
- **Presentation mode** — full-screen slide-by-slide viewer for PDFs and presentations using pdf.js, with keyboard/click/swipe navigation and slide counter
- **Auto-hiding controls** — presentation mode header and navigation buttons fade out after inactivity for a clean viewing experience
- **Original file download** — download the original presentation file from the file detail view
- **Re-upload** — replace the original presentation and trigger reconversion

#### Procedures (Abläufe)
- **New module: Procedures** — per-user checklists for structured processes (onboarding, equipment handout, etc.)
- **Templates** — reusable procedure blueprints with items and dependency chains, managed by PROCEDURE_MANAGER
- **Procedure instances** — created ad-hoc or from templates, with editable items before submission
- **Assignees** — assign procedures to one or more members with member picker
- **Item dependencies** — items can depend on other items (DAG), blocked items shown with lock icon
- **Public/private visibility** — procedures and individual items can be marked private (only visible to PROCEDURE_EDIT users)
- **User-assigned items** — items can be flagged as checkable by assignees; other items require PROCEDURE_EDIT permission
- **Resolve/reopen** — procedures can be resolved at any time and reopened if needed
- **Notifications** — domain events for assignment, resolution, reopening, and item completion
- **Sidebar integration** — badge shows open procedures; visible to all users with assigned procedures
- **Demo data** — 2 templates (onboarding, equipment handout) and 4 sample procedures with mixed states
- **Help center** — overview article for the procedures module

#### Server-Side Rendering
- **Nuxt 3 SSR migration** — frontend migrated from Vue SPA to Nuxt 3 with hybrid rendering: SSR for public pages, ISR for help center, SPA for authenticated station/admin views
- **Two-container deployment** — separate backend (Java) and frontend (Nuxt) Docker images for independent scaling and deployment

#### SEO
- **Dynamic sitemap** — `@nuxtjs/sitemap` generates `/sitemap.xml` with static pages and dynamic station URLs fetched from the discovery API
- **robots.txt** — crawl rules allowing public pages (`/discovery`, `/public/`, `/helpcenter/`) and blocking private routes (`/station/`, `/admin/`, `/api/`)
- **Canonical URLs** — `useCanonical` composable adds `<link rel="canonical">` and `og:url` to all public pages
- **Open Graph & Twitter cards** — all public pages include OG tags (title, description, type, image, locale, site_name) and Twitter card meta
- **Structured data (JSON-LD)** — `SoftwareApplication` on homepage, `Organization` on station pages, `Event` on public calendar (enables rich results), `BreadcrumbList` on KB navigation
- **SearchAction schema** — sitelinks search box on discovery page
- **Google optimizations** — `max-image-preview:large`, `max-snippet:-1`, `max-video-preview:-1` for richer search result previews
- **Google Search Console** — optional `NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION` env var for site verification
- **Help center SEO** — `HelpArticle` component auto-generates meta description and OG tags from article title/subtitle for all 142 help pages

#### Data Tracking System
- **`data_tracking.json`** — single source of truth for every DB table tracked in station transfer, GDPR export, and GDPR deletion. Stores per-column verification flags, FK metadata, lookups, output shape, custom scope paths, and PG `COMMENT ON TABLE`/`COMMENT ON COLUMN` text (descriptions excluded from the hash so editing comments doesn't invalidate verification)
- **Metadata-driven station export/import** — `GenericTableExporter` and `GenericTableImporter` generate SELECT/INSERT queries dynamically from the tracking metadata. `StationExportService` and `StationImportService` are now thin orchestrators with no per-table SQL
- **Topological table order** — `TableOrder` derives the export/import order from FK dependencies (skipping `SET NULL` FKs to break cycles); no hand-coded `TABLE_ORDER` list
- **Custom scope support** — tables reached via an incoming FK (e.g. `account` through `station_member.account_id`) declare a `customScope` in tracking and the engine emits an `IN (SELECT … FROM viaTable WHERE …)` filter
- **FK-flattened lookups** — `lookups` array on `TableEntry` adds joined fields like `account_email` to exported rows; the importer resolves them back to local FK ids
- **Output shape per table** — `SINGLE` for one-row-per-station tables (`station`), `FLAT` for enum-only tables (`station_disabled_module`); the wire format is keyed by DB table name
- **Account migration** — accounts/credentials transfer via `customScope` through `station_member`; existing target accounts (matched by email) are linked as-is, new accounts are created with `force_password_change=TRUE`
- **Federation state transfer** — every federation table (`federation_partner`, capability, share configs across boards/inventory/KB/protocol/quiz, event/news federation) now transfers with the station; the private key column transfers too so partners keep recognising the station post-migration
- **Metadata-driven GDPR export** — `GenericGdprExporter` builds queries from `gdprExport.identityColumns` matching the requested identity type (`ACCOUNT_ID`/`MEMBER_ID`/`MEMBER_UID`). `GdprExportService` shrank from ~470 hand-coded lines to a thin orchestrator; output keyed by DB table name (`accountTables`, `memberTables`, `memberUidTables`)
- **Metadata-driven GDPR deletion** — `GenericGdprDeleter` honours each `gdprDeletion` strategy (`DELETE_EXPLICIT`, `NULL`, `ANONYMIZE` with type-derived sentinels — zero-UUID, `"Gelöscht"`, NULL for nullable int — and `CASCADE`/`RETAIN`/`RETAIN_UNLINKED`/`NOT_APPLICABLE` no-ops with audit logs). UPDATEs run before DELETEs across all tables; DELETEs in reverse-topological order
- **Dev-mode admin panel** — `/admin/data-tracking` view available only when `Demo.dev()` is true (frontend tree-shakes via `import.meta.env.DEV`). Color-coded status badges, summary dashboard, search by table name / column name / description, batch status changes, per-column verified toggles, multi-select dropdowns for `ignoredColumns`, fully editable GDPR deletion strategies, foreign-key chips with key icons, dangling-reference audit banner that flags MEMBER_ID identity columns without an FK to `station_member`, CASCADE chip warnings when the FK parent's effective strategy isn't actually a deletion
- **Federated uploader for board attachments** — `board_ticket_attachment.uploaded_by INT REFERENCES station_member` replaced with `uploader_station_uid UUID` + `uploader_member_uid UUID`; matches the federated identity pattern already used on `board_ticket.creator_*`, `board_ticket_comment.author_*`, `board_ticket_transition.actor_*`, `board_ticket_watcher.watcher_*`. Federated members from partner stations can now attach files

#### Documentation
- **Environment variable reference** — hosting help page now documents all env vars organized by category: Database, API, Mailing, Auth, Theming, Tools, Frontend, Demo, and Docker/Compose — each with default value and beginner-friendly description

### Improvements
- **Type-safe API responses** — replaced ~50 `Map.of()` API responses across routes, services, and export classes with typed Java records for compile-time safety
- **CI retry** — test jobs (repository, service, other) retry once on failure; Docker push steps retry up to 3 times for transient registry errors
- **CI coverage job** — no longer re-runs all tests; skips the default `test` task since coverage data is downloaded from artifacts
- **FileInput component** — new reusable styled file picker component replacing raw `<input type="file">` elements across the knowledge base
- **Frontend Docker image** — replaced `nixos/nix:latest` with `node:24-alpine` for dramatically faster builds (no nix-shell overhead)
- **Inventory item status** — item detail now shows "Zugewiesen" (assigned) or "Verfügbar" (available) instead of generic "Aktiv"
- **Inventory member avatars** — member names in inventory edit view now display with avatars via MemberName component
- **Members sidebar badge** — now includes both pending changes and waiting list entry counts
- **Inventory sidebar badge** — shows pending exchange request count on the inventory section

### Bug Fixes
- **Help center waiting list link** — home page feature tile linked to non-existent route `/helpcenter/station/members/waitinglist` instead of `/helpcenter/station/members/waiting-lists`
- **Inventory item assigned user** — assigned user was not shown on the item detail page; lookup relied on history entries instead of the direct assignment
- **Permission picker rollback** — unchecking a parent permission (e.g. LOST_AND_FOUND_MANAGE) discarded previously selected child permissions (e.g. LOST_AND_FOUND_CREATE) instead of restoring them
- **My Inventory tab visibility** — sidebar tab was always visible even when the user had no assigned inventory items
- **Orphaned quiz attempt rows** — `quiz_test_attempt.member_id` and `graded_by` were bare INT columns without FKs, so deleting a member left dangling references. Both now FK to `station_member.id` with `CASCADE` and `SET NULL` respectively

### Technical Changes

#### Data Tracking Backend
- **`DataTracking` records** — `TableEntry`, `ColumnEntry`, `ForeignKey`, `Lookup`, `CustomScope`, `TransferContext`, `GdprExportContext`, `GdprDeletionContext`, `DeletionStrategy`, `IdentityColumn` with `Status`/`Strategy`/`IdentityType`/`OutputShape`/`Scope` enums
- **`SchemaReader`** — reads PG `information_schema` plus `obj_description` / `col_description` for table+column comments; emits `RawTable` / `RawColumn` / `RawForeignKey`
- **`HashComputer`** — deterministic SHA-256 over columns + FKs; descriptions intentionally excluded
- **`DataTrackingRefresher`** — merges live schema into `data_tracking.json`, refreshing descriptions on every run and preserving verification flags
- **`StationScopeResolver`** — BFS over the FK graph to find the join chain from any table to a `station_id` column; handles the `station` table itself via `id`, skips `SET NULL` FKs
- **`TableOrder.topological`** — Kahn's algorithm over `dependsOn`, breaks cycles via `SET NULL` skipping, leftover nodes appended alphabetically for stable output
- **`GenericTableExporter`** + **`GenericTableImporter`** + **`GenericGdprExporter`** + **`GenericGdprDeleter`** — engine classes driving the four major flows
- **`DataTrackingAdminService`** — dev-mode only service backing the admin panel, file-path-configurable for tests
- **`DataTrackingRoutes`** — handlers registered only when `Demo.dev()` is true
- **Engine wiring** — `StationExportService`/`StationImportService` dropped ~2400 lines of hand-coded SQL; `GdprExportService` dropped ~470 lines; `GdprDeletionService` dropped ~100 lines. Public API preserved on each
- **DB migration** — `board_ticket_attachment` `uploaded_by` → `uploader_station_uid` + `uploader_member_uid` UUID pair with data backfill; missing FKs on `quiz_test_attempt.member_id` (CASCADE) and `graded_by` (SET NULL) added with defensive orphan cleanup
- **Metadata drift fixes** — `entity_note`, `entity_note_version`, `inventory_item`, `profile_field_change_acknowledgement` identity-column names corrected to match real schema; stale entries removed on `form_answer`, `waiting_list_entry_guardian`, `waiting_list_entry_value`, `waiting_list_invite`, `kb_file`
- **CLI cleanup** — removed `DataTrackingReviewer`/`Prompter`/`ReviewCli`/`BackfillCli`/`TransferMetadataBackfillCli` and their gradle tasks; the dev admin panel covers their use cases. Kept `refreshDataTracking` since the frontend can't read live PG schema

#### Storage Monitoring Backend
- **`StorageCategory` enum** — `KB_FILES`, `BOARD_ATTACHMENTS`, `PAGE_IMAGES`, `AVATARS`, `IMAGES`
- **`StorageUsageRepository`** — delta updates, absolute sets, per-station/category queries
- **`StorageQuotaPresetRepository`** — preset CRUD, apply-to-station, reset quotas, station preset name lookup
- **`StorageQuotaService`** — quota checking, per-file/image size limits, delta tracking, warning threshold detection
- **`StorageReconciliationService`** — filesystem walk + DB recalculation, runs on startup (1min delay) and at configured interval
- **`PresentationCompressor`** — lossless ZIP recompression with `Deflater.BEST_COMPRESSION`
- **`StorageRoutes`** — station usage, admin overview, preset CRUD, apply/reset, reconciliation triggers
- **`StorageWarningEvent`** + handler — domain event notifying STATION_MANAGER role
- **`SizeParser` utility** — parses "5G", "50M" etc. into bytes and formats back
- **`Storage` config** — Ocular config element with env var overrides (`STORAGE_*`)
- **DB migration** — `station_storage_usage`, `storage_quota_preset` tables; station quota columns + `storage_preset_id` FK

#### Federation Version
- **`FederationVersionBroadcaster`** — eager singleton, pings all remote partners 2min after startup
- **`/remote/federation/ping`** — returns `VersionPingResponse` (typed record, not Map)
- **`FederationVersionComputer`** — now tracks DTOs from `FederationRemoteRoutes`, `FederationRoutes`, `LendingRoutes`, `BoardRoutes`
- **`FederationRepository.backfillPartnerVersions`** — updates all partners with version '0' on startup
- **`FederationRepository.createPartner`** — sets `federation_version` to current version at creation time

#### Sitemap
- **Jackson XML serialization** — replaced manual XML string concatenation with typed records and Jackson `XmlMapper`
- **`lastmod` dates** — KB files and pages include W3C Datetime `lastmod` from `updatedAt`; index URLs derive `lastmod` from their most recent child
- **Caffeine caching** — sitemap responses cached in-memory for 6 hours

#### Station Applications
- **Enum status** — `StationApplication.status` changed from raw string to `ApplicationStatus` enum
- **DB migration** — existing lowercase status values normalized to uppercase

#### Public Waitlist Backend
- **PENDING status** — new `WaitingListEntryStatus.PENDING` for entries awaiting approval
- **Verification tokens** — `waitlist_verification_token` table with 24h expiry
- **Domain event** — `WaitlistPublicRegistration` event + handler for WAITLIST_EDIT notifications
- **Email template** — verification email in DE/EN

#### Guardian Schema
- **Name split** — `waiting_list_entry_guardian.name` replaced with `firstname` + `lastname` for direct account creation
- **`GuardianInput` type** — extracted from inline object types in frontend for type safety

#### Badge Convention
- **Lint rule** — error-level rule flags `<span>` with `rounded-full` + padding; must use Badge components
- **Refactored** — 54 violations converted to PrimaryBadge, SecondaryBadge, SuccessBadge, etc.
- **Inline type rule** — warning-level rule flags `ref<{ ... }>` patterns that should use named types

#### Bug Fixes
- **AccountRepository.setEmailVerified** — missing `= TRUE` in SET clause
- **EventCommentRepository.delete** — missing `= TRUE` in soft-delete SET clause
- **WaitingListFieldConfig deserialization** — `FieldRequest.config` changed to `String` to match frontend JSON contract

#### Federation Routes
- **Route restructure** — federation management moved from `/station/manage/federation` to `/station/federate` to fix sidebar prefix overlap

#### Help Center
- **Roles page** — rewritten to use correct "Benutzertypen & Berechtigungen" terminology
- **Federation page** — added missing i18n keys (shared5-7, dummy content keys)
- **FormLabel component** — extracted repeated label pattern into reusable component
- **Page editor help** — dedicated help center page for page editor route

#### Demo Service Refactoring
- **DemoService split** — reduced from 2180 to 679 lines by extracting 4 new seeders:
  - `DemoMemberSeeder` (643 lines) — groups, profile fields, users, tags
  - `DemoEventSeeder` (684 lines) — categories, events, attendance, templates
  - `DemoNewsSeeder` (214 lines) — news articles with comments
  - `DemoPageSeeder` (174 lines) — public pages with hierarchy
- **Parallel seeding** — member seeding runs first, all other seeders run in parallel

#### Frontend Architecture
- **`useCanonical` composable** — reusable canonical URL + `og:url` injection from `NUXT_PUBLIC_SITE_URL`
- **`__sitemap` server route** — Nitro server route fetching discoverable stations for dynamic sitemap entries
- **`build.mjs` wrapper** — polls for build output completion, then SIGKILL's the detached nuxi process group to work around esbuild hang

#### CI/CD
- **`ignore-checks`** — Docker Build workflow excludes `Verify Docker Build` from `wait-on-check-action` to prevent deadlock

## v26.6.1

### New Features

#### Mention System
- **Bulk mentions** — mention entire groups, all event participants, registered members, or declined members in comments
- **Mention UI with avatars** — mention dropdown shows user avatars, name colors, and display tags
- **Guardian notifications** — event-related bulk mentions also notify guardians of mentioned members
- **Restricted mention lists** — when content is restricted to certain groups, only eligible members appear in the mention picker

#### Notifications
- **Mention notifications** — dedicated notification for mentions, separate from comment reply notifications
- **News mention notifications** — mentioning users in news comments now triggers notifications
- **News author in notifications** — new news notifications now show the author name

#### Event Detail
- **Tab layout** — event detail page split into Info and Registrations tabs
- **Non-manager registration display** — pending registrations show as simple cards for users without confirmation permissions

### Bug Fixes
- **Event registration date** — registration and decline actions now use the correct event date instead of defaulting to today
- **Recurring event next occurrence** — correctly shows today as next occurrence when the event hasn't ended yet
- **Notification links** — comment and mention notifications for events now link to the specific event detail page instead of the events list
- **Requirements redirect** — requirements page redirects to the dashboard when there are no pending requirements instead of showing an empty page
- **Avatar loading** — user avatars no longer re-fetch on every hover in the mention dropdown

### Improvements
- **Mobile-friendly tile reel** — home page tiles are responsive (1 on mobile, 2 on tablet, 3 on desktop) with always-visible navigation arrows and touch swipe support
- **Sidebar home link** — clicking the Ember logo/name in the sidebar navigates to the home page

### Technical Changes

#### Database
- **Generated `full_name` column** — `account.full_name` stored generated column replaces repeated `TRIM(first_name || ' ' || last_name)` in SQL queries (patch_7)

#### Backend Architecture
- **`MentionType` enum** — replaces raw strings for bulk mention types (`GROUP`, `EVENT`, `REGISTERED`, `DECLINED`)
- **`BulkMentionedInComment` domain event** — new event type resolved by `BulkMentionedInCommentHandler` to individual member notifications
- **`COMMENT_MENTION` notification type** — separate from `NEWS_COMMENT`, with `CommentMention` params and own locale key
- **KB comment events moved to service** — domain event publishing for KB comments moved from `KnowledgeBaseRoutes` to `KnowledgeBaseService`
- **`NewsService` resolves author name** — derives author name from `MemberIdentity` via account lookup instead of requiring callers to pass it
- **Event date validation** — backend derives event date for one-time events from `startTime` and validates day-of-week for recurring events
- **Restriction-filtered completions** — `/station-members/completions` accepts optional `restrictionType` and `entityId` params to filter by entity visibility

#### Frontend Architecture
- **`EventRegistrationsTab` component** — extracted registration logic from `EventDetailView` (319 lines, down from 506)
- **`MentionInput` unified suggestions** — refactored to support members, groups, and special mentions in a single dropdown
- **`UserAvatar` watcher fix** — watches derived `stationUid/memberUid` string instead of deep-watching the identity object

## v26.6.0

### New Features

#### Boards (Planer)
- **Kanban boards** — per-station scrum/kanban boards with customizable lanes, drag-and-drop ticket reordering between lanes, and position indicators
- **Ticket management** — create, edit, delete tickets with title, rich markdown description (tiptap editor), priority (5 levels with icons), assignee, due date, and custom fields
- **Checklists** — add checklists to tickets with drag-and-drop reordering, progress bar, and bulk delete
- **Ticket links** — link tickets with typed relationships (Relates to, Blocks, Blocked by, Causes, Caused by) with confluence-style display
- **Weblinks** — add external URLs to tickets
- **File attachments** — upload files to tickets with tile-based preview grid; image thumbnails, PDF viewer, and CSV table preview in a fullscreen overlay with keyboard navigation (arrow keys)
- **Labels** — color-coded labels per board with multi-select dropdown, inline creation, and label filter on the board and archived views
- **Knowledge base links** — link KB pages to tickets with title search and folder path display
- **Comments** — threaded comments using CommentThread component with @mentions, reply, edit, delete
- **Watch/unwatch** — subscribe to ticket changes and receive notifications
- **Activity feed** — interleaved timeline of comments, lane transitions, and history entries (priority changes, label assignments, title/description/due date changes, field changes) with rich formatting (lane color pills, priority icons, label badges)
- **Lane colors** — assign colors to lanes; used for lane column top borders and the ticket status button
- **Lane assignee** — custom field type `lane_assignee` that auto-assigns a member when a ticket moves to the referenced lane
- **Backlog** — board-level toggle that creates a hidden backlog lane; dedicated table view at `/station/boards/:boardId/backlog`
- **Archived view** — dedicated table view for tickets past the hide-done-after-days threshold at `/station/boards/:boardId/archived` with label filtering
- **Board overview** — `/station/boards` shows only boards accessible to the user
- **Board management** — `/station/boards/manage` for managers to create, edit, delete boards with settings icon per card
- **Board settings** — lane editor with color picker, field editor (string, number, boolean, enum, date, lane_assignee), backlog toggle, view/edit access restrictions
- **Due date reminders** — daily notification to assignee for overdue tickets not in the last lane
- **Full-text search** — PostgreSQL tsvector/tsquery on ticket title and description with relevance ranking
- **Read-only access** — users with view-only access see all content but cannot edit; all edit controls hidden
- **Drag-and-drop** — tickets between lanes with visual drop indicator; checklist items with grip handles

#### Board Access & Permissions
- **Role hierarchy in board access** — MANAGER role now correctly grants access to TEAM-restricted boards via transitive role expansion
- **Dedicated can-edit endpoint** — `GET /boards/{id}/can-edit` for frontend to check edit permission
- **View/edit access restrictions** — per-board role, group, and tag based access control

#### Permission System
- **Granular permissions** — replaced the flat role system with a hierarchical permission tree; each feature area (events, members, inventory, boards, etc.) has its own read/edit/manage permissions
- **User type permissions** — assign extra permissions to entire user types (Trial, Member, Guardian, Team) station-wide via a new management page
- **Permission picker** — new hierarchical permission selector with collapsible groups, icons, and descriptions; replaces the old flat role checkboxes in member edit and group management
- **Sidebar permission gating** — sidebar links are now shown or hidden based on the user's actual permissions rather than all-or-nothing manager checks
- **Read-only views** — users with read permission but not edit permission see content without edit controls (e.g. waiting list, boards)
- **Granular test permissions** — decomissioned QUIZ_MANAGER; replaced with TEST_CATALOG_VIEW, TEST_CATALOG_EDIT, TEST_CONFIGURE, TEST_RESULT_READ, TEST_REVIEW, and standalone TEST_MANAGER/PROTOCOL_MANAGER under STATION_ADMINISTRATOR
- **Protocol permissions** — PROTOCOL_CONFIGURE for definitions, PROTOCOL_CREATE for runs, PROTOCOL_TESTER for grading
- **NEWS_CREATE renamed to NEWS_EDIT** — covers creating, editing, and deleting news posts; NEWS_FEDERATE gates federation sharing
- **Form permissions** — POLL_VIEW_RESULTS for viewing analytics, POLL_CREATE for creating/editing forms; member restrictions in restriction picker
- **Station management permissions** — granular route permissions (STATION_GENERAL, STATION_LOOK_AND_FEEL, STATION_MAIL, STATION_MODULES, STATION_IMPORT_EXPORT) replace STATION_ADMINISTRATOR; sidebar restructured with manage and federation as separate top-level groups
- **Member permissions** — MEMBER_EDIT replaces MEMBER_MANAGER on import/delete/permissions/user-type routes; MEMBER_MANAGE_TAGS for tag CRUD; MEMBER_READ for GET endpoints
- **Inventory permissions** — INVENTORY_EDIT for update/delete items; MEMBER_READ for member inventory items

#### Member Identity & Display
- **Group colors** — assign a display color to groups; the highest-priority group's color is used as the member's name color everywhere
- **Tag badges** — tags can be marked visible with a color and position; they appear as inline colored pill badges next to member names
- **Unified member identity** — a single identity model (station UUID + member UUID + display name) is used everywhere from database through API to frontend
- **MemberName driven by identity** — the `MemberName` component derives its display name solely from the identity object

#### Waitlist Guardians
- **Multiple guardians per waitlist entry** — each entry can have multiple guardians with name, email, and phone number, replacing the single parent name/email fields
- **Guardian auto-onboarding** — when a waitlist entry is accepted, guardian accounts are automatically created with GUARDIAN user type, LOGIN and MEMBER_GUARDIAN permissions, and linked to the child member
- **Trial member type** — waitlist entries are created as TRIAL type until accepted, then converted to MEMBER
- **Expandable guardian details** — clicking a waitlist entry expands to show guardian contact details
- **Dedicated entry creation view** — adding waitlist entries uses a full page view instead of a modal
- **Waitlist permission split** — new WAITLIST_ADD permission for adding entries without full edit access

#### Member Detail & Edit
- **Member detail tabs** — split into tabs: Profile, Permissions, Guardians, Absences, Inventory, Notes
- **Relations tab** — new tab on member edit for assigning guardians to members and members to guardians
- **Absences tab** — users with MEMBER_EDIT can create, view, and delete absences from the member detail view
- **Permissions tab** — shows user type, permissions with human-friendly names, groups, and tags

#### Event Reminders
- **Configurable reminders** — events and event templates support multiple reminders defined in days before the event
- **Reminder scheduler** — background checker sends EVENT_REMINDER notifications to eligible members
- **Smart targeting** — public events notify all non-declined members; registration-required events notify only accepted/pending registrants
- **Template carry-over** — reminders from templates are applied when creating events from templates

#### Federated Comments
- **Event comments** — comment on events shared by federation partners; comments show the author's station badge
- **News comments** — comment on news posts shared by partners with full threading support
- **Knowledge base comments** — threaded comments on KB files with federation support and soft-delete

#### News Federation
- **Per-post sharing** — choose which news posts to share with partners: all partners or specific ones
- **Visibility role** — set a minimum role for shared news visibility at partner stations
- **Federated news in feed** — partner news posts appear inline in the news list, marked with a federation badge

#### Event Cancellation
- **Manual cancellation** — managers can cancel events with a reason
- **Auto-cancellation** — events that don't reach the minimum registration count by a threshold date are automatically cancelled
- **Cancellation notifications** — all registered members receive an EVENT_CANCELLED notification

#### Quiz & Test Improvements
- **New question types** — enumeration, ordering, matching, and fill-in-the-gap questions
- **Readonly catalog view** — users with TEST_CATALOG_VIEW see catalogs and questions with answers without edit controls
- **Test results tab** — test detail view has a Results tab showing all attempts
- **Enriched attempt detail** — single API call returns attempt, full question details, and member identity
- **Grading UX** — "Geprüft & Weiter" / "Geprüft & Beenden" shortcuts; compact icon buttons; reorganized mobile navigation

#### Other
- **Station requirements view** — shows outstanding requirements for the current member with sidebar badge
- **Board improvements** — human-readable URLs, federated ticket links, chronological activity tab, keyboard navigation
- **Sidebar counts** — all sidebar badges load in a single API call
- **Dev error handler** — filename format `HH-mm-ss - source - hash.txt`; `reportCaughtError()` for frontend catch blocks
- **Start/end date sync** — setting a start date auto-fills the end date if empty
- **Modules toggle** — added TEST_PROTOCOL and BOARDS to the modules management page

### Improvements

- **Borderless input fields** — new `borderless` prop on BaseInput/TextInput for clean inline editing
- **Click-to-edit title** — ticket title renders as heading, switches to borderless input on click
- **MemberSelectInput auto-open** — opens dropdown and focuses search immediately on mount
- **IconSelectInput auto-open** — priority selector opens dropdown immediately
- **Click-outside handling** — all sidebar editors (lane, priority, assignee, due date) close when clicking outside the right column
- **Color input component** — new ColorInput.vue for lane color selection in settings
- **SelectInput min-w-0** — global fix for dropdown width issues in flex containers
- **Checklist progress bar** — fixed invisible bar (was using undefined `--accent`, now uses `bg-primary`)
- **Overdue due dates** — highlighted in red on ticket tiles
- **Attachment count on tiles** — paperclip icon with count in ticket tile bottom row
- **Description save button** — replaced checkmark icon with proper "Speichern" PrimaryButton
- **Comment submit button** — changed to "Absenden" matching news comment pattern
- **Sidebar boards** — only shows boards the user can view (managers see all in manage view)

### Bug Fixes

- Fixed getViewAccess/getEditAccess returning empty lists instead of actual stored restriction IDs
- Fixed manage view not showing create/edit controls
- Fixed board managers seeing all boards in sidebar instead of only accessible ones
- Fixed role hierarchy not applied in board access checks (MANAGER not matching TEAM restrictions)
- Fixed file download throwing unauthorized (switched from direct URL to authenticated blob download)
- Fixed `createTicket` CTE missing `attachment_count` column causing runtime error
- Fixed KB link `folderPath` showing double `/` for root-level files
- Fixed KB links not loading on initial ticket detail page load
- Fixed checklist progress bar invisible (undefined CSS variable)
- Fixed lane top border using undefined `--accent` variable

---

### Technical Changes

#### Database
- **Patch 6** — 15 new tables: `board`, `board_lane` (with color), `board_field`, `board_view_access`, `board_edit_access`, `board_ticket` (with full-text search vector), `board_ticket_field_value`, `board_ticket_link`, `board_ticket_checklist_item`, `board_ticket_transition`, `board_ticket_comment`, `board_ticket_watcher`, `board_ticket_weblink`, `board_ticket_attachment`, `board_ticket_history`, `board_label`, `board_ticket_label`, `board_ticket_kb_link`
- Generated tsvector column with GIN index for full-text search
- Board-level `backlog_lane_id` FK for backlog support

#### Backend Architecture
- **18 new entity records** with RowMapping: Board, BoardLane, BoardField, BoardFieldConfig, BoardTicket, BoardTicketLink, LinkType, BoardTicketTransition, BoardChecklistItem, BoardComment, BoardWeblink, BoardTicketAttachment, BoardTicketFieldValue, BoardTicketWatcher, BoardTicketHistory, BoardLabel, BoardTicketKbLink, TicketPriority
- **BoardRepository** — CRUD for boards, lanes, fields, labels, access restrictions, backlog management
- **BoardTicketRepository** — CRUD for tickets, links, checklist, comments, weblinks, attachments, field values, watchers, history, KB links, activity feed (UNION ALL query)
- **BoardService** — access control with role hierarchy expansion via `Roles.expand()`, label management, backlog toggle
- **BoardTicketService** — ticket lifecycle, lane_assignee auto-assignment on move, @mention parsing in comments, watcher notifications, history logging for all changes
- **BoardRoutes / BoardTicketRoutes** — 50+ REST endpoints
- **DueDateReminderChecker** — scheduled executor for daily due date notifications
- **BoardTicketChanged** domain event — consolidated watcher notification for all ticket changes
- **MentionedInComment** extended — `BOARD_TICKET` ethentity type with ticket-detail link
- **LaneData, AccessData, TicketLabelMapping** — extracted to top-level records by spotless

#### Frontend Architecture
- **15 new views**: BoardOverviewView, BoardListView, BoardView, TicketDetailView, BoardSettingsView, BacklogView, ArchivedView + 5 help center pages
- **7 new components**: TicketTile, TicketChecklist, TicketActivity, TicketLinksSection, LabelSelectInput, ColorInput, DragList (reused)
- **boards.ts API** — 40+ functions for all board, ticket, label, attachment, KB link, and history operations
- **Authenticated file handling** — blob download/preview via axios instead of direct URLs

#### Permission Architecture
- Four new enums: `StationPermission`, `StationUserType`, `InstancePermission`, `InstanceUserType` replacing flat role strings
- `station_user_type_permission` DB table with CRUD API endpoints
- `PermissionPicker.vue` component with hierarchical display, implicit grant hiding, and "granted by" attribution
- Permission granularity across all route handlers; read-only routes accept `_READ` where previously they required manager grants

#### Member Identity
- `uid UUID` column added to `station_member`; `MemberIdentity(stationUid, memberUid)` record replaces dual local/federated representation
- `MemberIdentityFactory` service with `MemberNameResolver` Caffeine caching
- Mention format migrated to `@[stationUid/memberUid:Name]` with legacy support

#### Waitlist & Guardians
- `waiting_list_entry_guardian` table with cascade delete; migration backfills from legacy `parent_name`/`email`
- `event_reminder`, `event_template_reminder`, `event_reminder_sent` tables for reminder tracking
- `EventReminderChecker` scheduled executor (every 30 minutes)
- `QuizCatalogRepository.findQuestionsByIds()` batch query for enriched attempt detail

#### Test Coverage
- **Board repository tests** — 20+ tests covering tickets, lanes, labels, attachments, field values, weblinks, search, history, backlog, KB links
- **Board service tests** — 25+ tests covering CRUD, access control with role hierarchy, labels, backlog, field values, attachments, comments, watchers, move/reorder/link operations
- **JaCoCo exclusion** — DueDateReminderChecker, EventReminderChecker excluded (daemon pattern)
- All coverage thresholds met: 95% repositories, 90% services, 80% handlers

---

## v26.5.0

### New Features

#### Comments & @Mentions
- **Event comments** — threaded comments on events, just like news comments
- **@Mentions** — type `@` in any comment to search and tag members; they get a notification
- **Reply notifications** — replying to a comment notifies the original author
- **Soft-delete** — deleting a comment with replies shows "Dieser Kommentar wurde gelöscht" instead of removing the whole thread

#### Notes
- **Notes on inventory items, member profiles, and events** — managers can keep internal notes with version history
- Member profile notes are only visible to managers

#### Feeds (iCal, RSS, Atom)
- **iCal feed** — subscribe to your events in Thunderbird, Outlook, Google Calendar, or any calendar app
- **RSS and Atom feeds** — follow your notifications in any feed reader
- **Feed management** — generate, regenerate, or revoke your feed token; toggle which notification types appear in feeds
- Dashboard shows a reminder when feeds are not set up or inactive

#### Event Templates
- **Reusable templates** — save and load event templates with all fields, attendance settings, and registration limits
- **Quick fields** — Ort, Treffpunkt, and Thema quick-add buttons in the field editor

#### Federated Events
- **Cross-station event sharing** — share events with federation partners
- **Remote registration** — register for events at partner stations
- Partner station events shown on the upcoming events page

#### Federated Knowledge Base
- **Shared KB browsing** — browse files and folders from partner stations
- **Federated search** — search queries partner stations in parallel
- **Partner filter** — show only content from a specific partner

#### Public Calendar & Station View
- **Public calendar** — expose an event calendar for visitors without an account
- **Public station page** — unified public view with calendar and knowledge base tabs
- Event fields can be marked as public or internal

#### Event Categories
- Create, edit, reorder, and delete event categories
- Configure how many events each category shows on the overview
- Mark categories as public for the public calendar

#### Registrations
- **Grouped view** — registrations grouped by event, sorted by deadline
- **Fairness table** — acceptance/denial ratio per member for fair decision-making
- **Registration limit** — cap the number of accepted registrations per event
- **Deadline notifications** — managers are notified when a deadline expires with pending registrations

#### Inventory
- **Item detail page** — view item metadata, current assignment, full history, and manager notes

#### Theming
- **New themes** — color blind accessible themes and fire theme
- **Feel setting** — choose between rounded or cornered UI style
- **Hierarchical settings** — instance, station, and user each pick their theme; each level can lock for the level below

#### Problem Reports
- **Report a problem** — floating bug icon on all station pages; automatically captures page, roles, and recent requests
- **Admin review** — view, acknowledge, and delete problem reports

#### Admin Settings
- **Legal documents** — edit privacy policy, terms of service, consent text, and imprint
- **Mailing settings** — configure SMTP in the admin UI

### Improvements

- New help pages for theming, sessions, notifications, modules, import, federation, comments, templates, notes, categories, legal, and mailing
- iCal and RSS/Atom setup guides for Thunderbird, Outlook, Android, and iOS
- News has a dedicated detail page with always-visible comments
- Clicking a notification links directly to the relevant page and auto-acknowledges
- Sidebar headers are now clickable and collapsible
- Item names in inventory tables link to the detail page
- Admin and station settings split into focused sub-views
- Improved landing page
- **Form answer validation** — submitted answers are now validated against question rules (option range, multi-select limits, rating scale, ranking order, likert bounds)
- Absences visible to both event and attendance managers

### Bug Fixes

- Fixed @mentions not matching between frontend and backend
- Fixed deleting a comment removing all replies — now soft-deletes
- Fixed news author being notified on every comment instead of only on replies
- Fixed KB share links pointing to the wrong URL
- Fixed federated KB files navigating to a non-existent local file
- Fixed absences section visible to non-managers on event detail
- Fixed past event registrations appearing on the dashboard
- Fixed modal component warnings

---

### Technical Changes

#### Architecture
- **Domain event system** — `DomainEventBus` with Guice multibinding; 19 event handlers decouple notification logic from routes
- Services publish events after state changes; handlers create notifications
- Notifications no longer created in route handlers

#### Code Quality
- All `String config` fields replaced with typed records (`ProfileFieldConfig`, `EventFieldConfig`, `AttendanceFieldConfig`, `FormQuestionConfig`, `WaitingListFieldConfig`) with `parse()`/`toJson()`
- All `String *Type` fields replaced with proper enums (`ProfileFieldType`, `EventFieldType`, `AttendanceFieldType`, `NoteEntityType`, `CommentEntityType`, `FilterTableType`, `ContentType`, `ChangeType`)
- `QuizService.createQuestion()` accepts `QuestionConfig` instead of raw JSON
- `FormQuestionConfig.validate(FormAnswerValue)` validates answers per question type on submission
- `MultiLimitType` enum replaces raw `String multiLimitType` (NONE, AT_MOST, AT_LEAST, EXACTLY)
- Unified `QuestionType` enum — removed duplicate inner `FormQuestion.QuestionType`
- Removed dead `NewsCommented` event (superseded by `CommentCreated`)

#### Frontend Components
- `InfiniteReel`, `PublicEventList`, `DiffView`, `ThemeSelector`, `NoteEditor` components
- Comment highlight via `?comment=123` query param
- Lightweight `GET /station-members/completions` endpoint for @mention autocomplete

#### Infrastructure
- JaCoCo coverage enforcement: 95% repositories, 90% services, 80% handlers
- Unit tests for all 19 domain event handlers
- Parallel CI test jobs (`testRepositories`, `testServices`, `testOther`)
- Coverage verification across parallel CI jobs
- Javadoc verification in CI
- Comprehensive service test suite (attendance, auth, batch events, comments, consent, federation, fields, templates, feeds, forms, KB, notes, profiles, quiz, registrations, applications, protocols, settings)
- Database patch 5: public columns for stations, categories, events, fields, boards, problem reports, feed tracking

---

## v26.4.0

### New Features

#### Event Batch Import/Creation
- **Batch event creation** — create multiple events at once with a multi-step wizard (schedule, edit, confirm)
- **Date generation** — auto-generate recurring date ranges by count, interval, and event type
- **Batch edit table** — spreadsheet-style editing of generated events before creation
- **Event layouts** — reusable field templates for consistent event configuration across batch and single creation
- **Layout management view** — dedicated view for creating/editing event layouts with field configuration
- **Event filter bar** — filter upcoming events by category and other criteria
- **Events by category** — categorized display in the event index view
- **Registration stats panel** — fairness statistics for event registration acceptance/denial decisions (accepted/denied ratio per member)

#### Federation System
- **Multi-station federation** — connect with other stations to share content (Knowledge Base, Quiz catalogs, Test Protocols)
- **Partnership management** — create, suspend, resume, or end federation partnerships
- **Capability configuration** — control which content types can be shared per direction (import/export) per partner
- **Cross-instance federation** — RSA-signed HTTP communication between separate Ember instances
- **Shared content browsing** — browse KB files, quiz catalogs, and protocols shared by partners
- **One-click content copy** — copy federated content to your own station
- **Metadata caching** — browse federated content even when remote instance is temporarily unavailable
- **Webhook notifications** — real-time change notification between federated instances
- **Sync polling** — change log based sync for detecting content updates

#### Inventory Lending
- **Lending requests** — request inventory items from federated partner stations with date ranges
- **Request lifecycle** — REQUESTED → APPROVED → LENT → RETURNED → CLOSED workflow
- **Item assignment** — assign specific items to approved lending requests
- **Built-in messaging** — chat between requesting and owning stations with system messages
- **Inventory blocking** — block inventories or items during date ranges to prevent lending
- **Available browsing** — browse available inventory from partners with date filtering and search
- **Lent-out tracking** — view currently lent out items per inventory
- **Lending blocks** — tile-based creation UI supporting multiple inventories and items per block

#### Federation Discovery
- **Discovery registry** — stations can opt into being discoverable (none/instance/public visibility)
- **Public discovery page** (`/discovery`) — browse discoverable stations without login
- **Pairing codes** — stateless codes (`ember-BASE64(uid)-BASE64(host)`) for requesting federation
- **Station invite codes** — manager-generated codes that auto-activate (consent already given)
- **Pair requests** — discovery codes create pending requests that target station must accept/decline
- **Pair request management** — view and accept/decline incoming federation requests

#### Public Knowledge Base
- **Public KB mode** — OFF, ALLOW_ALL, or DENY_ALL per station
- **Per-file/folder visibility override** — override the global mode for individual items
- **Public browsing** — unauthenticated access to browse, read, and search public KB content
- **Public file viewer** — rendered markdown, PDF download, image display, YouTube embeds
- **Full-text search** — PostgreSQL tsvector search on public content with snippets

#### Unified Restrictions System
- **Consolidated architecture** — single restriction table per entity type replacing scattered tables
- **Flexible modes** — AND/OR logic for combining role, group, tag, and member restrictions
- **Role hierarchy** — transitive permission inheritance in PostgreSQL (MANAGER → TEAM → LOGIN)
- **Manager bypass** — management roles automatically bypass restrictions in their domain
- **Database functions** — efficient PL/pgSQL restriction checking with member identity resolution

#### Quiz AI Generation
- **AI-powered question generation** — generate quiz questions and wrong answers via AI providers
- **Batch generation** — generate multiple questions per category with context awareness
- **Custom prompts** — override default prompts per generation batch
- **Async job processing** — long-running generation with polling for results

#### Quiz CSV Import
- **CSV file import** — import questions from CSV into quiz catalogs
- **Column mapping** — flexible mapping of CSV columns to question fields
- **Custom separators** — configure separators for columns and multi-answer fields

#### API Monitoring (Admin)
- **Request logging** — all API requests logged with method, path, status code, and duration
- **Performance dashboard** — slowest/fastest endpoints, hourly stats, status code breakdown
- **Endpoint detail view** — drill into individual endpoints for response time charts and request history
- **Problem log** — application-wide problem logging with acknowledge/filter functionality

#### GDPR Export Improvements
- **ZIP format** — data export downloads as ZIP instead of plain JSON
- **PDF summary** — human-readable Typst-generated PDF with account info, memberships, inventory
- **User files included** — KB files created by the user bundled in the ZIP

#### Station Export/Import
- **UUID preservation** — station UUID preserved during transfer (federation codes survive)
- **Knowledge base export** — KB folders, files, content, and version history in station transfer
- **Logo export** — station logo transferred as base64

### Improvements

#### Frontend Architecture
- **Component library expansion** — 30+ new base components (Table, Typography, Display, Input, Discovery)
- **Convention linting** — automated checks for raw HTML elements, CSS class count, repeated patterns, file size
- **Help center linting** — validates every route has a corresponding help article
- **Icon linting** — verifies all FontAwesome icons are properly registered
- **View decomposition** — large views split into focused sub-components (Attendance, Inventory, Members, Quiz, Knowledge Base)
- **Style guide** — updated `/style` page showcasing all base components

#### Knowledge Base
- **Edit modals** — improved file/folder editing with restrictions, tags, and public visibility

#### Attendance
- **Session view refactoring** — decomposed into toolbar, header, member list, check mode, summary, and fields panels
- **Rapid check mode** — fast check-in/out workflow

#### Events
- **Export modal** — configurable event data export

#### Waiting List
- **Detail sub-views** — separated into overview, waiting, invites, testing, and finished sections

#### Theme & UI
- **Theme initialization fix** — dark/light mode applies correctly on first visit
- **Dark mode chart colors** — fixed ECharts label colors in dark mode
- **Station switcher** — improved station selection UI in footer

#### Quiz
- **Question point calculation rework** — improved scoring logic for quiz questions
- **Code cleanup** — refactored quiz configuration editors and catalog views

#### Federation
- **Webhook service cleanup** — improved reliability and code quality
- **Federation service refactoring** — cleaner entity handling with proper enums for ChangeType and ContentType
- **HTTP client improvements** — better error handling in federation communication

#### Admin
- **Station management** — enhanced with federation, discovery, and module settings
- **Docker workflow** — releases tag as `latest`, pushes to `main` tag as `dev`

### Security & Technical

- **Station-scoped access enforcement** — all entity read/write operations now validate that the authenticated user belongs to the correct station, preventing cross-station data access even with a valid session
- **Repository hardening** — queries now consistently filter by station ID to prevent unauthorized cross-station reads (Events, News, Members, Forms, Inventory, Knowledge Base, Attendance, Groups, Tags, Waiting List, Federation)
- **RSA-2048 signing** — federation requests cryptographically signed
- **Station UUIDs** — external identifiers prevent enumeration
- **Role hierarchy enforcement** — database-level transitive permission checking
- **Private key per station** — generated at station creation

### Privacy Policy

- Updated data export description to reflect ZIP+PDF+files format (Art. 15 and Art. 20 GDPR)

### Infrastructure

- **Renovate** — automated dependency updates with 14-day stabilization, auto-merge for minor/patch
- **Database patches 4-6** — federation tables, unified restrictions, role hierarchy, API logging, discovery settings

### Bug Fixes

- Fixed admin problems view not truncating error messages
- Fixed event field editor and value input handling for new field types
- Fixed attendance service integration with event batch creation

---

## v1.2.0

### New Features

#### Test Protocols (Prüfungsprotokolle)

- **Full test protocol system** for practical exams (e.g. Jugendflamme) — create protocol templates with hierarchical sections, subsections, and individual checkboxes with 0.5 or 1 point values
- **Protocol builder**: create and edit protocols with sections, subsections, and items. Edit protocol name, description, and pass threshold. Add descriptions to sections and items
- **Test runs**: create a test run from a protocol template, select members to test by group/role/individual. Runs have OPEN/CLOSED lifecycle
- **Touch-optimized grading wizard**: step-by-step or section-selectable grading view with large touch-friendly checkboxes. Auto-saves on every check. Section selector tabs with live score progress
- **Member locking**: while a tester grades a member, others are locked out. Re-entry allowed for the same tester. Auto-unlock on exit
- **Section completion tracking**: mark sections as "tested" with checkmark indicators. Track progress per member (e.g. "5/7 Abschnitte")
- **Evaluation table**: color-coded matrix view (like the Jugendflamme CSV) with sections as rows, members as columns. Average column. Pastel color coding (green ≥90%, yellow ≥60%, orange ≥30%, red <30%). Sticky first 3 columns for horizontal scrolling. Filter for incomplete members
- **PDF exports**:
  - Per-member protocol PDF (landscape, two-column): logo + station name header, checkboxes, per-section tester names, section headers as 3-column table (Name | Prüfer | Score), right-aligned points, horizontal separator lines
  - Evaluation table PDF (landscape): full matrix with pastel cell coloring, subsection detail rows, bold sum rows with separators, station branding
  - ZIP download: all member PDFs + evaluation table in a single ZIP file
- **Demo data**: Jugendflamme Stufe 1 protocol seeded with all 7 sections (Notruf, Knoten, Schläuche, Verteiler, Strahlrohr, Erste Hilfe, Unterflurhydrant). Open run for current year + completed run from last year with randomized scores
- **Roles**: `PROTOCOL_MANAGEMENT` (create/manage protocols and runs) and `PROTOCOL_TESTER` (grade members), both included in MANAGER
- **Module**: `TEST_PROTOCOL` (toggleable per station)
- **Help center**: dedicated help page with structure explanation, grading demo, and locking description

## v1.1.0

### New Features

#### Knowledge Base (Lernsammlung)

- **Rich text editor** (Tiptap-based) with full WYSIWYG formatting: bold, italic, underline, strikethrough, headings (H1–H3), bullet/ordered lists, blockquotes, code blocks, tables, horizontal rules, colored text, highlighted text
- **Editor refactored** into self-contained sub-components: `EditorToolbar`, `EditorTableBar`, `EditorLinkDialog`, `EditorImageDialog`, `EditorVideoDialog`, `EditorBubbleMenu`, `ImageNodeView`
- **Image support**: upload images or insert from URL, with resizable width controls directly below each image in the editor
- **Video embedding**: YouTube, Vimeo, PeerTube, Dailymotion — auto-detects provider and generates correct embed URL
- **Link dialog**: Confluence-style floating panel with KB file search by title, folder path display, and inline text editing. Replaces native `prompt()` dialogs
- **Link tooltip**: hovering on a link shows URL, edit button, open-in-new-tab button, and unlink button
- **Table editing**: contextual toolbar for adding/removing rows and columns, sticky below the app header for long documents
- **Raw markdown toggle**: switch between rich text and raw markdown view
- **Bubble menu**: formatting toolbar on text selection; link tooltip on link hover; dismiss button to close without losing selection
- **Word document import**: upload `.docx`, `.odt`, `.rtf`, `.html` files — automatically converted to markdown via pandoc
- **PDF text extraction**: uploaded PDFs are indexed for full-text search using Apache PDFBox
- **Search improvements**: prefix matching (e.g. "Notr" matches "Notruf"), highlighted snippets with yellow `<mark>` tags, markdown/HTML stripped from snippet text
- **Related files**: "further reading" links between KB files with add/remove UI on file detail page
- **File detail view**: shows last edit time and editor name, editable description, leaves edit mode after saving
- **Tags**: case-insensitive tag autocomplete on files and folders
- **Folder icons**: upload custom icons for folders, displayed in grid and list views. Icon updates now persist correctly in the database
- **Version history**: colored diff view with proper green/red backgrounds using `color-mix()`, version author names displayed
- **Condensed list view**: compact file browser with divider-separated rows instead of card containers
- **Binary file storage on disk**: PDFs, images, and other binary files stored in `data/kb-files/` instead of the database. Dropped `content BYTEA` column from `kb_file_content`
- **Link entries**: open in new tab instead of iframe embed
- **YouTube metadata**: fetches video title/author via oEmbed API for search indexing
- **Formatting showcase**: demo file in KB root showing all supported editor formatting

#### Quiz System

- **Full quiz feature**: catalogs, categories, question management, test creation, grading
- **Question types**: Multiple Choice, Fill-in-the-Blank, Free Answer, Connect, Image+Text, True/False, Ordering, Enumeration
- **AI question generation**: supports OpenAI, Anthropic Claude, Google Gemini. Session-based multi-turn conversations to avoid duplicate questions. Polling endpoint for streaming results
- **CSV import**: dedicated view with 3-step flow (upload → column mapping → preview/edit). Backend CSV parsing with Apache Commons CSV. Per-question answer splitting, type-specific configuration, AI wrong answer generation
- **PDF export**: Typst-based with checkboxes, fill-in-the-blank gaps, word banks, section summaries, image embedding, page break control
- **Test lifecycle**: DRAFT → ACTIVE → CLOSED with frozen questions generated at activation. Attempt counting per student
- **Auto-grading**: MC, T/F, connect, ordering, fill-blank auto-graded on submit. Free answer/image text require manual grading
- **Config as JsonNode**: question config stored as typed JSON objects instead of raw strings

#### Waiting List

- **Full waiting list feature**: registration forms with custom fields, invite codes, scoring formulas
- **Status lifecycle**: WAITING → INVITED → TESTING → JOINED/WITHDRAWN with timestamp recording for each transition
- **Member creation**: on invite, creates station member with testing group assignment
- **Attendance tracking**: testing members added to attendance sessions via their testing group, attendance count tracked
- **Self-service**: public registration page, interest confirmation, self-withdrawal via token
- **Auto-confirmation**: scheduled daemon checks for expired confirmations, sends reminders, auto-withdraws after grace period
- **Editable registration date**: managers can edit when an entry was added to the waitlist
- **Email notifications**: registration confirmation, confirm reminder, removal warning templates (DE + EN)
- **Demo data**: seeded entries across all statuses with attendance records

#### Admin Settings

- **Platform settings view**: station registration toggle, auth config (token sizes, session duration), mailing config (SMTP), legal document editing with versioning
- **Patch notes view**: pulls releases from GitHub API, renders release notes with markdown formatting, accessible via clickable version in footer

### UI & Component Improvements

- **SelectionToggleButton**: shared component for role/group/tag toggle selections (replaces raw buttons in 6+ views: EventEditView, IndexView, EventModal, NewsEditView, AbsenceView)
- **DropdownMenuItem**: shared component for dropdown menu items (used in KnowledgeBaseView)
- **Markdown content CSS**: comprehensive `.markdown-content` class replacing non-functional `prose` classes (Tailwind Typography plugin was not installed). Covers headings, lists, quotes, tables, code blocks, images, iframes, horizontal rules, alternating table row backgrounds
- **`--border` CSS variable**: properly defined for light (`#c0c0c0`) and dark (`#3a3a3a`) modes — fixes invisible borders throughout the app
- **Search snippet highlighting**: matched terms shown with yellow `<mark>` background
- **EmberLogo component**: reusable logo display with blink animation, used across landing page, sidebar, help center, 404 pages
- **ThemePicker component**: theme color selection
- **NotFoundContent/NotFoundView**: 404 pages with branding
- **FormulaInput component**: formula editor for waiting list scoring
- **Style guide updated**: SelectionToggleButton and DropdownMenuItem added to `/style`
- **Help center**: added pages for Knowledge Base editor, admin settings; updated existing pages

### Infrastructure

- **Data directory initialization**: legal document templates bundled in JAR, copied to `data/` on first startup if files are missing
- **`.dockerignore`**: excludes `data/`, build artifacts, and IDE files from Docker builds
- **WebP image support**: TwelveMonkeys ImageIO library for native WebP reading; graceful fallback for unsupported formats
- **Pandoc integration**: `PANDOC_BIN` env variable for document conversion (defaults to `pandoc`)
- **Strikethrough in CommonMark**: added GFM strikethrough extension to the markdown renderer
- **Request body redaction**: auth, AI, and config endpoints excluded from request/response logging
- **Shared utilities**: `CsvParser` (Apache Commons CSV), `PandocConverter`, `TextDiff` (unified diff patches), `TypstCompiler` (PDF generation)
- **Unit tests**: markdown rendering, quiz PDF export, waiting list service, score evaluator

### Bug Fixes

- Fixed diff view colors using `color-mix()` instead of broken Tailwind CSS variable opacity
- Fixed `prose` classes doing nothing — replaced with custom `.markdown-content` CSS
- Fixed markdown toggle crash (`el is null`) by using `v-show` instead of `v-if` for editor content
- Fixed link clicks opening URLs in the editor — intercepted via `editorProps.handleClick`
- Fixed horizontal rule invisible — changed border color to `color-mix(in srgb, var(--text) 25%, transparent)`
- Fixed heading buttons not working — added `clearNodes()` before `setHeading()` to exit lists/blockquotes
- Fixed P button no effect — changed to `clearNodes().setParagraph()`
- Fixed images not showing in editor — lift `<img>` out of `<p>` tags before setting editor content
- Fixed image upload for WebP — added fallback for formats ImageIO can't read
- Fixed folder icon not showing after upload — now updates `folder.iconUrl` in database
- Fixed table controls bar not appearing — moved reactive refs before `useEditor()` call
- Fixed BubbleMenu conflicts — merged two BubbleMenus into one with `shouldShow` callback
- Fixed search snippets showing tsvector tokens — now uses `ts_headline` on actual `text_content`
- Fixed demo mode station registration — disabled via `station_registration_enabled` setting

### Dependencies Added

- `@tiptap/*` (vue-3, starter-kit, extensions for table, highlight, youtube, image, color, text-style, underline, link, placeholder)
- `turndown` (HTML → Markdown conversion)
- `marked` (Markdown → HTML parsing)
- `diff` (text diffing for version history)
- Apache PDFBox 3.0.5 (PDF text extraction)
- Apache Commons CSV 1.14.0 (CSV parsing)
- TwelveMonkeys ImageIO WebP 3.13.0 (WebP image support)
- OpenAI Java SDK, Anthropic Java SDK, Google GenAI SDK (AI question generation)
- java-diff-utils 4.15 (unified diff patches)

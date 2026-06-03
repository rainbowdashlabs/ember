# Changelog

## v26.7.0

### New Features

#### Permission System
- **Granular permissions** — replaced the flat role system with a hierarchical permission tree; each feature area (events, members, inventory, boards, etc.) has its own read/edit/manage permissions
- **User type permissions** — assign extra permissions to entire user types (Trial, Member, Guardian, Team) station-wide via a new management page
- **Permission picker** — new hierarchical permission selector with collapsible groups, icons, and descriptions; replaces the old flat role checkboxes in member edit and group management
- **Sidebar permission gating** — sidebar links are now shown or hidden based on the user's actual permissions rather than all-or-nothing manager checks
- **Read-only views** — users with read permission but not edit permission see content without edit controls (e.g. waiting list, boards)

#### Member Identity & Display
- **Group colors** — assign a display color to groups; the highest-priority group's color is used as the member's name color everywhere
- **Tag badges** — tags can be marked visible with a color and position; they appear as inline colored pill badges next to member names
- **Unified member identity** — a single identity model (station UUID + member UUID + display name) is used everywhere from database through API to frontend, simplifying federation and enabling consistent display across local and remote members
- **Identity carries display name** — the `MemberIdentity` object now includes the resolved member name; all API responses that reference members return an enriched identity with avatar, name, group color, and tag badge in one object
- **MemberName driven by identity** — the `MemberName` component derives its display name solely from the identity object; separate name props have been removed across the entire frontend

#### Federated Comments
- **Event comments** — comment on events shared by federation partners; comments show the author's station badge
- **News comments** — comment on news posts shared by partners with full threading support
- **Knowledge base comments** — threaded comments on KB files with federation support and soft-delete

#### News Federation
- **Per-post sharing** — choose which news posts to share with partners: all partners or specific ones
- **Visibility role** — set a minimum role (Member/Team/Manager) for shared news visibility at partner stations
- **Federated news in feed** — partner news posts appear inline in the news list, marked with a federation badge
- **Federated news detail** — dedicated view for reading partner station news with full content and comments

#### Event Cancellation
- **Manual cancellation** — managers can cancel events with a reason; a red alert banner is shown on cancelled events
- **Auto-cancellation** — events that don't reach the minimum registration count by a threshold date are automatically cancelled
- **Cancellation notifications** — all registered members receive an EVENT_CANCELLED notification

#### Station Requirements View
- **Requirements page** — new view showing outstanding requirements for the current member: incomplete profile, forced forms, and forced quizzes
- **Sidebar badge** — a badge appears when the member has unfinished requirements

#### Board Improvements
- **Human-readable URLs** — board and ticket URLs now use short keys and ticket numbers (e.g. `/boards/jug/tickets/1`) instead of integer IDs
- **Federated ticket links** — create and delete ticket links on federated boards with history logging
- **Assignee change logging** — assignee changes are logged in ticket history and trigger notifications
- **Lane replacement safety** — replacing lanes preserves existing lane IDs and moves orphaned tickets to the last remaining lane
- **Chronological activity tab** — the history tab is now sorted chronologically instead of grouped by type
- **Keyboard navigation in ticket link search** — arrow keys to navigate, Enter to select, self-linking prevented

#### Help Center
- **Boards help rewrite** — all 9 boards help pages rewritten with detailed sections and tips
- **Type permissions help** — new article explaining the layered permission model
- **Requirements help** — help article for the requirements view

#### Other
- **Layered Ember logo** — new animated logo with blink, bounce, and FAQ shake animations, replacing the old static logo
- **Sidebar counts** — all sidebar badges now load in a single API call for better performance
- **Parallel demo seeding** — demo data seeding runs module seeders in parallel using virtual threads, significantly reducing startup time

### Bug Fixes

- Fixed federation comment badges showing incorrectly for own station's federated comments
- Fixed federation access not respecting role hierarchy (MANAGER not matching TEAM restrictions)
- Fixed ticket links not working on federated boards
- Fixed assignee changes not being tracked in board ticket history
- Fixed lane deletion removing tickets instead of moving them to remaining lanes
- Fixed protocol views not rendering correctly with new permission model
- Fixed federation HTTP client serialization issues

---

### Technical Changes

#### Permission Architecture
- Four new enums: `StationPermission`, `StationUserType`, `InstancePermission`, `InstanceUserType` replacing flat role strings
- New `station_user_type_permission` DB table (patch 7) with CRUD API endpoints
- `AccessManager` extended to resolve type-level grants alongside individual and group grants
- `PermissionPicker.vue` component with hierarchical display and implicit grant hiding
- `hasAnyXPermission()` composable helpers in `useSession.ts` for all feature areas
- Waitlist permission split: `WAITLIST_ADD` → `WAITLIST_EDIT`, separate `WAITLIST_READ`
- Board permissions split: `BOARD_USE`, `BOARD_EDIT`, `BOARD_FEDERATE`, `BOARD_MANAGER`
- Groups API renamed: `groups/{id}/roles` → `groups/{id}/permissions`, `Role` → `PermissionGrant`

#### Member Identity
- `uid UUID` column added to `station_member` with unique `(station_id, uid)` index
- `MemberIdentity(stationUid, memberUid)` record replaces dual local/federated member representation
- Dropped satellite federation tables: `federated_assignee`, `federated_creator`, `federated_comment_author`, `federated_watcher` for boards, events, news, KB
- `MemberIdentityFactory` service for enriching identities with display metadata
- `MemberNameResolver` with Caffeine caching replaces static `StationUidResolver`/`MemberUidResolver`
- Mention format migrated to `@[stationUid/memberUid:Name]` with legacy support
- Avatar URLs use `stationUid/memberUid` path segments

#### Group Colors & Tag Badges
- `MemberGroup` extended with `color` (hex) and `position` (priority) fields
- `UserTag` extended with `color`, `visible`, and `position` fields
- `MemberIdentity` API response includes `nameColor`, `displayTag`, `stationName`

#### Federation
- `FederationHttpClient` refactored to typed generic API (`get<T>`, `getList<T>`, `post`, `put`, `delete`)
- New tables: `event_comment_federated_author`, `news_comment_federated_author`, `kb_comment`, `kb_comment_federated_author`
- `news_federation_share` + `news_federation_share_target` tables with `NEWS_SHARE` capability
- Remote/proxy comment endpoints for events, news, and KB
- Federated board access: two-tier model with shared required role + local override
- Board `uid UUID` column for federation references
- `all_remote_member_id` columns migrated from TEXT to native PostgreSQL UUID

#### Frontend
- `LayeredEmberLogo.vue` composited from individual image fragments with `useEmberLogo` composable
- `SidebarCountService` + `useSidebarCounts` composable (replaces `usePendingChanges`)
- `RequirementsView.vue`, `FederatedDetailView.vue` (news), `FederatedEventDetailView.vue`, `UserTypePermissionsView.vue`
- `KbCommentSection.vue`, `EventCancelModal.vue`, `MemberName.vue` enriched
- Dev error reporter (`devErrorReporter.ts`) for in-app exception display in dev mode

#### Backend
- `SidebarCountService` — single endpoint returning all sidebar badge counts
- `EventThresholdChecker` — scheduled executor for auto-cancellation (every 30 minutes)
- `StationExportService`/`StationImportService` extended to cover 45+ tables for full station portability
- Caffeine cache dependency added (replaces `ConcurrentHashMap` caches)
- Demo seeding parallelized with `CompletableFuture` + `Executors.newVirtualThreadPerTaskExecutor()`; exchange/procurement code extracted to `seedExchanges()`/`seedProcurements()` helper methods

---

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
- **MentionedInComment** extended — `BOARD_TICKET` entity type with ticket-detail link
- **LaneData, AccessData, TicketLabelMapping** — extracted to top-level records by spotless

#### Frontend Architecture
- **15 new views**: BoardOverviewView, BoardListView, BoardView, TicketDetailView, BoardSettingsView, BacklogView, ArchivedView + 5 help center pages
- **7 new components**: TicketTile, TicketChecklist, TicketActivity, TicketLinksSection, LabelSelectInput, ColorInput, DragList (reused)
- **boards.ts API** — 40+ functions for all board, ticket, label, attachment, KB link, and history operations
- **Authenticated file handling** — blob download/preview via axios instead of direct URLs

#### Test Coverage
- **Board repository tests** — 20+ tests covering tickets, lanes, labels, attachments, field values, weblinks, search, history, backlog, KB links
- **Board service tests** — 25+ tests covering CRUD, access control with role hierarchy, labels, backlog, field values, attachments, comments, watchers, move/reorder/link operations
- **JaCoCo exclusion** — DueDateReminderChecker excluded (daemon pattern, like RegistrationDeadlineChecker)
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

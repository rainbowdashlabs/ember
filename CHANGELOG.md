# Changelog

## v26.5.0

### New Features

#### Domain Event System
- **Event-driven architecture** — new `DomainEventBus` with Guice multibinding decouples all notification logic from routes
- **17 event handlers** for all notification-producing operations (events, news, exchange, forms, procurement, lending, groups, comments)
- Services publish domain events after state changes; dedicated handlers create notifications
- Routes no longer depend on `NotificationService` directly

#### Comments & @Mentions
- **Event comments** — threaded comments with unlimited nesting depth on events
- **@mention autocomplete** — type `@` in any comment field to search and tag members, with styled inline chips in the editor
- **Mention rendering** — `@Name` tokens render as styled primary-colored text in both comment display and edit fields
- **MentionedInComment notifications** — mentioned members receive a notification
- **Reply notifications** — replying to a comment notifies the original comment author
- **Soft-delete** — deleting a comment with replies preserves the thread structure, showing "Dieser Kommentar wurde gelöscht" in place of the content
- **Privacy** — email addresses are never exposed in the mention autocomplete dropdown
- **News comment migration** — news comments now use the same generic `CommentThread` with full nesting and @mention support

#### Notes System
- **Entity notes** — diff-based versioned notes on inventory items, member profiles, and events
- **NoteEditor component** — markdown textarea with save tracking and version history panel
- **Event notes** — managers can add internal notes to events (visible in event detail view)
- **Manager-only visibility** — member profile notes are restricted to managers

#### User Feeds (iCal, RSS, Atom)
- **Personal iCal feed** — subscribe to your events in Thunderbird, Outlook, Google Calendar, or any calendar app
- **RSS 2.0 and Atom 1.0 notification feeds** — follow your notifications in any feed reader
- **Feed token management** — generate, regenerate, or revoke your feed token
- **Per-type feed toggle** — enable or disable feed inclusion per notification type
- **Dashboard feed integration** — CTA banners on the notifications and upcoming events panels when feeds are not configured or inactive (not polled in 24h)
- **Feed activity tracking** — feed poll timestamps tracked to detect inactive subscriptions
- **Help center guides** — platform-specific setup instructions for iCal and RSS/Atom readers

#### Event Templates
- **Templates replace layouts** — reusable event templates are now a superset of the old layout system
- **Full template editor** — dedicated page for creating/editing templates with field configuration
- **Attendance template and registration limit** — templates can include attendance settings and participant caps
- **Load Template dropdown** — populate an event form from a saved template in one click
- **Quick field templates** — Ort, Treffpunkt, and Thema quick-add buttons in the field editor with public/internal defaults

#### Federated Events
- **Cross-station event sharing** — share events with federation partners (local and remote)
- **Event sharing toggle** — enable/disable federation sharing per event in the event edit view
- **Federated events section** — partner station events shown on the upcoming events page
- **Remote registration** — register for events at partner stations via signed HTTP
- **Webhook notifications** — real-time status updates for registration acceptance/denial and member name changes
- **EVENT_SHARE capability** — control event sharing per federation partner

#### Federated Knowledge Base
- **Shared KB browsing** — federated KB files and folders from partner stations shown on the knowledge base page
- **Federated search** — KB search queries partner stations in parallel and merges results
- **Partner station filter** — filter knowledge base view to show only content from a specific partner
- **Remote search endpoint** — `GET /federation/remote/kb/search` for cross-instance KB search

#### Public Calendar & Station View
- **Public calendar** — stations can expose a public event calendar for unauthenticated visitors
- **Public station shell** — unified `/public/station/:uid` layout with calendar and knowledge base tabs
- **Discovery integration** — discoverable stations link to their public station view
- **Public event fields** — individual event fields can be marked as public or internal

#### Event Category Management
- **Dedicated category view** — create, edit, reorder, and delete event categories
- **Max shown events** — configure how many events each category shows on the index view
- **Public toggle** — mark categories as public for the public calendar

#### Registration Rework
- **Grouped registrations view** — registrations grouped by event, sorted by deadline
- **Expandable fairness table** — acceptance/denial ratio per member for fair decision-making
- **Registration limit** — cap the number of accepted registrations per event
- **Deadline notifications** — managers are notified when a registration deadline expires with pending registrations

#### Inventory Item Detail View
- **Item detail page** — view item metadata, current assignment, full assignment history, and manager notes

#### Theming
- **Color blind themes** — new accessible theme options
- **Fire theme** — new color theme
- **Three-axis theming** — Theme (color) × Variant (light/dark) × Feel (rounded/corners)
- **Hierarchical theme settings** — instance → station → user, each level can lock for the level below
- **ThemeSelector component** — visual theme picker with live preview
- **Station theme management** — dedicated station settings view for theme and feel configuration

#### Problem Reports
- **Report problem button** — floating bug icon on all station pages to report issues
- **Auto-captured context** — page URL, user roles, last 20 API requests with status/duration, browser info, screen size
- **Admin review** — `/admin/problem-reports` view with expandable details, request history table, acknowledge/delete actions

#### Admin Settings
- **Legal document management** — dedicated admin view for editing privacy policy, terms of service, consent text, and imprint
- **Mailing configuration** — dedicated admin view for SMTP settings

### Improvements

#### Help Center
- New help pages for theming, sessions, notifications, modules, import, federation settings, comments, templates, notes, item detail view, event categories, legal, and mailing
- RSS/Atom and iCal setup guides with platform-specific instructions (Thunderbird, Outlook, Android, iOS)

#### Frontend
- **InfiniteReel component** — animated infinite scrolling display
- **PublicEventList component** — event listing for public views
- **DiffView component** — shared unified diff renderer with compact and line-number modes, used by KB version history and note editor
- **News detail view** — dedicated `/station/news/:id` page with always-visible comments, linked from notifications
- **Comment highlight** — `?comment=123` query param scrolls to and highlights the specific comment
- **Sidebar rebuild** — section headers are now navigable (Dashboard, News, Profile, Members, Attendance, Events, Forms, Lost & Found, Knowledge Base merge their default child into the header)
- **Collapsible sidebar groups** — arrow hidden when section has no children
- **Member completions endpoint** — lightweight `GET /station-members/completions` (LOGIN role) returns only id + name for comment autocomplete
- **Item detail links** — item names in inventory tables link to the item detail view
- **Dashboard navigation** — clicking events/registrations navigates to the specific event, notifications auto-acknowledge on click
- **Admin settings refactored** — split into focused sub-views (legal, mailing, general)
- **Station settings refactored** — split into focused sub-views (theme, modules, federation, discovery)
- **Landing page update** — improved visual presentation

#### Backend
- `DemoService` migrated to use services instead of repositories for operations that produce domain events
- `ConsentService` extended with imprint document retrieval and version tracking
- Form service now publishes `FormPublished` and `FormDeleted` domain events
- Absences endpoint now accessible to both `EVENT_MANAGER` and `ATTENDANCE_MANAGER` roles

#### Code Quality
- **Typed config records** — all `String config` fields replaced with typed records (`ProfileFieldConfig`, `EventFieldConfig`, `AttendanceFieldConfig`, `FormQuestionConfig`, `WaitingListFieldConfig`) with `parse()`/`toJson()` methods
- **Typed enum fields** — all `String *Type` fields replaced with proper enums (`ProfileFieldType`, `EventFieldType`, `AttendanceFieldType`, `NoteEntityType`, `CommentEntityType`, `FilterTableType`, `ContentType`, `ChangeType`)
- **Quiz config as objects** — `QuizService.createQuestion()` accepts `QuestionConfig` instead of raw JSON strings

### Infrastructure

- **JaCoCo coverage enforcement** — 95% line coverage for repositories, 90% for services
- **Parallel CI test jobs** — repository, service, and other tests run in separate GitHub Actions jobs for faster feedback
- **Coverage verification in CI** — JaCoCo exec artifacts uploaded and merged across parallel test jobs
- **Javadoc verification** — dedicated CI job validates javadoc generation
- **Split Gradle test tasks** — `testRepositories`, `testServices`, `testOther` for parallel local and CI execution
- **Comprehensive test suite** — new service tests for attendance, auth, batch events, comments, consent, event federation, event fields, event layouts, event templates, feed tokens, forms, knowledge base, notes, profile fields, quiz, registration codes, station applications, test protocols, user settings
- **Database patch 5** — public columns for stations, event categories, events, event fields, boards, problem reports, feed poll tracking

### Bug Fixes

- Fixed @mention regex mismatch — backend pattern now matches the `@[id:name]` format sent by the frontend
- Fixed comment deletion cascading to child comments — now soft-deletes when children exist
- Fixed news author being notified on every comment — only the replied-to comment author is notified
- Fixed KB share link generating wrong URL — now correctly links to `/public/station/:uid/knowledge/file/:id`
- Fixed federated shared files being clickable (navigating to non-existent local file) — removed broken navigation
- Fixed absences section visible to non-managers on event detail view
- Fixed unnecessary 403 requests for absences from non-manager users
- Fixed past event registrations appearing on the dashboard
- Fixed `Modal` component warnings from incorrect `show`/`close` prop usage
- Fixed event layouts being a separate concept from templates — consolidated into a single template system
- Fixed notification creation being coupled to route handlers — moved to domain event handlers
- Fixed news comment rendering using inline logic — migrated to shared CommentThread component

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

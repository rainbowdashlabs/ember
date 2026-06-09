# Ember

Ember is an all-in-one platform for managing youth groups (Jugendfeuerwehren). It handles attendance, events, inventory, members, forms, news, knowledge base, quizzes, test protocols, boards, federation, waiting lists, and more — all in a single self-hosted application.

## Quick Start (Docker)

### Prerequisites

- Docker and Docker Compose
- A domain with HTTPS (recommended for production)

### Production Setup

1. **Create a project directory and copy the compose file:**

```bash
mkdir ember && cd ember
```

2. **Create `compose.yaml` as defined [here](/docker/compose.prod.yaml) or create your own.**

3. **Start the services:**

```bash
docker compose up -d
```

4. **Check the logs for the admin credentials:**

```bash
docker compose logs ember | grep -A 5 "Default admin"
```

On first startup, Ember creates a default admin account and a default station. The auto-generated password is printed to the logs. You will be required to change it on first login.

5. **Access the application** at `http://localhost:8000` or your configured domain.

### Reverse Proxy

In production, place Ember behind a reverse proxy (nginx, Caddy, Traefik) with HTTPS. Update the `baseUrl` in the config to match your public URL:

```yaml
# config/config.yaml
api:
  baseUrl: "https://ember.yourdomain.com"
```

If you use Cloudflare as proxy, Ember automatically reads the `CF-IPCountry` header to display session locations.

## Configuration

Ember uses YAML configuration via [Ocular](https://github.com/rainbowdashlabs/ocular). The config file is auto-generated at `config/config.yaml` on first startup. All config values can be overridden via environment variables.

### Environment Variables

#### Database

| Variable      | Description        | Default     |
|---------------|--------------------|-------------|
| `DB_HOST`     | PostgreSQL host    | `localhost` |
| `DB_PORT`     | PostgreSQL port    | `5432`      |
| `DB_USER`     | Database user      | `ember`     |
| `DB_PASSWORD` | Database password  | `ember`     |
| `DB_DATABASE` | Database name      | `ember`     |
| `DB_SCHEMA`   | Schema name        | `ember`     |

#### API

| Variable                | Description                       | Default                       |
|-------------------------|-----------------------------------|-------------------------------|
| `API_HOST`              | Listen address                    | `0.0.0.0`                     |
| `API_PORT`              | Listen port                       | `8080`                        |
| `API_BASEURL`           | Public URL (for emails and links) | `http://localhost:5173`       |
| `API_DEMOURL`           | URL to link to a demo instance    | `https://demo.ember-panel.de` |
| `API_MAXIMAGESIZEBYTES` | Max upload size in bytes          | `5242880` (5 MB)              |

#### Authentication

| Variable                  | Description                     | Default |
|---------------------------|---------------------------------|---------|
| `AUTH_SESSIONMINUTES`     | Session duration in minutes     | `30`    |
| `AUTH_TOKENBYTES`         | Token entropy in bytes          | `32`    |
| `AUTH_PASSWORDTOKENHOURS` | Password reset token validity   | `72`    |
| `AUTH_VERIFYTOKENHOURS`   | Email verification validity     | `24`    |

#### Mailing

| Variable                                    | Description                        | Default  |
|---------------------------------------------|------------------------------------|----------|
| `MAILING_PROVIDER`                          | Mail provider (`SMTP`)             | `SMTP`   |
| `MAILING_SENDERADDRESS`                     | Sender email address               | (empty)  |
| `MAILING_SENDERNAME`                        | Sender display name                | `Ember`  |
| `MAILING_SMTP_HOST`                         | SMTP host                          | (empty)  |
| `MAILING_SMTP_PORT`                         | SMTP port                          | `665`    |
| `MAILING_SMTP_SSL`                          | Use SSL                            | `false`  |
| `MAILING_USER`                              | SMTP username                      | (empty)  |
| `MAILING_PASSWORD`                          | SMTP password                      | (empty)  |
| `MAILING_DAILYSENDLIMIT`                    | Daily email send limit             | `200`    |
| `MAILING_NOTIFICATIONDIGESTINTERVALMINUTES` | Notification digest interval (min) | `60`     |

#### Theming

| Variable               | Description                            | Default   |
|------------------------|----------------------------------------|-----------|
| `THEMING_DEFAULTTHEME` | Default color theme                    | `ember`   |
| `THEMING_DEFAULTFEEL`  | Default UI style (`ROUNDED`/`CORNERS`) | `ROUNDED` |
| `THEMING_LOCKFEEL`     | Lock feel at instance level            | `false`   |

#### Demo

| Variable                    | Description                              | Default |
|-----------------------------|------------------------------------------|---------|
| `DEMO_ENABLED`              | Enable demo mode                         | `false` |
| `DEMO_DEV`                  | Dev mode (demo accounts without reset)   | `false` |
| `DEMO_RESETINTERVALHOURS`   | How often demo data resets               | `1`     |

#### Tools

| Variable     | Description                         | Default   |
|--------------|-------------------------------------|-----------|
| `TYPST_BIN`  | Path to typst binary (PDF export)   | `typst`   |
| `PANDOC_BIN` | Path to pandoc binary (doc import)  | `pandoc`  |

### Full Config Reference

```yaml
api:
  host: "0.0.0.0"
  port: 8080
  baseUrl: "https://..."
  demoUrl: ""
  maxImageSizeBytes: 5242880
  privacyPolicyDir: "data/privacy"
  consentDir: "data/consent"
  tosDir: "data/tos"
  imprintDir: "data/imprint"

auth:
  sessionMinutes: 30
  tokenBytes: 32
  passwordTokenHours: 72
  verifyTokenHours: 24

database:
  host: "localhost"
  port: "5432"
  user: "ember"
  password: "ember"
  database: "ember"
  schema: "ember"
  poolSize: 5

demo:
  enabled: false
  dev: false
  resetIntervalHours: 1

mailing:
  provider: "SMTP"
  senderAddress: ""
  senderName: "Ember"
  smtp:
    host: ""
    port: 665
    ssl: false
  user: ""
  password: ""
  dailySendLimit: 200
  notificationDigestIntervalMinutes: 60

theming:
  defaultTheme: "ember"
  defaultFeel: "ROUNDED"
  lockFeel: false
```

## Email Setup

Ember can send emails for account verification, password resets, and notifications. Configure the SMTP settings in `config/config.yaml`:

```yaml
mailing:
  provider: "SMTP"
  senderAddress: "noreply@yourdomain.com"
  senderName: "Ember"
  smtp:
    host: "smtp.yourdomain.com"
    port: 587
    ssl: false
  user: "your-smtp-user"
  password: "your-smtp-password"
```

Without email configuration, Ember still works — users just can't self-register or reset passwords. Admins can invite users and force password changes instead.

## Legal Documents (Privacy Policy, ToS, Consent)

Ember ships with default legal documents in German and English. There are four document types: **Privacy Policy**, **Terms of Service**, **Consent** (shown before login), and **Imprint**.

### Editing via Admin UI

Legal documents are managed through the admin interface at `/admin/settings/legal`:

1. Select the document type (Privacy, ToS, Consent, Imprint)
2. Select the language (DE, EN, or add new languages)
3. Each document consists of one or more markdown sections (files), all shown on the same page
4. Sections can be **reordered** (up/down arrows), **enabled/disabled** (toggle), and **deleted**
5. Disabled sections are preserved on disk but excluded from the rendered document shown to users
6. Use the preview button to see the combined rendered result
7. Save — Ember automatically versions the document and prompts users to re-consent

### File Structure

Documents are stored as numbered markdown files on disk. The admin UI manages these files automatically:

```
data/
  privacy/
    de/
      01-general.md           # Enabled section
      02-rights.md            # Enabled section
      _03-experimental.md     # Disabled section (underscore prefix)
    en/
      01-general.md
      02-rights.md
  consent/
    de/01-consent.md
    en/01-consent.md
  tos/
    de/01-terms.md
    en/01-terms.md
  imprint/
    de/01-imprint.md
    en/01-imprint.md
  images/               # Uploaded images (avatars, KB icons, quiz images)
  kb-files/             # Knowledge base binary files (PDFs, etc.)
```

Files prefixed with `_` are disabled and excluded from rendering. The numeric prefix (`01-`, `02-`, ...) determines the order.

### Version Tracking

Ember automatically tracks changes to legal documents:

- On each startup (and on each save via the admin UI), the content is hashed and compared against `data/{type}/version.txt`
- If content changed: the old version is archived to `data/{type}/history/`, a diff is generated, and `version.txt` is updated
- **Users are automatically prompted to re-consent** when any document changes
- All consent records are stored in the database with timestamps, IP addresses, and document versions for GDPR compliance

The `version.txt` and `history/` directory are auto-managed — do not edit them manually.

## Permissions

Ember uses a hierarchical permission system. Permissions are assigned per member at the station level. Higher permissions implicitly grant their children.

### Top-Level Permissions

| Permission               | Description                                   | Key Children                                                                |
|--------------------------|-----------------------------------------------|-----------------------------------------------------------------------------|
| `STATION_ADMINISTRATOR`  | Full station access — grants all permissions  | All manager permissions below                                               |
| `LOGIN`                  | Can log in to the station                     | `USER`                                                                      |

### Feature Managers

Each feature area has a manager permission that grants all sub-permissions within that area:

| Manager                  | Description                  | Key Sub-Permissions                                                                                                                                   |
|--------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ATTENDANCE_MANAGER`     | Full attendance access       | `ATTENDANCE_EDIT`, `ATTENDANCE_READ`, `ATTENDANCE_CONFIGURE`, `ATTENDANCE_EXPORT`                                                                     |
| `INVENTORY_MANAGER`      | Full inventory access        | `INVENTORY_EDIT`, `INVENTORY_CREATE`, `INVENTORY_READ`, `INVENTORY_CHECK`, `INVENTORY_EXCHANGE`, `INVENTORY_LENDING_MANAGER`, `INVENTORY_PROCUREMENT` |
| `EVENT_MANAGER`          | Full event access            | `EVENT_EDIT`, `EVENT_REGISTRATION`, `EVENT_MANAGE_TEMPLATE`, `EVENT_MANAGE_CATEGORY`, `EVENTS_FEDERATE`                                               |
| `MEMBER_MANAGER`         | Full member access           | `MEMBER_EDIT`, `MEMBER_READ`, `MEMBER_CHANGES`, `MEMBER_EXPORT`, `MEMBER_FIELDS`, `MEMBER_MANAGE_GROUP`, `MEMBER_MANAGE_TAGS`, `MEMBER_NOTES`         |
| `WAITLIST_MANAGER`       | Full waiting list access     | `WAITLIST_EDIT`, `WAITLIST_ADD`, `WAITLIST_READ`                                                                                                      |
| `NEWS_MANAGER`           | Full news access             | `NEWS_EDIT`, `NEWS_FEDERATE`                                                                                                                          |
| `POLL_MANAGER`           | Full forms/survey access     | `POLL_CREATE`, `POLL_VIEW_RESULTS`                                                                                                                    |
| `TEST_MANAGER`           | Full quiz/test access        | `TEST_CONFIGURE`, `TEST_CATALOG_EDIT`, `TEST_CATALOG_VIEW`, `TEST_REVIEW`, `TEST_RESULT_READ`                                                         |
| `PROTOCOL_MANAGER`       | Full test protocol access    | `PROTOCOL_CONFIGURE`, `PROTOCOL_CREATE`, `PROTOCOL_TESTER`                                                                                            |
| `BOARD_MANAGER`          | Full board access            | `BOARD_EDIT`, `BOARD_USE`, `BOARD_FEDERATE`                                                                                                           |
| `KNOWLEDGE_MANAGER`      | Full knowledge base access   | `KNOWLEDGE_EDIT`, `KNOWLEDGE_FEDERATE`                                                                                                                |
| `LOST_AND_FOUND_MANAGER` | Full lost & found access     | `LOST_AND_FOUND_MANAGE`, `LOST_AND_FOUND_CREATE`                                                                                                      |
| `STATION_MANAGER`        | Full station settings access | `STATION_GENERAL`, `STATION_LOOK_AND_FEEL`, `STATION_MAIL`, `STATION_MODULES`, `STATION_IMPORT_EXPORT`, `STATION_FEDERATION`, `STATION_STATISTICS`    |

### Special Roles

| Permission        | Description                                             |
|-------------------|---------------------------------------------------------|
| `MEMBER_GUARDIAN` | Parent/guardian — can manage linked children's profiles |

Permissions can also be assigned to entire user types (Trial, Member, Guardian, Team, Manager) station-wide via the user type permissions management page.

## Backup

Back up the PostgreSQL database regularly:

```bash
docker compose exec postgres pg_dump -U ember ember > backup.sql
```

The `config/` and `data/` directories should also be backed up as they contain your configuration, legal documents, uploaded images, and knowledge base files.

## Development

### Prerequisites

- JDK 25
- Node.js 24
- PostgreSQL 17
- Optional: typst (PDF export), pandoc (document import)

### Backend

```bash
./gradlew build          # Build and test
./gradlew run            # Run the backend
```

### Frontend

```bash
cd frontend
npm install
npm run dev              # Start dev server (http://localhost:5173)
```

### Dev Mode

Use `docker/compose.dev.yaml` to run with demo accounts but without periodic data resets:

```bash
docker compose -f docker/compose.dev.yaml up --build
```

## License

AGPL-3.0-only — see [LICENSE](LICENSE) for details.

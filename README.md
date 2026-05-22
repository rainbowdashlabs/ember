# Ember

Ember is an all-in-one platform for managing youth groups (Jugendfeuerwehren). It handles attendance, events, inventory, members, forms, news, and more — all in a single self-hosted application.

## Quick Start (Docker)

### Prerequisites

- Docker and Docker Compose
- A domain with HTTPS (recommended for production)

### Production Setup

1. **Create a project directory and copy the compose file:**

```bash
mkdir ember && cd ember
```

2. **Create `compose.yaml` as defined in [here](/docker/compose.prod.yaml) or create your own:**

3. **Start the services:**

```bash
docker compose up -d
```

4. **Check the logs for the admin credentials:**

```bash
docker compose logs ember | grep -A 5 "Default admin"
```

On first startup, Ember creates a default admin account and a default station. The auto-generated password is printed to the logs. You will be required to change it on first login.

5. **Access the application** at `http://localhost:8000` or your defined domain

### Reverse Proxy

In production, place Ember behind a reverse proxy (nginx, Caddy, Traefik) with HTTPS. Update the `baseUrl` in the config to match your public URL:

```yaml
# config/config.yaml
api:
  baseUrl: "https://ember.yourdomain.com"
```

If you use Cloudflare as proxy, Ember automatically reads the `CF-IPCountry` header to display session locations.

## Configuration

Ember uses YAML configuration via [Ocular](https://github.com/rainbowdashlabs/ocular). The config file is auto-generated at `config/config.yaml` on first startup.

### Environment Variables

Database settings can be overridden via environment variables:

| Variable       | Description                                   | Default     |
|----------------|-----------------------------------------------|-------------|
| `DB_HOST`      | PostgreSQL host                               | `localhost` |
| `DB_PORT`      | PostgreSQL port                               | `5432`      |
| `DB_USER`      | Database user                                 | `ember`     |
| `DB_PASSWORD`  | Database password                             | `ember`     |
| `DB_DATABASE`  | Database name                                 | `ember`     |
| `DB_SCHEMA`    | Schema name                                   | `ember`     |
| `DEMO_ENABLED` | Enable demo mode                              | `false`     |
| `DEMO_DEV`     | Enable dev mode (demo accounts without reset) | `false`     |
| `TYPST_BIN`    | Path to typst binary (for PDF export)         | `typst`     |
| `PANDOC_BIN`   | Path to pandoc binary (for document import)   | `pandoc`    |

### Full Config Reference

```yaml
api:
  host: "0.0.0.0"          # Listen address
  port: 8080                # Listen port
  baseUrl: "https://..."    # Public URL (for emails and links)
  demoUrl: ""               # URL to link to a demo instance
  maxAvatarSizeBytes: 2097152  # 2 MB
  privacyPolicyDir: "data/privacy"
  consentDir: "data/consent"
  tosDir: "data/tos"
  imprintDir: "data/imprint"

auth:
  sessionMinutes: 30        # Session duration
  tokenBytes: 32            # Token entropy
  passwordTokenHours: 72    # Password reset token validity
  verifyTokenHours: 24      # Email verification token validity

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
  resetIntervalHours: 1     # How often demo data resets

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

Ember ships with default legal documents in German and English. These are composed from markdown files and served as HTML to users.

### Directory Structure

```
data/
  privacy/              # Privacy policy
    de/
      01-general.md
      02-rights.md
    en/
      01-general.md
      02-rights.md
  consent/              # Consent banner (shown before login)
    de/01-consent.md
    en/01-consent.md
  tos/                  # Terms of service
    de/01-nutzungsbedingungen.md
    en/01-terms.md
  imprint/              # Imprint / legal notice
    de/01-impressum.md
    en/01-imprint.md
  images/               # Uploaded images (avatars, KB icons, quiz images)
  kb-files/             # Knowledge base binary files (PDFs, etc.)
```

Files within a locale directory are sorted alphabetically and concatenated to form the full document.

### Customizing Legal Documents

1. Mount `./data:/app/data` in your compose file (included by default)
2. On first startup, the default files are available in the `data/` directory
3. Edit the markdown files as needed
4. Restart the application

### Version Tracking

Ember automatically tracks changes to legal documents:

- On each startup, the content is hashed and compared against `data/{type}/version.txt`
- If content changed: the old version is archived to `data/{type}/history/`, a diff is generated, and `version.txt` is updated
- **Users are automatically prompted to re-consent** when any document changes
- All consent records are stored in the database with timestamps, IP addresses, and document versions for GDPR compliance

The `version.txt` and `history/` directory are auto-managed — do not edit them manually.

## Roles

Ember uses a hierarchical role system:

| Role                    | Description                                            |
|-------------------------|--------------------------------------------------------|
| `ADMIN`                 | Full platform admin (account-level, not station-bound) |
| `MANAGER`               | Station manager — includes all management roles        |
| `TEAM`                  | Team member — access to management features            |
| `MEMBER_MANAGEMENT`     | Manage members, groups, tags                           |
| `ATTENDENCE_MANAGEMENT` | Manage attendance templates and sessions               |
| `EVENT_MANAGEMENT`      | Manage events and registrations                        |
| `INVENTORY_MANAGEMENT`  | Manage inventory items and assignments                 |
| `NEWS_MANAGEMENT`       | Manage news posts                                      |
| `POLL_MANAGEMENT`       | Manage forms and surveys                               |
| `QUIZ_MANAGEMENT`       | Manage quiz catalogs, tests, and AI generation         |
| `KNOWLEDGE_MANAGEMENT`  | Manage knowledge base files and folders                |
| `WAITLIST_MANAGEMENT`   | Manage waiting lists and entries                       |
| `GUARDIAN`              | Parent/guardian — can manage assigned children         |
| `MEMBER`                | Regular group member                                   |
| `LOGIN`                 | Can log in (base role)                                 |

Higher roles inherit lower ones (e.g. `MANAGER` includes `TEAM`, which includes all `*_MANAGEMENT` roles).

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

#!/usr/bin/env bash
#
# Ember installer.
#
#   wget -qO- https://ember-panel.de/install.sh | bash
#   curl -fsSL https://ember-panel.de/install.sh | bash
#
# Writes a compose file into the current directory, brings the stack up and shows the login it
# created. Every question can be answered ahead of time with an environment variable, which is what
# makes the same script usable unattended:
#
#   curl -fsSL https://ember-panel.de/install.sh \
#     | EMBER_MODE=port EMBER_PORT=8080 EMBER_ASSUME_YES=1 bash
#
# Or click the answers together on https://ember-panel.de/install and pass the code it gives you:
#
#   curl -fsSL https://ember-panel.de/install.sh | bash -s ABC123
#
set -euo pipefail

# Read from the terminal rather than from stdin. Piped into bash, stdin is the script itself, so a
# plain `read` would swallow the rest of the script instead of waiting for an answer.
# Tested by opening it, not by asking whether it exists: without a controlling terminal /dev/tty is
# there and still cannot be opened, and a failed redirection would take the whole script with it.
if { exec 3< /dev/tty; } 2> /dev/null; then
    INTERACTIVE=1
else
    INTERACTIVE=0
fi

readonly BOLD=$'\033[1m' DIM=$'\033[2m' RED=$'\033[31m' GREEN=$'\033[32m' YELLOW=$'\033[33m' OFF=$'\033[0m'

EMBER_SOURCE="${EMBER_SOURCE:-https://ember-panel.de}"

say()  { printf '%s\n' "$*"; }
step() { printf '\n%s==>%s %s%s\n' "$GREEN" "$OFF" "$BOLD" "$*$OFF"; }
warn() { printf '%s!%s  %s\n' "$YELLOW" "$OFF" "$*" >&2; }
die()  { printf '%s✖%s  %s\n' "$RED" "$OFF" "$*" >&2; exit 1; }

# Asks a question, with a default. Falls back to the default when nothing is attached to answer,
# which is what makes the unattended form work without a separate code path.
ask() {
    local prompt="$1" default="${2-}" answer=""
    if [ "$INTERACTIVE" = 0 ]; then
        printf '%s' "$default"
        return
    fi
    printf '%s%s%s' "$prompt" "${default:+ ${DIM}[${default}]${OFF}}" ': ' >&2
    IFS= read -r answer <&3 || true
    printf '%s' "${answer:-$default}"
}

ask_yes_no() {
    local prompt="$1" default="$2" answer
    answer=$(ask "$prompt (y/n)" "$default")
    # German answers are taken as well: the pages are German and whoever installs this may answer
    # the way they read.
    case "${answer,,}" in
        y | yes | j | ja | true | 1) return 0 ;;
        *) return 1 ;;
    esac
}

ask_choice() {
    local prompt="$1" default="$2"; shift 2
    local options=("$@") answer
    if [ "$INTERACTIVE" = 1 ]; then
        printf '\n%s\n' "$prompt" >&2
        local i=1
        for option in "${options[@]}"; do
            printf '  %s) %s\n' "$i" "${option#*:}" >&2
            i=$((i + 1))
        done
    fi
    while :; do
        answer=$(ask "Choice" "$default")
        local i=1
        for option in "${options[@]}"; do
            if [ "$answer" = "$i" ] || [ "$answer" = "${option%%:*}" ]; then
                printf '%s' "${option%%:*}"
                return
            fi
            i=$((i + 1))
        done
        [ "$INTERACTIVE" = 0 ] && die "Unknown choice: $answer"
        warn "Please pick one of the choices offered."
    done
}

list_networks() {
    [ "$INTERACTIVE" = 0 ] && return
    printf '\n%s%s%s\n' "$DIM" "$1:" "$OFF" >&2
    docker network ls --format '  {{.Name}}' 2>/dev/null | grep -v '^  bridge$\|^  host$\|^  none$' >&2 || true
}

# Makes sure a network is there to join, creating it when it is not. A compose file that names an
# external network which does not exist fails at `up` with a message about the network, several
# steps after the point where the name was actually chosen.
ensure_network() {
    local name="$1" why="$2"
    docker network inspect "$name" > /dev/null 2>&1 && return
    warn "There is no network called '$name' yet. $why."
    if ask_yes_no "Create it now?" "y"; then
        docker network create "$name" > /dev/null || die "Could not create the network '$name'."
        say "  Created the network '$name'."
    else
        warn "The stack will not start without it: docker network create $name"
    fi
}

random_secret() {
    if command -v openssl > /dev/null 2>&1; then
        openssl rand -base64 24 | tr -d '/+=' | cut -c1-24
    else
        head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' | cut -c1-24
    fi
}

# --- A code from the install page ----------------------------------------------------------------

# The answers clicked together on the website. They arrive as assignments and are read into the
# environment, where they take the same path as any other answer given ahead of time: nothing below
# knows or cares where they came from.
EMBER_CODE="${EMBER_CODE:-${1-}}"
if [ -n "$EMBER_CODE" ]; then
    step "Fetching the answers behind code $EMBER_CODE"
    fetch=""
    if command -v curl > /dev/null 2>&1; then
        fetch=$(curl -fsSL "$EMBER_SOURCE/api/v1/public/install/$EMBER_CODE" 2> /dev/null) || fetch=""
    elif command -v wget > /dev/null 2>&1; then
        fetch=$(wget -qO- "$EMBER_SOURCE/api/v1/public/install/$EMBER_CODE" 2> /dev/null) || fetch=""
    fi
    [ -z "$fetch" ] && die "Code '$EMBER_CODE' is unknown or has expired. A new one is made at $EMBER_SOURCE/install."

    while IFS='=' read -r key value; do
        case "$key" in
            EMBER_*) export "$key=$value" ;;
        esac
    done <<< "$fetch"

    say "  Took $(printf '%s\n' "$fetch" | grep -c .) answers. Everything else is asked below."
fi

# --- What is already here ---------------------------------------------------------------------

step "Checking what is here"

command -v docker > /dev/null 2>&1 || die "Docker is not installed. https://docs.docker.com/engine/install/"
docker compose version > /dev/null 2>&1 || die "The docker compose plugin is missing. https://docs.docker.com/compose/install/"
docker info > /dev/null 2>&1 || die "Docker is not running, or this user is not allowed to talk to it."

say "Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null || echo '?') is ready."
say "Installing into: ${BOLD}$PWD${OFF}"

COMPOSE_FILE="${EMBER_COMPOSE_FILE:-compose.yaml}"
if [ -e "$COMPOSE_FILE" ]; then
    ask_yes_no "$COMPOSE_FILE already exists and will be overwritten. Carry on?" "n" \
        || die "Stopped. Nothing was changed."
fi

# --- How it should be reachable ---------------------------------------------------------------

step "How it is reached"

say "${DIM}The frontend server hands /api to the backend itself, so a port on this machine is all"
say "that is needed. Traefik is for a name and a certificate in front of it.${OFF}"

MODE="${EMBER_MODE:-}"
if [ -z "$MODE" ]; then
    MODE=$(ask_choice "How should Ember be reached?" "port" \
        "port:A port on this machine, reached directly or over a VPN." \
        "traefik:An existing Traefik with Let's Encrypt, the way ember-panel.de runs.")
fi

HOST_PORT="${EMBER_PORT:-8080}"
BIND_ADDRESS="${EMBER_BIND:-}"
EMBER_HOSTNAME="${EMBER_HOST:-}"
TRAEFIK_NETWORK="${EMBER_TRAEFIK_NETWORK:-traefik}"
TRAEFIK_RESOLVER="${EMBER_TRAEFIK_RESOLVER:-letsencrypt}"
TRAEFIK_ENTRYPOINT="${EMBER_TRAEFIK_ENTRYPOINT:-websecure}"

if [ "$MODE" = "traefik" ]; then
    while [ -z "$EMBER_HOSTNAME" ]; do
        EMBER_HOSTNAME=$(ask "Hostname Traefik listens for (e.g. ember.example.org)" "")
        [ -z "$EMBER_HOSTNAME" ] && [ "$INTERACTIVE" = 0 ] && die "EMBER_HOST is required for the Traefik arrangement."
    done
    list_networks "Docker networks that already exist"
    TRAEFIK_NETWORK=$(ask "Docker network Traefik runs in" "$TRAEFIK_NETWORK")
    ensure_network "$TRAEFIK_NETWORK" "Traefik reaches Ember only through it"
    TRAEFIK_ENTRYPOINT=$(ask "Traefik entrypoint" "$TRAEFIK_ENTRYPOINT")
    TRAEFIK_RESOLVER=$(ask "Traefik certificate resolver" "$TRAEFIK_RESOLVER")
    BASE_URL="https://$EMBER_HOSTNAME"
else
    HOST_PORT=$(ask "Port on this machine" "$HOST_PORT")
    # Which address the port is opened on. Left as it is, docker opens it on every interface the
    # machine has, which is not what somebody reaching this over a VPN or from the machine itself
    # is asking for.
    say ""
    say "${DIM}Empty opens the port on every interface. 127.0.0.1 keeps it on this machine, and the"
    say "address of a VPN interface keeps it on that network.${OFF}"
    BIND_ADDRESS=$(ask "Address to open the port on" "${EMBER_BIND:-}")
    [[ "$HOST_PORT" =~ ^[0-9]+$ ]] || die "Not a valid port: $HOST_PORT"
    EMBER_HOSTNAME="${EMBER_HOSTNAME:-localhost}"
    EMBER_HOSTNAME=$(ask "Address this instance is reached at" "$EMBER_HOSTNAME")
    BASE_URL="http://$EMBER_HOSTNAME:$HOST_PORT"
fi

BASE_URL=$(ask "Public address, which is what the links in emails are built from" "$BASE_URL")

# --- The rest ------------------------------------------------------------------------------------

step "Database"

DB_MODE="${EMBER_DB_MODE:-}"
if [ -z "$DB_MODE" ]; then
    DB_MODE=$(ask_choice "Where does PostgreSQL come from?" "bundled" \
        "bundled:Brought along. Started here as a container." \
        "external:Already running somewhere else and only joined.")
fi

DB_HOSTNAME="${EMBER_DB_HOST:-postgres}"
DB_PORT="${EMBER_DB_PORT:-5432}"
DB_USER="${EMBER_DB_USER:-ember}"
DB_NAME="${EMBER_DB_NAME:-ember}"
DB_SCHEMA="${EMBER_DB_SCHEMA:-ember_schema}"
DB_PASSWORD="${EMBER_DB_PASSWORD:-}"
DB_NETWORK="${EMBER_DB_NETWORK:-}"
EXPOSE_DB=0

if [ "$DB_MODE" = "external" ]; then
    DB_HOSTNAME=$(ask "Database host, as the container reaches it" "$DB_HOSTNAME")
    DB_PORT=$(ask "Port" "$DB_PORT")
    DB_NAME=$(ask "Database" "$DB_NAME")
    DB_USER=$(ask "User" "$DB_USER")
    while [ -z "$DB_PASSWORD" ]; do
        DB_PASSWORD=$(ask "Password" "")
        [ -z "$DB_PASSWORD" ] && [ "$INTERACTIVE" = 0 ] && die "EMBER_DB_PASSWORD is required for a database that already exists."
    done
    DB_SCHEMA=$(ask "Schema" "$DB_SCHEMA")
    say ""
    say "${DIM}A database running in docker is reachable only over a network they share. One on the host"
    say "or on another machine needs none, so leave this empty.${OFF}"
    list_networks "Docker networks that already exist"
    DB_NETWORK=$(ask "Existing network to the database (empty if none is needed)" "$DB_NETWORK")
    [ -n "$DB_NETWORK" ] && ensure_network "$DB_NETWORK" "Ember reaches the database only through it"
else
    DB_PASSWORD="${DB_PASSWORD:-$(random_secret)}"
    if [ -z "${EMBER_EXPOSE_DB:-}" ]; then
        ask_yes_no "Publish the database port on this machine? Only needed to reach it from outside" "n" \
            && EXPOSE_DB=1
    else
        EXPOSE_DB="$EMBER_EXPOSE_DB"
    fi
fi

# --- The rest ------------------------------------------------------------------------------------

step "Settings"

EMBER_TAG=$(ask "Version (image tag)" "${EMBER_TAG:-latest}")

# Both arrangements put a proxy in front, and with nothing trusted Ember sees that proxy as the
# visitor for every request: the rate limits, the traffic figures and the log all record it instead
# of the person. The docker bridge range is what the proxy talks from.
TRUSTED_PROXIES=$(ask "Ranges whose forwarded-for headers are believed" \
    "${EMBER_TRUSTED_PROXIES:-172.16.0.0/12}")

BEHIND_CLOUDFLARE="${EMBER_CLOUDFLARE:-}"
if [ -z "$BEHIND_CLOUDFLARE" ]; then
    if ask_yes_no "Does this instance sit behind Cloudflare?" "n"; then
        BEHIND_CLOUDFLARE=true
    else
        BEHIND_CLOUDFLARE=false
    fi
fi

step "Where things are kept"

say "${DIM}The configuration and the uploaded files sit as directories next to this file, so that a"
say "backup picks them up without having to reach into docker for them.${OFF}"

CONFIG_DIR=$(ask "Directory for the configuration" "${EMBER_CONFIG_DIR:-./config}")
DATA_DIR=$(ask "Directory for files and images" "${EMBER_DATA_DIR:-./data}")

DB_VOLUME=""
if [ "$DB_MODE" = "bundled" ]; then
    say ""
    say "${DIM}A named volume is the default for the database. Anything starting with . or / is taken as"
    say "a directory and mounted straight through instead.${OFF}"
    DB_VOLUME=$(ask "Where the database keeps its files" "${EMBER_DB_VOLUME:-ember-data}")
fi

# --- Write it out ---------------------------------------------------------------------------------

step "Writing the files"

# Which networks the backend joins, and which of them compose has to be told are somebody else's.
EMBER_NETWORKS="default"
NETWORK_BLOCK=""
if [ "$MODE" = "traefik" ]; then
    EMBER_NETWORKS="$EMBER_NETWORKS, traefik"
    NETWORK_BLOCK="  traefik:
    name: $TRAEFIK_NETWORK
    external: true
"
fi
if [ -n "$DB_NETWORK" ]; then
    EMBER_NETWORKS="$EMBER_NETWORKS, database"
    NETWORK_BLOCK="$NETWORK_BLOCK  database:
    name: $DB_NETWORK
    external: true
"
fi
[ "$EMBER_NETWORKS" = "default" ] && EMBER_NETWORKS=""

# A named volume is docker's to manage; a path is ours, and has to exist before it is mounted or
# docker creates it as root and the container cannot write into it.
case "$DB_VOLUME" in
    "" | [!./]*) DB_VOLUME_IS_PATH=0 ;;
    *) DB_VOLUME_IS_PATH=1 ;;
esac

mkdir -p "$CONFIG_DIR" "$DATA_DIR"
[ "$DB_VOLUME_IS_PATH" = 1 ] && mkdir -p "$DB_VOLUME"

{
    echo "EMBER_TAG=$EMBER_TAG"
    echo "EMBER_HOST=$EMBER_HOSTNAME"
    echo "EMBER_DB_PASSWORD=$DB_PASSWORD"
} > .env
chmod 600 .env
say "  .env"

{
    cat <<YAML
# Written by the Ember installer on $(date -u '+%Y-%m-%d %H:%M UTC').
# Edit freely: the installer never reads this file back.
services:
YAML

    if [ "$DB_MODE" = "bundled" ]; then
        cat <<YAML
  postgres:
    image: postgres:18-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: $DB_NAME
      POSTGRES_USER: $DB_USER
      POSTGRES_PASSWORD: \${EMBER_DB_PASSWORD}
    volumes:
      - $DB_VOLUME:/var/lib/postgresql
YAML

        if [ "$EXPOSE_DB" = 1 ]; then
            cat <<YAML
    ports:
      - "$DB_PORT:5432"
YAML
        fi

        cat <<YAML
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $DB_USER"]
      interval: 5s
      timeout: 3s
      retries: 10

YAML
    fi

    cat <<YAML
  ember:
    container_name: ember
    image: ghcr.io/rainbowdashlabs/ember-backend:\${EMBER_TAG:-latest}
    restart: unless-stopped
    environment:
      DB_HOST: $DB_HOSTNAME
      DB_PORT: "$DB_PORT"
      DB_USER: $DB_USER
      DB_PASSWORD: \${EMBER_DB_PASSWORD}
      DB_DATABASE: $DB_NAME
      DB_SCHEMA: $DB_SCHEMA
      # Where this instance is reached from outside. It is what the links in emails are built from.
      API_BASEURL: "$BASE_URL"
      # Which hops may speak for the visitor. Left empty, the proxy in front counts as the visitor
      # for every request that arrives.
      NETWORK_TRUSTEDPROXIES: "$TRUSTED_PROXIES"
      NETWORK_CLOUDFLARE: "$BEHIND_CLOUDFLARE"
    volumes:
      - $CONFIG_DIR:/app/config
      - $DATA_DIR:/app/data
YAML

    if [ "$DB_MODE" = "bundled" ]; then
        cat <<YAML
    depends_on:
      postgres:
        condition: service_healthy
YAML
    fi

    if [ "$MODE" = "traefik" ]; then
        cat <<YAML
    labels:
      traefik.enable: "true"
      traefik.http.routers.ember-api.rule: "Host(\`\${EMBER_HOST}\`) && PathPrefix(\`/api\`)"
      traefik.http.routers.ember-api.entrypoints: $TRAEFIK_ENTRYPOINT
      traefik.http.routers.ember-api.tls.certresolver: $TRAEFIK_RESOLVER
      traefik.http.services.ember-api.loadbalancer.server.port: "8080"
YAML
    fi

    [ -n "$EMBER_NETWORKS" ] && printf '    networks: [%s]\n' "$EMBER_NETWORKS"

    cat <<YAML

  frontend:
    container_name: ember-frontend
    image: ghcr.io/rainbowdashlabs/ember-frontend:\${EMBER_TAG:-latest}
    restart: unless-stopped
    # Where the server render reaches the backend. The browser uses a relative path, which has an
    # origin to resolve against; the server does not, and without this it falls back to its own
    # localhost, where nothing answers.
    environment:
      NUXT_BACKEND_URL: http://ember:8080
    depends_on:
      - ember
YAML

    if [ "$MODE" = "traefik" ]; then
        cat <<YAML
    labels:
      traefik.enable: "true"
      traefik.http.routers.ember-web.rule: "Host(\`\${EMBER_HOST}\`)"
      traefik.http.routers.ember-web.entrypoints: $TRAEFIK_ENTRYPOINT
      traefik.http.routers.ember-web.tls.certresolver: $TRAEFIK_RESOLVER
      traefik.http.routers.ember-web.priority: "1"
      traefik.http.services.ember-web.loadbalancer.server.port: "3000"
    networks: [default, traefik]
YAML
    else
        # Nothing else is needed in front. The frontend server hands /api and the sitemaps to the
        # backend itself, at the address it is given here, so publishing its port publishes both.
        cat <<YAML
    ports:
      - "${BIND_ADDRESS:+$BIND_ADDRESS:}$HOST_PORT:3000"
YAML
    fi

    # A path is mounted straight through and needs no declaration; only a named volume does.
    if [ "$DB_MODE" = "bundled" ] && [ "$DB_VOLUME_IS_PATH" = 0 ]; then
        cat <<YAML

volumes:
  $DB_VOLUME:
YAML
    fi

    if [ -n "$NETWORK_BLOCK" ]; then
        printf '\nnetworks:\n%s' "$NETWORK_BLOCK"
    fi
} > "$COMPOSE_FILE"
say "  $COMPOSE_FILE"

# --- Up ------------------------------------------------------------------------------------------

step "Starting"

if [ "$INTERACTIVE" = 1 ] && [ "${EMBER_ASSUME_YES:-0}" != 1 ]; then
    say ""
    say "  Reached at:   ${BOLD}$BASE_URL${OFF}"
    say "  Version:      $EMBER_TAG"
    say "  Directory:    $PWD"
    ask_yes_no "Pull the images and start now?" "y" || {
        say "Nothing started. When you are ready: docker compose -f $COMPOSE_FILE up -d"
        exit 0
    }
fi

docker compose -f "$COMPOSE_FILE" pull
docker compose -f "$COMPOSE_FILE" up -d

step "Waiting for Ember to come up"

# The login exists only on a first start, and it is written once, while the instance is coming up.
# Waiting for it is the difference between showing it and telling somebody to go and find it.
CREDENTIALS=""
for _ in $(seq 1 60); do
    CREDENTIALS=$(docker compose -f "$COMPOSE_FILE" logs ember 2>/dev/null \
        | grep -A 3 "Default admin account created" || true)
    [ -n "$CREDENTIALS" ] && break
    docker compose -f "$COMPOSE_FILE" logs ember 2>/dev/null | grep -q "API server started" && break
    sleep 2
done

if [ -n "$CREDENTIALS" ]; then
    # Only the two lines worth copying, without the log decoration they arrive wrapped in.
    ADMIN_EMAIL=$(printf '%s\n' "$CREDENTIALS" | sed -n 's/.*Email: *//p' | head -1 | tr -d '\r')
    ADMIN_PASSWORD=$(printf '%s\n' "$CREDENTIALS" | sed -n 's/.*Password: *//p' | head -1 | tr -d '\r')
    say ""
    say "${GREEN}${BOLD}  The login it created${OFF}"
    say ""
    say "    Email:    ${BOLD}$ADMIN_EMAIL${OFF}"
    say "    Password: ${BOLD}$ADMIN_PASSWORD${OFF}"
    say ""
    say "${DIM}  You will be asked to change it on the first login.${OFF}"
    say "${DIM}  It is written here and in the log of the first start, nowhere else.${OFF}"
    say ""
else
    say ""
    say "  No new administrator was created, which is what happens when one already exists here."
    say ""
fi

say "  Open:      ${BOLD}$BASE_URL${OFF}"
say "  Stop:      docker compose -f $COMPOSE_FILE down"
say "  Logs:      docker compose -f $COMPOSE_FILE logs -f"
say ""
say "${DIM}  The logs follow now. Ctrl+C stops watching them, not the containers.${OFF}"
say ""

exec docker compose -f "$COMPOSE_FILE" logs -f

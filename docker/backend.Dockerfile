FROM gradle:jdk25-alpine AS build

ARG GITHUB_ACTIONS=false
ARG GITHUB_REF_TYPE=null
ARG GITHUB_REF_NAME=null
ARG GITHUB_SHA=null
ARG GITHUB_ACTIONS=$GITHUB_ACTIONS
ARG GITHUB_REF_TYPE=$GITHUB_REF_TYPE
ARG GITHUB_REF_NAME=$GITHUB_REF_NAME
ARG GITHUB_SHA=$GITHUB_SHA

WORKDIR /home/gradle

# The build reads the tags to record when each version was released, and git refuses a checkout it
# does not own. The sources arrive here as root and the build runs as gradle.
RUN apk add --no-cache git && git config --global --add safe.directory /home/gradle

# The declarations arrive on their own so that resolving them is a layer a source change does not
# invalidate, the same split the frontend image makes around `npm ci`. Copying everything first
# meant every push re-fetched the whole dependency graph.
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle gradle

RUN gradle resolveDependencies --no-daemon

COPY . .

RUN gradle clean installDist --no-daemon -x test -x javadocJar -x sourcesJar

FROM eclipse-temurin:25-alpine AS runtime

ARG GITHUB_ACTIONS=false
ARG GITHUB_REF_TYPE=null
ARG GITHUB_REF_NAME=null
ARG GITHUB_SHA=null
ENV GITHUB_ACTIONS=$GITHUB_ACTIONS
ENV GITHUB_REF_TYPE=$GITHUB_REF_TYPE
ENV GITHUB_REF_NAME=$GITHUB_REF_NAME
ENV GITHUB_SHA=$GITHUB_SHA

ENV DOCKER=true

RUN apk add --no-cache typst pandoc libreoffice-impress font-liberation libwebp-tools qpdf curl unzip

WORKDIR /app

COPY --from=build /home/gradle/build/install/ember/ .
COPY templates templates
COPY --chmod=755 docker/default-font.sh /usr/local/bin/default-font

RUN mkdir -p config

ENV DOCUMENTS_DEFAULTFONTDIR=/app/data/default-font

ARG EMBER_VERSION=dev

LABEL org.opencontainers.image.title="Ember Backend" \
      org.opencontainers.image.version="$EMBER_VERSION" \
      org.opencontainers.image.authors="RainbowDashLabs and Contributors" \
      org.opencontainers.image.description="API server of Ember, the management panel for fire brigade stations" \
      org.opencontainers.image.source="https://github.com/rainbowdashlabs/ember" \
      org.opencontainers.image.url="https://ember-panel.de" \
      org.opencontainers.image.documentation="https://ember-panel.de/helpcenter/station/basics/hosting" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.vendor="RainbowDashLabs" \
      org.opencontainers.image.revision="$GITHUB_SHA"

# The environment carries every secret an operator sets: the database password, the token pepper,
# the mail credentials, the storage encryption key, the second-factor key. Printing it wrote all of
# them into the container log on every start, and a container that cannot reach its database restarts
# for as long as that lasts, so the log fills with copies. Those logs are what an operator pastes
# into a bug report. The application already logs which overrides exist and what it read, with the
# secrets masked, so nothing is lost here.
ENTRYPOINT ["default-font", "./bin/ember"]

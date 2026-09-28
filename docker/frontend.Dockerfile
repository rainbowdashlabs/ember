FROM node:24-alpine AS build

WORKDIR /build

COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN NODE_OPTIONS='--max-old-space-size=8192' npm run build

FROM node:24-alpine

WORKDIR /app
COPY --from=build /build/.output ./

EXPOSE 3000

ENV NODE_ENV=production
ENV NITRO_PORT=3000
ENV NITRO_HOST=0.0.0.0

ARG GITHUB_SHA=null
ARG EMBER_VERSION=dev

LABEL org.opencontainers.image.title="Ember Frontend" \
      org.opencontainers.image.version="$EMBER_VERSION" \
      org.opencontainers.image.authors="RainbowDashLabs and Contributors" \
      org.opencontainers.image.description="Web interface of Ember, the management panel for fire brigade stations" \
      org.opencontainers.image.source="https://github.com/rainbowdashlabs/ember" \
      org.opencontainers.image.url="https://ember-panel.de" \
      org.opencontainers.image.documentation="https://ember-panel.de/helpcenter/station/basics/hosting" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.vendor="RainbowDashLabs" \
      org.opencontainers.image.revision="$GITHUB_SHA"

CMD ["node", "server/index.mjs"]

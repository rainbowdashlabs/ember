# Dev image - source is bind-mounted at /build, node_modules lives in a named
# volume so the host's potentially-different OS / arch install does not shadow
# the in-container one. Runs the Nuxt dev server with HMR; chokidar uses polling
# because the inotify events from a bind mount are not always reliable across
# host filesystems.
FROM node:24-alpine

WORKDIR /build

EXPOSE 3000

ENV NITRO_PORT=3000
ENV NITRO_HOST=0.0.0.0
ENV CHOKIDAR_USEPOLLING=true
ENV WATCHPACK_POLLING=true
ENV NODE_OPTIONS=--max-old-space-size=8192

# The startup script reconciles node_modules with the lock file before it starts the dev server,
# and narrates both steps. It lives outside /build because the source bind mount and the
# node_modules volume both land there at run time.
COPY docker/frontend-dev-start.sh /usr/local/bin/frontend-dev-start.sh

CMD ["sh", "/usr/local/bin/frontend-dev-start.sh"]

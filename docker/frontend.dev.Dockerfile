# Dev image — source is bind-mounted at /build, node_modules lives in a named
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

# install runs against the bind-mounted source the first time the container
# starts; afterwards the named-volume node_modules survives across restarts.
CMD ["sh", "-c", "[ -d node_modules ] && [ -n \"$(ls -A node_modules 2>/dev/null)\" ] || npm ci; exec npm run dev"]

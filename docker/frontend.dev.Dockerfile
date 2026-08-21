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

# install runs against the bind-mounted source the first time the container starts; afterwards the
# named-volume node_modules survives across restarts. The stamp is what makes that safe: a volume
# filled weeks ago holds whatever package.json wanted then, so a dependency added since is simply
# missing and Nuxt fails at startup asking whether it is installed. Recording the lock file it was
# installed from, and installing again when that no longer matches, keeps the volume honest without
# paying for an install on every start.
CMD ["sh", "-c", "stamp=node_modules/.lock-stamp; want=$(md5sum package-lock.json | cut -d' ' -f1); [ \"$(cat $stamp 2>/dev/null)\" = \"$want\" ] || { npm ci && printf %s \"$want\" > $stamp; }; exec npm run dev"]

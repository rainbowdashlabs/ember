FROM nixos/nix:latest AS build

WORKDIR /build/
RUN nix-channel --update
COPY shell.nix .

COPY frontend/package*.json /build
RUN nix-shell --run "npm ci"
COPY frontend/ .
RUN nix-shell --run "NODE_OPTIONS='--max-old-space-size=8192' npm run build"

FROM node:22-alpine

WORKDIR /app
COPY --from=build /build/.output ./

EXPOSE 3000

ENV NODE_ENV=production
ENV NITRO_PORT=3000
ENV NITRO_HOST=0.0.0.0

CMD ["node", "server/index.mjs"]

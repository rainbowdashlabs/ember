# The backend for the end-to-end stack, from a distribution somebody else already built.
#
# The development image compiles the sources inside the container on every start. On a developer's
# machine the caches are warm and that is a moment; on a fresh runner it is the whole build, once per
# instance and once per group, which was the larger half of every end-to-end job. Here the runner
# builds the distribution once and hands it over, so this image only has to start it.
#
# Build the distribution first: ./gradlew installDist -x test
FROM eclipse-temurin:25-alpine

ENV DOCKER=true

RUN apk add --no-cache typst pandoc libreoffice-impress font-liberation libwebp-tools qpdf

WORKDIR /app

COPY build/install/ember/ .
COPY templates templates

RUN mkdir -p config data

ENTRYPOINT ["./bin/ember"]

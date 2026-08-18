# Dev image - source is bind-mounted at /home/gradle/project, the Gradle home
# (~/.gradle) and the project's build/ live in named volumes so incremental
# compilation survives container restarts. Building inside this container is
# as fast as building on the host once the cache is warm.
FROM gradle:jdk25-alpine

WORKDIR /home/gradle/project

# Runtime tools the app shells out to (typst for PDF rendering, pandoc, libreoffice
# for the legacy Word path, libwebp for image processing, qpdf for PDF wrangling).
RUN apk add --no-cache typst pandoc libreoffice-impress font-liberation libwebp-tools qpdf

ENV DOCKER=true
ENV GRADLE_USER_HOME=/home/gradle/.gradle

EXPOSE 8080

# Use the project's wrapper so the Gradle version matches the one pinned in
# gradle/wrapper/gradle-wrapper.properties. The wrapper downloads its
# distribution into GRADLE_USER_HOME on first run; the named volume keeps it
# alive across restarts so subsequent starts skip the download entirely.
# Application plugin's run task is the foreground process so docker compose
# stop / restart cycles it cleanly. Source edits on the host trigger a
# fresh, incremental rebuild on the next restart.
CMD ["./gradlew", "run", "--no-daemon", "-x", "test"]

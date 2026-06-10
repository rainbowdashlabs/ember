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

RUN apk add --no-cache typst pandoc libreoffice-impress

WORKDIR /app

COPY --from=build /home/gradle/build/install/ember/ .
COPY templates templates

RUN mkdir -p config

ENTRYPOINT ["sh", "-c", "env && exec ./bin/ember"]

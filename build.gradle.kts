import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

plugins {
    id("java")
    application
    alias(libs.plugins.spotless)
    alias(libs.plugins.idea)
    jacoco
}

application {
    mainClass = "dev.chojo.ember.Bootstrapper"
    applicationDefaultJvmArgs = listOf("-Dlogback.configurationFile=logback.xml")
}

group = "dev.chojo"
// CalVer as YY.MINOR.MICRO -> https://calver.org/
version = "26.13.3"

repositories {
    maven("https://eldonexus.de/repository/maven-proxies/")
    mavenCentral()
}

dependencies {
    implementation(libs.ocular)
    annotationProcessor(libs.ocular)
    implementation(libs.bundles.config)
    implementation(libs.bundles.javalin)

    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.bundles.sadu)

    implementation(libs.bundles.logback)
    implementation(libs.slf4j)

    annotationProcessor(libs.javalin.openapi.annotation)
    implementation(libs.bundles.javalin)

    implementation(libs.guice)
    implementation(libs.bcrypt)
    implementation(libs.jspecify)
    implementation(libs.caffeine)
    implementation(libs.totp)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.webauthn.server)

    implementation(libs.angus)
    implementation(libs.pebble)
    implementation(libs.bundles.commonmark)
    implementation(libs.jsoup)
    implementation(libs.java.diff.utils)
    implementation(libs.commons.csv)
    implementation(libs.thumbnailator)
    implementation(libs.imageio.webp)
    implementation(libs.smbj)
    implementation(libs.sshd.core)
    implementation(libs.sshd.sftp)
    implementation(libs.aws.s3)
    implementation(libs.pdfbox)
    implementation(libs.ical4j)
    implementation(libs.rome)
    implementation(libs.rome.modules)
    implementation(libs.bundles.ai)

    testRuntimeOnly(libs.junit.platform)
    testImplementation(libs.sadu.testing)
    testImplementation(libs.postgres)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.archunit)
}

/**
 * Number of JVMs a test task may fork.
 *
 * Every fork starts its own database container, and rootless Docker allocates the host port in a
 * check-then-bind that races every outbound socket on the machine. Disabling the Testcontainers
 * reaper halves the containers a fork starts and removes the one that lost that race by far the most
 * often, which is what keeps one fork per two cores workable. Override with `-PtestForks=N` when a
 * machine needs a different balance.
 */
fun testForks(): Int {
    val configured = providers.gradleProperty("testForks").orNull?.toIntOrNull()
    return configured ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

tasks {
    withType<Test>().configureEach {
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    }

    compileJava {
        options.isIncremental = true
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    processResources {
        val projectVersion = project.version.toString();
        inputs.property("projectVersion", projectVersion)
        from(sourceSets.main.get().resources.srcDirs) {
            filesMatching("version") {
                var version = projectVersion
                var workflow = (System.getenv("GITHUB_ACTIONS") ?: "false") == "true"
                if (workflow) {
                    val now = ZonedDateTime.now(ZoneOffset.UTC)
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    val formattedDate = now.format(formatter)

                    version = when (System.getenv("GITHUB_REF_TYPE")) {
                        "branch" -> "$version ${System.getenv("GITHUB_REF_NAME")}-${
                            System.getenv("GITHUB_SHA").substring(0, 7)
                        } @ $formattedDate"

                        "tag" -> "$version @ $formattedDate"
                        else -> "$version snapshot"
                    }
                }
                expand(
                    "version" to version
                )
            }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    test {
        useJUnitPlatform {
            excludeTags("locale")
        }
        testLogging {
            events("passed", "skipped", "failed")
        }
        filter {
            excludeTestsMatching("dev.chojo.ember.tracking.*")
        }
        maxParallelForks = testForks()
    }

    register<Test>("testRepositories") {
        group = "verification"
        description = "Runs repository tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("locale") }
        testLogging { events("passed", "skipped", "failed") }
        filter { includeTestsMatching("*.repository.*") }
        maxParallelForks = testForks()
    }

    register<Test>("testServices") {
        group = "verification"
        description = "Runs service tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("locale") }
        testLogging { events("passed", "skipped", "failed") }
        filter { includeTestsMatching("*.service.*") }
        maxParallelForks = testForks()
    }

    register<Test>("testOther") {
        group = "verification"
        description = "Runs non-repository, non-service tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("locale") }
        testLogging { events("passed", "skipped", "failed") }
        filter {
            excludeTestsMatching("*.repository.*")
            excludeTestsMatching("*.service.*")
            excludeTestsMatching("dev.chojo.ember.tracking.*")
        }
        maxParallelForks = testForks()
    }

    register("fetchCloudflareRanges") {
        group = "build"
        description = "Fetches Cloudflare's published edge IP ranges into generated resources."
        val outputFile = layout.buildDirectory.file("generated/resources/cloudflare-ranges.txt").get().asFile
        outputs.file(outputFile)
        outputs.upToDateWhen {
            outputFile.exists() &&
                (Instant.now().toEpochMilli() - outputFile.lastModified()) <
                    TimeUnit.DAYS.toMillis(7)
        }
        doLast {
            outputFile.parentFile.mkdirs()
            try {
                val sb = StringBuilder()
                sb.append("# Auto-generated at build time from cloudflare.com\n")
                sb.append("# Generated: ").append(Instant.now().toString()).append('\n')
                sb.append("# Sources:\n")
                sb.append("#   https://www.cloudflare.com/ips-v4\n")
                sb.append("#   https://www.cloudflare.com/ips-v6\n\n")
                sb.append("# IPv4\n")
                sb.append(URI.create("https://www.cloudflare.com/ips-v4").toURL().readText())
                sb.append("\n# IPv6\n")
                sb.append(URI.create("https://www.cloudflare.com/ips-v6").toURL().readText())
                sb.append('\n')
                outputFile.writeText(sb.toString())
                logger.lifecycle("Fetched Cloudflare edge IP ranges into ${outputFile.relativeTo(rootDir)}")
            } catch (e: Exception) {
                if (outputFile.exists()) {
                    logger.warn("Cloudflare ranges fetch failed ({}); keeping cached file at {}", e.message, outputFile.relativeTo(rootDir))
                } else {
                    throw GradleException("Could not fetch Cloudflare ranges and no cached file exists: ${e.message}")
                }
            }
        }
    }

    processResources {
        dependsOn("fetchCloudflareRanges")
    }

    afterEvaluate {
        tasks.findByName("sourcesJar")?.dependsOn("fetchCloudflareRanges")
    }

    register<JavaExec>("generateFederationVersion") {
        group = "build"
        description = "Generates the per-surface federation contract hashes from the API contract"
        dependsOn("compileJava")
        mainClass = "dev.chojo.ember.feature.federation.contract.FederationVersionComputer"
        classpath = sourceSets.main.get().runtimeClasspath
        args = listOf(
            file("src/main/resources/federation_version.json").absolutePath,
            file("src/main/resources/federation_versions.json").absolutePath,
            project.version.toString(),
            file("frontend/src/federation_versions.json").absolutePath
        )
    }

    register<JavaExec>("refreshDataTracking") {
        group = "build"
        description = "Refreshes src/main/resources/data_tracking.json from the live DB schema (testcontainer)." +
            " Editing of the tracking entries themselves happens via the /admin/data-tracking dev panel."
        dependsOn("compileTestJava")
        mainClass = "dev.chojo.ember.tracking.DataTrackingRefreshCli"
        classpath = sourceSets.test.get().runtimeClasspath
    }

    register<Test>("testTracking") {
        group = "verification"
        description = "Runs data tracking verification tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("locale") }
        testLogging { events("passed", "skipped", "failed") }
        filter { includeTestsMatching("dev.chojo.ember.tracking.*") }
        maxParallelForks = 1
    }

    register("verifyJavadoc") {
        group = "verification"
        description = "Verifies Javadoc generation succeeds"
        dependsOn("javadoc")
    }

    register("verify") {
        group = "verification"
        description = "Runs all verification tasks in parallel"
        dependsOn("testRepositories", "testServices", "testTracking", "testOther", "jacocoCoverageCheck", "verifyJavadoc", "checkLicenseBackend", "checkLicenseFrontend")
    }

    register<JacocoReport>("jacocoFullReport") {
        group = "verification"
        description = "Merged coverage report from all test tasks"
        dependsOn("test", "testRepositories", "testServices", "testTracking", "testOther")
        executionData(
            fileTree("build/jacoco") { include("*.exec") }
        )
        sourceSets(sourceSets.main.get())
        reports {
            xml.required.set(true)
            csv.required.set(true)
            html.required.set(true)
        }
    }

    register<JacocoCoverageVerification>("jacocoCoverageCheck") {
        group = "verification"
        description = "Enforces 80% line coverage for services and repositories"
        dependsOn("test", "testRepositories", "testServices", "testTracking", "testOther")
        executionData(
            fileTree("build/jacoco") { include("*.exec") }
        )
        sourceSets(sourceSets.main.get())
        violationRules {
            // Repositories: 95% line coverage
            rule {
                element = "CLASS"
                includes = listOf("*.repository.*")
                excludes = listOf("*.route.*")
                limit {
                    counter = "LINE"
                    minimum = "0.95".toBigDecimal()
                }
            }
            // Handlers: 80% line coverage
            rule {
                element = "CLASS"
                includes = listOf("*.handler.*", "*.handlers.*")
                limit {
                    counter = "LINE"
                    minimum = "0.80".toBigDecimal()
                }
            }
            // Services: 90% line coverage
            rule {
                element = "CLASS"
                includes = listOf("*.service.*")
                excludes = listOf(
                    // Infrastructure that calls external systems
                    "*.mail.service.*",
                    "*.FederationHttpClient*",
                    "*.FederationWebhookService*",
                    "*.ApiRequestLogger*",
                    "*.DataInitializer",
                    "*.ProblemLogAppender*",
                    // Demo/seed data generators. Only the avatar seeder is exempt: it fetches
                    // pictures from a third-party API with a hard-wired client and caches them in
                    // a hard-wired directory, so covering it would mean two production seams for
                    // demo data. Every other seeder is gated like any other service.
                    "*.DemoAvatarSeeder*",
                    "*.DemoService*",
                    // PDF/export services requiring external binaries
                    "*PdfService*",
                    "*ReportService*",
                    "*ExportService*",
                    // External AI API calls
                    "*.AiService*",
                    // File I/O services
                    "*.KbFileStorageService*",
                    "*.PageFileStorageService*",
                    "*.PdfCompressor*",
                    "*.BoardAttachmentService*",
                    // Unreachable catch: gzip() wraps a ByteArrayOutputStream, which cannot throw
                    // the IOException the GZIP streams declare, so 2 of its 19 lines can never be
                    // executed and it sits at 89.5%. Accepted permanently rather than restructured
                    // - the catch is required by the checked signature.
                    "*.TextCompressionPolicy*",
                    // Unified storage façade - heavy I/O against the backend layer,
                    // public-surface paths covered by StorageServiceTest
                    "*.StorageService*",
                    // Image variant pipeline and the thin per-domain wrappers over it -
                    // exercised end-to-end via route tests, not unit-covered.
                    "*.ImageVariantService*",
                    "*.LostAndFoundImageService*",
                    "*.QuizQuestionImageService*",
                    "*.KbIconService*",
                    "*.KbImageService*",
                    "*.LogoFragmentService*",
                    // External binary dependent services
                    "*.LegalDocumentService*",
                    // Daemon/scheduler threads
                    "*.RegistrationDeadlineChecker*",
                    "*.DueDateReminderChecker*",
                    // Complex CSV parsing with many edge cases
                    "*.MemberImportService*",
                    // Not CSV parsing despite its name: the uncovered part is remote-transfer
                    // orchestration on background executors against a live source instance over
                    // HTTP. Needs an integration test, not a unit test.
                    "*.StationImportService*",
                    // Storage monitoring (filesystem walks, scheduled reconciliation, ZIP compression)
                    "*.StorageReconciliationService*",
                    "*.StorageQuotaService*",
                    // Federation version broadcaster (daemon thread, startup-only)
                    "*.FederationVersionBroadcaster*",
                    // Maps tile cache (filesystem walks + outbound HTTP, exercised manually)
                    "*.MapTileCacheService*",
                    // Startup refresh of Cloudflare's published edge ranges - outbound HTTP to
                    // cloudflare.com with a hard-wired client; the parsing and matching logic it
                    // delegates to lives in ClientIp and is covered there.
                    "*.CloudflareRangesService*",
                    // Discovery chain (HTTP + daemon threads, exercised by integration tests)
                    "*.DiscoveryHttpClient*",
                    "*.DiscoveryPingScheduler*",
                    "*.DiscoveryStationRefreshScheduler*",
                    "*.DiscoveryMaintenanceScheduler*",
                    "*.DiscoveryPingService*",
                    "*.DiscoveryStationFetcher*",
                    "*.FederationPartnerSeeder*",
                    "*.DiscoveryKeyService*",
                    "*.DiscoveryStationProjectionService*",
                    // WebAuthn verification - finishRegistration/finishAssertion success paths
                    // need a real authenticator-issued credential signature, not a unit test.
                    "*.WebAuthnService*",
                    // Static CIDR helper record - class-init only, not worth unit-testing
                    "*.RemoteUrlValidator.Cidr",
                )
                limit {
                    counter = "LINE"
                    minimum = "0.90".toBigDecimal()
                }
            }
        }
    }

    register("checkLicenseBackend") {
        group = "verification"
        description = "Checks license headers for backend Java files"
        dependsOn("spotlessJavaCheck")
    }

    register("checkLicenseFrontend") {
        group = "verification"
        description = "Checks license headers for frontend Vue and JavaScript files"
        dependsOn("spotlessJavascriptCheck", "spotlessVueCheck")
    }

    register("formatFrontend") {
        group = "formatting"
        description = "Applies license headers and whitespace rules to frontend Vue, TypeScript and locale files"
        dependsOn("spotlessJavascriptApply", "spotlessVueApply", "spotlessFrontendLocalesApply")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/resources"))
        }
    }
}

idea {
    project {
        settings {
            var shared = listOf(
                "--sun-misc-unsafe-memory-access=allow",
                "--enable-native-access=ALL-UNNAMED"
            )
            runConfigurations {
                register<org.jetbrains.gradle.ext.Gradle>("Run App") {
                    projectPath = project.path
                    taskNames = listOf("run")
                    jvmArgs = shared.joinToString(" ")
                }
                register<org.jetbrains.gradle.ext.Gradle>("Run App - All SKUs") {
                    projectPath = project.path
                    taskNames = listOf("run")
                    jvmArgs =                       shared.joinToString(" ")
                }
            }
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        licenseHeaderFile(rootProject.file("HEADER.txt"))
        trimTrailingWhitespace()
        endWithNewline()
        palantirJavaFormat("2.84.0")
            .formatJavadoc(false)
        removeUnusedImports()
        importOrder("", "java", "javax", "\\#")
        encoding("UTF-8")
    }

    format("javascript") {
        licenseHeaderFile(
            rootProject.file("HEADER.txt"),
            "(import|const|let|var|export|function|type|interface|enum|class|abstract|async|declare|//|/\\*\\*)",
        )
        target("frontend/src/**/*.js", "frontend/src/**/*.ts")
        targetExclude("frontend/node_modules/**", "frontend/dist/**")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("vue") {
        licenseHeaderFile(rootProject.file("HEADER.txt"), "(<template|<script|<style)")
        target("frontend/src/**/*.vue")
        targetExclude("frontend/node_modules/**", "frontend/dist/**")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("backendLocales") {
        encoding("UTF-8")
        target("src/main/resources/locale*.properties")
    }

    format("frontendLocales") {
        encoding("UTF-8")
        target("frontend/src/locales/*.json")
    }
}

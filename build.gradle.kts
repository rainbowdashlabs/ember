import org.jetbrains.gradle.ext.ShortenCommandLine
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
version = "26.6.0"

repositories {
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

    implementation(libs.angus)
    implementation(libs.bundles.commonmark)
    implementation(libs.java.diff.utils)
    implementation(libs.commons.csv)
    implementation(libs.thumbnailator)
    implementation(libs.imageio.webp)
    implementation(libs.pdfbox)
    implementation(libs.ical4j)
    implementation(libs.rome)
    implementation(libs.bundles.ai)

    testRuntimeOnly(libs.junit.platform)
    testImplementation(libs.sadu.testing)
    testImplementation(libs.postgres)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockito)
}

tasks {
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
    }

    register<Test>("testRepositories") {
        group = "verification"
        description = "Runs repository tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("locale") }
        testLogging { events("passed", "skipped", "failed") }
        filter { includeTestsMatching("dev.chojo.ember.repository.*") }
    }

    register<Test>("testServices") {
        group = "verification"
        description = "Runs service tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("locale") }
        testLogging { events("passed", "skipped", "failed") }
        filter { includeTestsMatching("dev.chojo.ember.service.*") }
    }

    register<Test>("testOther") {
        group = "verification"
        description = "Runs non-repository, non-service tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("locale") }
        testLogging { events("passed", "skipped", "failed") }
        filter {
            excludeTestsMatching("dev.chojo.ember.repository.*")
            excludeTestsMatching("dev.chojo.ember.service.*")
        }
    }

    register<JavaExec>("generateFederationVersion") {
        group = "build"
        description = "Generates the federation version hash from the API contract"
        dependsOn("compileJava")
        mainClass = "dev.chojo.ember.feature.federation.version.FederationVersionComputer"
        classpath = sourceSets.main.get().runtimeClasspath
        args = listOf(
            file("src/main/resources/federation_version").absolutePath,
            file("src/main/resources/federation_versions.json").absolutePath,
            project.version.toString(),
            file("frontend/src/federation_versions.json").absolutePath
        )
    }

    register("verifyJavadoc") {
        group = "verification"
        description = "Verifies Javadoc generation succeeds"
        dependsOn("javadoc")
    }

    register("verify") {
        group = "verification"
        description = "Runs all verification tasks in parallel"
        dependsOn("testRepositories", "testServices", "testOther", "jacocoCoverageCheck", "verifyJavadoc", "checkLicenseBackend", "checkLicenseFrontend")
    }

    register<JacocoReport>("jacocoFullReport") {
        group = "verification"
        description = "Merged coverage report from all test tasks"
        dependsOn("testRepositories", "testServices", "testOther")
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
        dependsOn("testRepositories", "testServices", "testOther")
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
                    "*.route.*",
                    // Infrastructure that calls external systems
                    "*.mail.service.*",
                    "*.FederationHttpClient*",
                    "*.FederationWebhookService*",
                    "*.FederatedBoardProxyService*",
                    "*.ApiRequestLogger*",
                    "*.DataInitializer",
                    "*.ProblemLogAppender*",
                    // Demo/seed data generators
                    "*.Demo*Seeder*",
                    "*.DemoService*",
                    // PDF/export services requiring external binaries
                    "*PdfService*",
                    "*ReportService*",
                    "*ExportService*",
                    // External AI API calls
                    "*.AiService*",
                    // File I/O services
                    "*.KbFileStorageService*",
                    // External binary dependent services
                    "*.LegalDocumentService*",
                    // Daemon/scheduler threads
                    "*.RegistrationDeadlineChecker*",
                    "*.DueDateReminderChecker*",
                    // Import services (complex CSV parsing with many edge cases)
                    "*.MemberImportService*",
                    "*.StationImportService*",
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
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
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
        licenseHeaderFile(rootProject.file("HEADER.txt"), "(import|const|let|var|export|//)")
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

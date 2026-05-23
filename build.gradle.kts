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
version = "1.2.0"

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

    implementation(libs.angus)
    implementation(libs.bundles.commonmark)
    implementation(libs.java.diff.utils)
    implementation(libs.commons.csv)
    implementation(libs.thumbnailator)
    implementation(libs.imageio.webp)
    implementation(libs.pdfbox)
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

                        "tag" -> "$version ${System.getenv("GITHUB_REF_NAME").substring(1)} @ $formattedDate"
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

    register<JacocoReport>("jacocoFullReport") {
        group = "verification"
        description = "Merged coverage report from unit + database tests"
        executionData(file("build/jacoco/test.exec"), file("build/jacoco/testDatabase.exec"))
        sourceSets(sourceSets.main.get())
        reports {
            xml.required.set(true)
            csv.required.set(true)
            html.required.set(true)
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

import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

plugins {
    id("java")
    application
    alias(libs.plugins.spotless)
    alias(libs.plugins.idea)
    alias(libs.plugins.spotbugs)
    jacoco
}

application {
    mainClass = "dev.chojo.ember.Bootstrapper"
    applicationDefaultJvmArgs =
        listOf("-Dlogback.configurationFile=logback.xml", "-Djdk.httpclient.allowRestrictedHeaders=host")
}

group = "dev.chojo"
// CalVer as YY.MINOR.MICRO -> https://calver.org/
version = "26.20.0"

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

    implementation(libs.guice)
    implementation(libs.bcrypt)
    implementation(libs.jspecify)
    compileOnly(libs.spotbugs.annotations)
    implementation(libs.caffeine)
    implementation(libs.java.otp)
    implementation(libs.commons.codec)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.webauthn.server)

    implementation(libs.angus)
    implementation(libs.jdkim)
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
    testImplementation(libs.greenmail)
    testImplementation(libs.javalin.testtools)
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

/**
 * One slice of the test source set, run as a task of its own and as a CI job of its own.
 *
 * The slices are cut by package. Every suite but the last one names the packages it holds, and the
 * last one takes whatever they leave, so a new test always lands in exactly one suite.
 *
 * The architecture rules run on ArchUnit's own JUnit engine, which Gradle's test name filters do not
 * reach, so a suite that does not exclude the engine runs them whatever it is meant to hold. Importing
 * the codebase for them takes a few hundred megabytes of a fork's heap, and a fork that had already
 * spent its heap on database tests ran out of memory halfway through the import. Only the suite
 * without a database keeps them.
 *
 * @property packages the test name patterns this suite holds; empty for the suite of the rest
 * @property forks the forks this suite may use at most; the tracking tests share one database
 */
data class TestSuite(
    val name: String,
    val description: String,
    val packages: List<String> = emptyList(),
    val architectureRules: Boolean = false,
    val forks: Int? = null,
)

val testSuites = listOf(
    TestSuite("testRepositories", "Runs the repository tests", listOf("*.repository.*")),
    TestSuite("testServices", "Runs the service tests", listOf("*.service.*")),
    TestSuite("testTracking", "Runs the data tracking verification tests", listOf("dev.chojo.ember.tracking.*"), forks = 1),
    TestSuite("testOther", "Runs every test the other suites leave, the architecture rules included", architectureRules = true),
)

val testSuiteNames = testSuites.map { it.name }

/** The packages some suite names, which the suite of the rest leaves out. */
val suitePackages = testSuites.flatMap { it.packages }

/**
 * The line coverage of the whole backend, routes included, may not fall below this.
 *
 * It stands just under where the suites reach today, so it catches a tested area losing its tests
 * without a list of classes it does not apply to. Raise it as coverage grows; it never goes down.
 * The lines a change touches are held to the stricter patch gate in `gradle/patch-coverage.gradle.kts`.
 */
val coverageFloor = "0.66".toBigDecimal()

/**
 * The line coverage every repository class has to reach on its own.
 *
 * A repository is SQL a test can run completely against a real database, so this one gate per class
 * stays: unlike the services, no repository needs an exception to meet it.
 */
val repositoryCoverage = "0.95".toBigDecimal()

/**
 * Runs a git command in this checkout, or answers null where it cannot be run at all.
 *
 * A build has no business failing because the sources arrived without their history: a tarball, a
 * shallow clone and an image built from a context that leaves `.git` behind are all legitimate ways
 * to get here, and each of them simply knows less about when a version was released.
 */
fun gitOutput(vararg arguments: String): String? =
    try {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(layout.projectDirectory.asFile)
            .start()
        val text = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (finished && process.exitValue() == 0) text else null
    } catch (e: Exception) {
        null
    }

/**
 * When each version was tagged, as the changelog page shows it and links between.
 *
 * Read from the tags rather than written down by hand, because the tag is what a release is cut
 * from and anything kept beside it would be one more thing to forget. A checkout with no tags
 * yields an empty record, and the page then shows the entries without their dates.
 */
fun releaseTagsJson(): String {
    val format = "--format=%(refname:short)\t%(creatordate:iso-strict)"
    val lines = gitOutput("for-each-ref", "--sort=-creatordate", format, "refs/tags")
        ?.lines()
        ?.filter { it.isNotBlank() }
        .orEmpty()
    val entries = lines.mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 2) return@mapNotNull null
        val tag = parts[0].trim()
        val version = tag.removePrefix("v")
        if (!version.matches(Regex("\\d+(\\.\\d+)*"))) return@mapNotNull null
        val released = try {
            OffsetDateTime.parse(parts[1].trim()).toInstant().toString()
        } catch (e: Exception) {
            return@mapNotNull null
        }
        """  "$version": {"tag": "$tag", "releasedAt": "$released"}"""
    }
    return entries.joinToString(",\n", "{\n", "\n}\n")
}

val releaseTags = tasks.register("releaseTags") {
    description = "Records when each version was tagged, for the changelog to show and link between"
    val output = layout.buildDirectory.file("generated/changelog/releases.json")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(releaseTagsJson())
    }
}

/**
 * Downloads every dependency this build can resolve, compiling nothing.
 *
 * It exists for the image build, which runs it from the dependency declarations alone, before the
 * sources arrive. The layer it fills then survives any change to a source file, so a push that
 * touches one class no longer re-fetches the whole dependency graph.
 *
 * The view is lenient because the point is to warm a cache rather than to judge the build: a
 * configuration that cannot be resolved yet is left to the real build, which reports it properly.
 */
tasks.register("resolveDependencies") {
    description = "Downloads every resolvable dependency, so an image layer can hold them"
    val resolvable = configurations.matching { it.isCanBeResolved }
    doLast {
        resolvable.forEach { configuration ->
            configuration.incoming.artifactView { isLenient = true }.artifacts.artifactFiles.files
        }
    }
}

tasks {
    withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
        maxParallelForks = testForks()
        maxHeapSize = "1g"
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        systemProperty("jdk.httpclient.allowRestrictedHeaders", "host")
        systemProperty(
            "refusal.baseline.update",
            providers.systemProperty("refusal.baseline.update").getOrElse("false"),
        )
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
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
        // The changelog is read by the instance rather than fetched from GitHub by whoever is
        // looking at it, so it travels in the jar beside the version it belongs to. One file per
        // language, named by the locale that asks for it.
        from(layout.projectDirectory.file("CHANGELOG.md")) {
            into("changelog")
            rename { "en.md" }
        }
        from(layout.projectDirectory.file("CHANGELOG.de.md")) {
            into("changelog")
            rename { "de.md" }
        }
        from(releaseTags) {
            into("changelog")
        }
    }

    test {
        filter { excludeTestsMatching("dev.chojo.ember.tracking.*") }
    }

    testSuites.forEach { suite ->
        register<Test>(suite.name) {
            group = "verification"
            description = suite.description
            testClassesDirs = sourceSets.test.get().output.classesDirs
            classpath = sourceSets.test.get().runtimeClasspath
            if (!suite.architectureRules) useJUnitPlatform { excludeEngines("archunit") }
            filter {
                if (suite.packages.isEmpty()) {
                    suitePackages.forEach { excludeTestsMatching(it) }
                } else {
                    suite.packages.forEach { includeTestsMatching(it) }
                }
            }
            suite.forks?.let { maxParallelForks = it }
        }
    }

    register("testAll") {
        group = "verification"
        description = "Runs every test suite"
        dependsOn(testSuiteNames)
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

    register<JavaExec>("generateApiSpec") {
        group = "build"
        description = "Writes src/main/resources/api/openapi.json from the route annotations and the wire records"
        dependsOn("compileTestJava")
        mainClass = "dev.chojo.ember.api.spec.ApiSpecCli"
        classpath = sourceSets.test.get().runtimeClasspath
    }

    val coverageData = fileTree("build/jacoco") { include(testSuiteNames.map { "$it.exec" }) }

    val coverageReport = register<JacocoReport>("jacocoFullReport") {
        group = "verification"
        description = "Merges the coverage the test suites recorded into one report, without running them"
        mustRunAfter(testSuiteNames)
        executionData(coverageData)
        sourceSets(sourceSets.main.get())
        reports {
            xml.required = true
            csv.required = true
            html.required = true
        }
    }

    register<JacocoCoverageVerification>("jacocoCoverageCheck") {
        group = "verification"
        description = "Holds the line coverage of the whole backend above its floor and of every repository near complete"
        mustRunAfter(coverageReport)
        executionData(coverageData)
        sourceSets(sourceSets.main.get())
        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    minimum = coverageFloor
                }
            }
            rule {
                element = "CLASS"
                includes = listOf("*.repository.*")
                limit {
                    counter = "LINE"
                    minimum = repositoryCoverage
                }
            }
        }
    }

    register("formatFrontend") {
        group = "formatting"
        description = "Applies license headers and whitespace rules to frontend Vue, TypeScript and locale files"
        dependsOn("spotlessJavascriptApply", "spotlessVueApply", "spotlessFrontendLocalesApply")
    }
}

/**
 * Null use is checked on the compiled main classes: every package is `@NullMarked`, and a value
 * that can be null is marked jspecify `@Nullable`, which SpotBugs holds every use of to a check.
 *
 * SpotBugs does not take `@NullMarked` as a default, so a null passed or returned where nothing is
 * marked goes unseen until a package also carries SpotBugs' own defaults for parameters and method
 * returns in its `package-info.java`. Fields get none: SpotBugs cannot read jspecify on a record
 * component's field, and every nullable component would read as a violation.
 *
 * Only the null-pointer detectors report, and the classes the configuration processor generates are
 * left out.
 */
spotbugs {
    ignoreFailures = false
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.LOW
    includeFilter = layout.projectDirectory.file("gradle/spotbugs-null.xml")
    excludeFilter = layout.projectDirectory.file("gradle/spotbugs-generated.xml")
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}

tasks.named("spotbugsTest") { enabled = false }

apply(from = "gradle/patch-coverage.gradle.kts")

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

    // The data tracking file is generated and committed, so its layout has to be somebody's decision
    // rather than whatever the serialiser happens to do. Left to the library it moved once already and
    // rewrote twenty thousand lines around the one column that had changed. Here it is asserted.
    json {
        target("src/main/resources/data_tracking.json")
        encoding("UTF-8")
        gson().indentWithSpaces(2)
        endWithNewline()
    }
}

import org.gradle.testing.jacoco.tasks.JacocoReport
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The lines a change added or rewrote, per file, as `git diff` reports them.
 *
 * The diff runs from the merge base of the checkout and the given base to the working tree, so what
 * is not committed yet counts as well: the coverage report was written from the working tree too.
 * Deleted lines are left out, since nothing can cover a line that is gone.
 */
object ChangedLines {
    private val fileHeader = Regex("""^\+\+\+ b/(.+)$""")
    private val hunkHeader = Regex("""^@@ -\S+ \+(\d+)(?:,(\d+))? @@""")

    /**
     * Reads the changed lines under a path of the repository.
     *
     * @param repository the root of the checkout
     * @param base the ref the change is measured against, such as `origin/main`
     * @param path the directory to look at, relative to the repository root
     * @return the changed line numbers per file, keyed by the path relative to [path]
     */
    fun since(repository: File, base: String, path: String): Map<String, Set<Int>> {
        val mergeBase = git(repository, "merge-base", base, "HEAD")?.trim()
            ?: throw GradleException(
                "Cannot find where this checkout left '$base'. Fetch it, or name another base with -PcoverageBase=<ref>.",
            )
        val diff = git(repository, "diff", "--unified=0", "--no-color", "--no-ext-diff", "--no-renames", mergeBase, "--", path)
            ?: throw GradleException("git diff against $mergeBase failed.")
        return parse(diff, "$path/")
    }

    private fun parse(diff: String, prefix: String): Map<String, Set<Int>> {
        val lines = mutableMapOf<String, MutableSet<Int>>()
        var current: MutableSet<Int>? = null
        for (line in diff.lineSequence()) {
            if (line.startsWith("+++ ")) {
                current = fileHeader.find(line)?.let { lines.getOrPut(it.groupValues[1].removePrefix(prefix)) { mutableSetOf() } }
                continue
            }
            val hunk = hunkHeader.find(line) ?: continue
            val start = hunk.groupValues[1].toInt()
            val count = hunk.groupValues[2].ifEmpty { "1" }.toInt()
            current?.addAll(start until start + count)
        }
        return lines.filterValues { it.isNotEmpty() }
    }

    private fun git(repository: File, vararg arguments: String): String? {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(repository)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        return if (finished && process.exitValue() == 0) output else null
    }
}

/**
 * Which source lines a JaCoCo XML report counts as executable, and which of those the tests ran.
 *
 * A line the report does not name holds no code of its own (a blank line, a comment, a closing
 * brace, an import) and is neither covered nor missed. A line counts as covered once any of its
 * instructions ran.
 *
 * @property executable the executable lines per source file, keyed by the path below the source root
 * @property covered the executable lines the tests ran
 */
class JacocoLines(val executable: Map<String, Set<Int>>, val covered: Map<String, Set<Int>>) {

    companion object {
        /**
         * Reads a report written by a `JacocoReport` task with its XML output switched on.
         *
         * The report names its DTD, which is not shipped beside it, so the parser is told not to
         * fetch it.
         */
        fun read(report: File): JacocoLines {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isValidating = false
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            val document = factory.newDocumentBuilder().parse(report)
            val executable = mutableMapOf<String, MutableSet<Int>>()
            val covered = mutableMapOf<String, MutableSet<Int>>()
            for (pkg in document.getElementsByTagName("package").elements()) {
                for (source in pkg.getElementsByTagName("sourcefile").elements()) {
                    val path = "${pkg.getAttribute("name")}/${source.getAttribute("name")}"
                    for (line in source.getElementsByTagName("line").elements()) {
                        val number = line.getAttribute("nr").toInt()
                        executable.getOrPut(path) { mutableSetOf() }.add(number)
                        if (line.getAttribute("ci").toInt() > 0) covered.getOrPut(path) { mutableSetOf() }.add(number)
                    }
                }
            }
            return JacocoLines(executable, covered)
        }

        private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }
    }
}

/**
 * Fails when the tests leave too many of the lines a change touched unrun.
 *
 * The change is everything between the merge base with [base] and the working tree, under
 * [sourcePath]. Of its lines, only those the coverage [report] counts as executable take part, so a
 * change that only moves comments or imports passes whatever the tests do. The gate declares no
 * output and therefore runs every time: what it compares against moves with every commit.
 */
abstract class PatchCoverageCheck : DefaultTask() {

    /** The merged JaCoCo XML report the lines are looked up in. */
    @get:Internal
    abstract val report: RegularFileProperty

    /** The root of the checkout the diff is taken in. */
    @get:Internal
    abstract val repository: DirectoryProperty

    /** The ref the change is measured against, such as `origin/main`. */
    @get:Input
    abstract val base: Property<String>

    /** The source directory the report's paths are relative to, relative to [repository]. */
    @get:Input
    abstract val sourcePath: Property<String>

    /** The share of changed executable lines that must be covered, between 0 and 1. */
    @get:Input
    abstract val minimum: Property<BigDecimal>

    /** Compares the changed lines with the report and fails, listing the lines no test ran, when too few are covered. */
    @TaskAction
    fun check() {
        val reportFile = report.get().asFile
        if (!reportFile.exists()) {
            throw GradleException("No coverage report at ${reportFile.path}. Run the tests and the report first: ./toolchain.sh be-report")
        }
        val lines = JacocoLines.read(reportFile)
        val changed = ChangedLines.since(repository.get().asFile, base.get(), sourcePath.get())

        val missed = sortedMapOf<String, List<Int>>()
        var total = 0
        changed.forEach { (file, numbers) ->
            val executable = numbers.intersect(lines.executable[file].orEmpty())
            val uncovered = executable - lines.covered[file].orEmpty()
            total += executable.size
            if (uncovered.isNotEmpty()) missed[file] = uncovered.sorted()
        }

        if (total == 0) {
            logger.lifecycle("Patch coverage: no executable line changed since ${base.get()}.")
            return
        }
        val coveredCount = total - missed.values.sumOf { it.size }
        val ratio = BigDecimal(coveredCount).divide(BigDecimal(total), 4, RoundingMode.DOWN)
        val summary = "Patch coverage: $coveredCount of $total changed lines covered (${percent(ratio)}), minimum ${percent(minimum.get())}."
        if (ratio >= minimum.get()) {
            logger.lifecycle(summary)
            return
        }
        val listing = missed.entries.joinToString("\n") { (file, numbers) -> "  ${sourcePath.get()}/$file: ${ranges(numbers)}" }
        throw GradleException("$summary\nChanged lines no test runs:\n$listing")
    }

    private fun percent(value: BigDecimal): String = "${value.movePointRight(2).setScale(2, RoundingMode.DOWN)} %"

    private fun ranges(numbers: List<Int>): String {
        val runs = mutableListOf<IntRange>()
        for (number in numbers) {
            val last = runs.lastOrNull()
            if (last != null && last.last + 1 == number) runs[runs.lastIndex] = last.first..number else runs += number..number
        }
        return runs.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
    }
}

/**
 * The share of the executable lines a branch changed that its tests have to run, routes included.
 *
 * The whole-backend floor in the build script only stops coverage from falling; this is what makes
 * every change bring its tests along.
 */
val patchCoverageMinimum = "0.80".toBigDecimal()

tasks.register<PatchCoverageCheck>("patchCoverageCheck") {
    group = "verification"
    description = "Holds the coverage of the lines changed since the base branch above its minimum"
    val coverageReport = tasks.named<JacocoReport>("jacocoFullReport")
    mustRunAfter(coverageReport)
    report = coverageReport.flatMap { it.reports.xml.outputLocation }
    repository = layout.projectDirectory
    base = providers.gradleProperty("coverageBase")
        .orElse(providers.environmentVariable("COVERAGE_BASE"))
        .orElse("origin/main")
    sourcePath = "src/main/java"
    minimum = patchCoverageMinimum
}

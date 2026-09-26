/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.javalin.http.HttpResponseException;
import io.javalin.http.NoContentResponse;
import io.javalin.http.RedirectResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every failure a reader can be shown has to be a named {@link dev.chojo.ember.api.Refusal}.
 *
 * <p>A raw {@code throw new BadRequestResponse("...")} hands the reader a sentence written at the
 * throw and nothing to quote in a report, which is the state the refusal registry replaced. The
 * route layer is held to that absolutely; everything below it is held to the count it already had,
 * so the backlog can only shrink.
 *
 * <p>This is deliberately not part of {@code ArchitectureTest}, whose own documentation says its
 * rules hold everywhere without exception. A rule carrying a frozen backlog does not, and mixing
 * the two would invite the next person to add an exception list to the rules that have none.
 *
 * <p>These are plain JUnit tests rather than ArchUnit's own {@code @ArchTest} rules, and they
 * import the classes themselves. Gradle's {@code --tests} filter does not reach ArchUnit's engine,
 * so a rule written that way cannot be run on its own, which the baseline has to be in order to
 * rewrite itself. One import serves both tests.
 */
public class RefusalCoverageTest {

    /**
     * The responses that are not failures and are therefore none of this rule's business.
     *
     * <p>{@link NoContentResponse} is a {@code 204} and {@link RedirectResponse} a {@code 3xx}:
     * neither says anything went wrong, so neither owes the reader a sentence or a code.
     */
    private static final Set<String> NOT_REFUSALS =
            Set.of(NoContentResponse.class.getName(), RedirectResponse.class.getName());

    private static final String REFUSAL_RESPONSE = "dev.chojo.ember.api.RefusalResponse";
    private static final Path BASELINE = Path.of("src/test/resources/refusal-baseline.properties");

    private static JavaClasses backend;

    @BeforeAll
    static void importTheBackend() {
        backend = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.chojo.ember");
    }

    /**
     * No route builds a failure response itself.
     *
     * <p>Absolute, with no baseline and no exception list: a route is where a reader's request is
     * answered, so a failure raised there is one somebody will read and quote.
     */
    @Test
    void everyFailureARouteGivesCarriesACode() {
        var unnamed = new ArrayList<String>();
        for (var type : backend) {
            if (!type.getPackageName().contains(".route") || isAFailureItself(type)) continue;
            for (var call : type.getConstructorCallsFromSelf()) {
                if (!isAnUnnamedFailure(call.getTargetOwner())) continue;
                unnamed.add("%s builds %s itself at %s"
                        .formatted(
                                type.getSimpleName(),
                                call.getTargetOwner().getSimpleName(),
                                call.getSourceCodeLocation()));
            }
        }

        assertTrue(
                unnamed.isEmpty(),
                "A reader shown one of these has no code to quote. Add a Refusal and throw "
                        + "Refusal.NAME.raise() instead:\n" + String.join("\n", unnamed));
    }

    /**
     * No route empties an {@link java.util.Optional} with a bare {@code orElseThrow()}.
     *
     * <p>It throws {@code NoSuchElementException}, which is not a failure response at all, so the
     * rule above cannot see it and the catch-all turns it into a {@code 500} carrying the code for
     * "something went wrong in Ember". That is exactly the answer this whole registry exists to
     * replace, and it hides which of a hundred lines produced it.
     *
     * <p>Most of these sit after a write that already succeeded, where the honest sentence is that
     * the change was saved and could not be read back. A bare throw cannot say that either.
     */
    @Test
    void noRouteEmptiesAnOptionalWithoutSayingWhy() {
        var bare = new ArrayList<String>();
        for (var type : backend) {
            if (!type.getPackageName().contains(".route")) continue;
            for (var call : type.getMethodCallsFromSelf()) {
                if (!"orElseThrow".equals(call.getName())) continue;
                if (!call.getTarget().getRawParameterTypes().isEmpty()) continue;
                bare.add("%s at %s".formatted(type.getSimpleName(), call.getSourceCodeLocation()));
            }
        }

        assertTrue(
                bare.isEmpty(),
                "These answer with the catch-all fault and name no line. Pass a refusal, as in "
                        + "orElseThrow(Refusal.NAME::raise):\n" + String.join("\n", bare));
    }

    /**
     * Everything below the route layer, frozen at the number of unnamed failures it already threw.
     *
     * <p>A class may keep what it had and not one throw more, and a class absent from the file must
     * throw none, which is every class written from here. Run
     * {@code ./toolchain.sh be-refusal-baseline} after converting some, which rewrites the file and
     * refuses to record any count that has risen.
     */
    @Test
    void nothingBelowTheRouteLayerThrowsMoreThanItAlreadyDid() {
        var counts = unnamedFailuresBelowRoutes();
        var allowed = readBaseline();

        if (Boolean.getBoolean("refusal.baseline.update")) {
            writeBaseline(counts, allowed);
            return;
        }

        var grown = new ArrayList<String>();
        counts.forEach((type, thrown) -> {
            var permitted = allowed.getOrDefault(type, 0);
            if (thrown > permitted) {
                grown.add("%s throws %d unnamed failures, was allowed %d".formatted(type, thrown, permitted));
            }
        });

        assertTrue(
                grown.isEmpty(),
                "These gained an unnamed failure. Raise a Refusal instead:\n" + String.join("\n", grown));
    }

    /**
     * Counts the unnamed failures each class outside the route layer still throws.
     *
     * @return the count per class, for those that throw any, ordered by class name
     */
    private static Map<String, Integer> unnamedFailuresBelowRoutes() {
        var counts = new TreeMap<String, Integer>();
        for (var type : backend) {
            if (type.getPackageName().contains(".route") || isAFailureItself(type)) continue;
            var thrown = 0;
            for (var call : type.getConstructorCallsFromSelf()) {
                if (isAnUnnamedFailure(call.getTargetOwner())) thrown++;
            }
            if (thrown > 0) counts.put(type.getFullName(), thrown);
        }
        return counts;
    }

    /**
     * Whether constructing this is a failure nobody has named.
     *
     * <p>{@code RefusalResponse} is the one failure that carries a code, and it is built by
     * {@code Refusal.raise()} rather than at a throw site, so it never answers true here.
     *
     * @param target the class being constructed
     * @return true where the construction is a failure without a code
     */
    private static boolean isAnUnnamedFailure(JavaClass target) {
        if (NOT_REFUSALS.contains(target.getFullName())) return false;
        if (REFUSAL_RESPONSE.equals(target.getFullName())) return false;
        return target.isAssignableTo(HttpResponseException.class);
    }

    /**
     * Whether this class is a kind of failure itself, rather than somewhere one is raised.
     *
     * <p>Such a class calls its superclass constructor, which reads as building a failure and is
     * nothing of the sort: it is the declaration of one. Counting it would put every exception in
     * the codebase into the baseline and say the backlog is four larger than it is.
     *
     * @param type the class to judge
     * @return true where the class is itself a failure response
     */
    private static boolean isAFailureItself(JavaClass type) {
        return type.isAssignableTo(HttpResponseException.class);
    }

    /**
     * The counts recorded the last time the baseline was written.
     *
     * @return the allowed count per class, empty where the file has not been written yet
     */
    private static Map<String, Integer> readBaseline() {
        if (!Files.exists(BASELINE)) return Map.of();
        var allowed = new LinkedHashMap<String, Integer>();
        for (var line : readLines()) {
            var text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) continue;
            var split = text.lastIndexOf('=');
            if (split < 0) fail("The refusal baseline has a line that is not a count: " + text);
            allowed.put(
                    text.substring(0, split).strip(),
                    Integer.parseInt(text.substring(split + 1).strip()));
        }
        return allowed;
    }

    private static Iterable<String> readLines() {
        try {
            return Files.readAllLines(BASELINE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("The refusal baseline could not be read", e);
        }
    }

    /**
     * Writes the baseline back, refusing to record a count that has grown.
     *
     * <p>The file exists to let the backlog fall. A run that raised a number would freeze in the
     * very thing the rule is there to catch, so an update that would do so fails instead.
     *
     * @param counts what each class throws now
     * @param previous what the file allowed before
     */
    private static void writeBaseline(Map<String, Integer> counts, Map<String, Integer> previous) {
        var raised = counts.entrySet().stream()
                .filter(entry -> !previous.isEmpty() && entry.getValue() > previous.getOrDefault(entry.getKey(), 0))
                .map(Map.Entry::getKey)
                .toList();
        assertTrue(raised.isEmpty(), "Refusing to record a higher count for: " + raised);

        var text = new StringBuilder("""
                # Unnamed failure responses still thrown outside the route layer, per class.
                # A class may keep what it has and not one more; a class absent from here must throw none.
                # Written by ./toolchain.sh be-refusal-baseline, which never raises a count.
                """);
        counts.forEach(
                (type, thrown) -> text.append(type).append('=').append(thrown).append('\n'));
        try {
            Files.createDirectories(BASELINE.getParent());
            Files.writeString(BASELINE, text.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("The refusal baseline could not be written", e);
        }
    }
}

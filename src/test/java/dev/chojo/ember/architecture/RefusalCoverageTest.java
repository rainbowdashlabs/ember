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

import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every failure a reader can be shown has to be a named {@link dev.chojo.ember.api.Refusal}.
 *
 * <p>A raw {@code throw new BadRequestResponse("...")} hands the reader a sentence written at the
 * throw and nothing to quote in a report, which is the state the refusal registry replaced. That
 * holds everywhere, in a route and in the services below it alike, with no exception list.
 *
 * <p>These are plain JUnit tests rather than ArchUnit's own {@code @ArchTest} rules, and they
 * import the classes themselves. Gradle's {@code --tests} filter does not reach ArchUnit's engine,
 * so a rule written that way cannot be run on its own.
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

    private static JavaClasses backend;

    @BeforeAll
    static void importTheBackend() {
        backend = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.chojo.ember");
    }

    /**
     * No class builds a failure response itself.
     *
     * <p>Absolute, with no baseline and no exception list: whether it is thrown in a route or three
     * services below one, a failure reaches a reader, who will read it and quote it.
     */
    @Test
    void everyFailureCarriesACode() {
        var unnamed = new ArrayList<String>();
        for (var type : backend) {
            if (isAFailureItself(type)) continue;
            for (var call : type.getConstructorCallsFromSelf()) {
                if (!isAnUnnamedFailure(call.getTargetOwner())) continue;
                unnamed.add("%s builds %s itself at %s"
                        .formatted(
                                type.getSimpleName(),
                                call.getTargetOwner().getSimpleName(),
                                call.getSourceCodeLocation()));
            }
            for (var reference : type.getConstructorReferencesFromSelf()) {
                if (!isAnUnnamedFailure(reference.getTargetOwner())) continue;
                unnamed.add("%s hands over %s::new at %s"
                        .formatted(
                                type.getSimpleName(),
                                reference.getTargetOwner().getSimpleName(),
                                reference.getSourceCodeLocation()));
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
     * Whether constructing this is a failure nobody has named.
     *
     * <p>{@code RefusalResponse} and the refusals that carry more than a sentence, such as one per
     * question of a form, all carry a code, so none of them answers true here.
     *
     * @param target the class being constructed
     * @return true where the construction is a failure without a code
     */
    private static boolean isAnUnnamedFailure(JavaClass target) {
        if (NOT_REFUSALS.contains(target.getFullName())) return false;
        if (target.isAssignableTo(REFUSAL_RESPONSE)) return false;
        return target.isAssignableTo(HttpResponseException.class);
    }

    /**
     * Whether this class is a kind of failure itself, rather than somewhere one is raised.
     *
     * <p>Such a class calls its superclass constructor, which reads as building a failure and is
     * nothing of the sort: it is the declaration of one.
     *
     * @param type the class to judge
     * @return true where the class is itself a failure response
     */
    private static boolean isAFailureItself(JavaClass type) {
        return type.isAssignableTo(HttpResponseException.class);
    }
}

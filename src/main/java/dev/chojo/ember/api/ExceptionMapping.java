/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.util.DevErrorWriter;
import dev.chojo.ember.util.ProblemKey;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.util.List;

/**
 * Turns exceptions into standardized JSON error responses.
 *
 * <p>Every one of them answers with a sentence. A status on its own leaves the reader unable to
 * tell whether they sent something wrong or Ember fell over, which is the one thing they need
 * to know before deciding whether to fix it or report it, so the body always carries prose and
 * the status is chosen to say honestly whose problem this is.
 *
 * <p>The technical half of a fault never reaches the reader. A stack trace, a statement, a
 * constraint name and a file path all stay in the log, and the response carries only the short
 * reference that finds the log line. A development instance also writes each fault to the
 * development error log.
 *
 * <p>A storage move that cannot be made is a refusal with a reason, not a fault. It arrives from the
 * acts that move files as a side effect of something else, a station joining a cluster or being let
 * go, where the caller has to be told that nothing happened and why. Somebody who cannot be given an
 * account is the same kind of answer: the address is already somebody's. It is mapped here rather
 * than at each route because provisioning happens as a side effect of several acts, naming a manager
 * for a station among them, and every route that forgot the mapping turned a refusal into a fault
 * with no message at all.
 */
@Singleton
public class ExceptionMapping {
    private static final Logger log = LoggerFactory.getLogger(ExceptionMapping.class);

    private final Demo demoConfig;
    private final DevErrorWriter devErrorWriter;

    @Inject
    public ExceptionMapping(Demo demoConfig, DevErrorWriter devErrorWriter) {
        this.demoConfig = demoConfig;
        this.devErrorWriter = devErrorWriter;
    }

    /**
     * Registers every exception handler on the router.
     *
     * @param routes the router to register on
     */
    public void install(RoutesConfig routes) {
        boolean devErrors = demoConfig.dev();

        routes.exception(StepUpRequiredException.class, (err, ctx) -> {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.header("X-StepUp-Required", err.category().name());
            ctx.json(StepUpChallenge.of(err.category(), err.proofs()));
        });

        routes.exception(ApiException.class, (err, ctx) -> {
            logFailure(ctx, err.status().getCode(), err.getMessage(), err, devErrors);
            ctx.json(new ErrorResponseWrapper(err.getClass().getSimpleName(), err.getMessage()))
                    .status(err.status());
        });

        routes.exception(RefusalResponse.class, (err, ctx) -> {
            logFailure(ctx, err.getStatus(), err.getMessage(), err, devErrors);
            if (err instanceof RateLimits.TooManyRequestsException refused) {
                ctx.header("Retry-After", Long.toString(refused.retryAfterSeconds()));
            }
            ctx.json(err.body()).status(err.getStatus());
        });

        routes.exception(HttpResponseException.class, (err, ctx) -> {
            int code = err.getStatus();
            logFailure(ctx, code, err.getMessage(), err, devErrors);
            ctx.json(new ErrorResponseWrapper(HttpStatus.forStatus(code).getMessage(), err.getMessage()))
                    .status(code);
        });

        routes.exception(IllegalArgumentException.class, (err, ctx) -> {
            logRefusal(ctx, BodyRefusal.INPUT_NOT_USABLE.code(), "Invalid input", err.getMessage(), err);
            String said = Failures.readable(err.getMessage()).orElse(BodyRefusal.INPUT_NOT_USABLE.message());
            ctx.json(ErrorResponseWrapper.of(BodyRefusal.INPUT_NOT_USABLE, said))
                    .status(BodyRefusal.INPUT_NOT_USABLE.status());
        });

        routes.exception(MigrationException.class, (err, ctx) -> {
            logRefusal(ctx, err.getClass().getSimpleName(), "Storage move refused", err.getMessage(), null);
            ctx.json(new ErrorResponseWrapper("Storage Unavailable", err.getMessage()))
                    .status(HttpStatus.BAD_REQUEST);
        });

        routes.exception(StationMemberInviteService.ProvisionException.class, (err, ctx) -> {
            logRefusal(ctx, err.getClass().getSimpleName(), "Member could not be provisioned", err.getMessage(), null);
            ctx.json(new ErrorResponseWrapper("Conflict", err.getMessage())).status(HttpStatus.CONFLICT);
        });

        routes.exception(StreamReadException.class, (err, ctx) -> {
            logRefusal(ctx, BodyRefusal.BODY_NOT_JSON.code(), "Malformed body", err.getMessage(), null);
            answerRefusal(ctx, BodyRefusal.BODY_NOT_JSON, BodyRefusal.BODY_NOT_JSON.message());
        });

        routes.exception(MismatchedInputException.class, (err, ctx) -> {
            var kind = err instanceof UnrecognizedPropertyException
                    ? BodyRefusal.BODY_UNEXPECTED_FIELD
                    : BodyRefusal.BODY_DOES_NOT_MATCH;
            logRefusal(ctx, kind.code(), "Rejected body", err.getMessage(), null);
            if (err instanceof UnrecognizedPropertyException unknown) {
                var refused = BodyRefusal.BODY_UNEXPECTED_FIELD.raise(unknown.getPropertyName());
                ctx.json(refused.body()).status(refused.getStatus());
                return;
            }
            answerRefusal(
                    ctx, BodyRefusal.BODY_DOES_NOT_MATCH, atFieldPath(BodyRefusal.BODY_DOES_NOT_MATCH, err.getPath()));
        });

        routes.exception(ValueInstantiationException.class, (err, ctx) -> {
            logRefusal(ctx, BodyRefusal.BODY_VALUE_REJECTED.code(), "Rejected value in body", err.getMessage(), null);
            answerRefusal(ctx, BodyRefusal.BODY_VALUE_REJECTED, rejectedValueDetail(err));
        });

        routes.exception(Exception.class, (err, ctx) -> {
            var refusal = Failures.describe(err);
            String reference = Failures.reference();
            boolean ours = refusal.status().getCode() >= 500;
            if (ours) {
                log.error("Unhandled exception on route {} {}, reference {}", ctx.method(), ctx.path(), reference, err);
            } else {
                logRefusal(ctx, refusal.code(), "Request refused", "reference " + reference, err);
            }
            if (devErrors) devErrorWriter.write(err, ctx.method() + " " + ctx.path());
            ctx.json(new ErrorResponseWrapper(
                            refusal.status().getMessage(),
                            refusal.message(),
                            refusal.code(),
                            null,
                            ours ? reference : null,
                            null))
                    .status(refusal.status());
        });
    }

    /**
     * Writes a named refusal as the error body and status it stands for.
     */
    private static void answerRefusal(Context ctx, Refusal refusal, String message) {
        ctx.json(ErrorResponseWrapper.of(refusal, message)).status(refusal.status());
    }

    /**
     * Records a failure on its way out, at the volume its status deserves.
     *
     * <p>A fault is an error with its stack trace, a miss is whatever {@link #logNotFound} decides,
     * and any other {@code 4xx} is a refusal, see {@link #logRefusal}. A {@code 401} is left silent: an
     * expired session is the most ordinary thing that happens here.
     */
    private void logFailure(Context ctx, int code, String message, Throwable err, boolean devErrors) {
        if (code >= 500) {
            log.error("HTTP {} on {} {}: {}", code, ctx.method(), ctx.path(), message, err);
            if (devErrors) devErrorWriter.write(err, ctx.method() + " " + ctx.path());
            return;
        }
        if (code == 404) {
            logNotFound(ctx, message);
            if (devErrors) devErrorWriter.write(err, ctx.method() + " " + ctx.path());
            return;
        }
        if (code >= 400 && code != 401) logRefusal(ctx, refusalKind(code, err), "HTTP " + code, message, null);
    }

    /**
     * Records a refusal: a reader asked for something and was told no.
     *
     * <p>A refusal is no fault, and one reader can cause any number of them, each with its own ids and
     * names in the message. It is therefore marked as a refusal, which the problem log keeps apart from
     * faults, and named by its route template and its kind, so however often it happens it is one
     * problem per route and kind, while the messages keep a few readable examples. A refusal on an
     * address no route claims is named without the address, so a scanner cannot make a new problem
     * with every guess.
     *
     * @param ctx     the request that was refused
     * @param kind    what refused it: the refusal's code where it has one
     * @param what    how the line starts, saying what was refused
     * @param message what the response or the cause said
     * @param err     the cause, where its stack trace is worth keeping
     */
    private static void logRefusal(Context ctx, String kind, String what, String message, @Nullable Throwable err) {
        var endpoint = ctx.endpoints().matchedHttpEndpoint();
        String route = endpoint == null ? "unmatched" : endpoint.path;
        log.atWarn()
                .addMarker(ProblemKey.REFUSAL)
                .addKeyValue(ProblemKey.NAME, "refusal " + kind + " " + ctx.method() + " " + route)
                .setCause(err)
                .log("{} on {} {}: {}", what, ctx.method(), ctx.path(), message);
    }

    /**
     * The kind a refusal is named by: the refusal's code, the exception's own name where it stands for
     * one particular refusal, or the status where nothing more is known.
     */
    private static String refusalKind(int code, Throwable err) {
        if (err instanceof RefusalResponse refused) return refused.refusal().code();
        if (err.getClass() != ApiException.class && err instanceof ApiException) {
            return err.getClass().getSimpleName();
        }
        return "HTTP " + code;
    }

    /**
     * Records that nothing was found at an address, but only where a route answered it.
     *
     * <p>A 404 behind a real route says something that ought to be there is not, such as an appointment
     * that was deleted while somebody still had it open, and that is worth an operator's attention. A 404
     * for an address no route claims is a guess, almost always a scanner trying file after file, and
     * worth none: it is logged at debug level and never reaches the problem log. The route's template
     * names the problem, so a hundred missing appointments are one problem with a count, while each
     * message keeps the address that was asked for.
     *
     * @param ctx     the request that found nothing
     * @param message what the response said
     */
    private static void logNotFound(Context ctx, String message) {
        var endpoint = ctx.endpoints().matchedHttpEndpoint();
        if (endpoint == null) {
            log.debug("404 on {} {}: {}", ctx.method(), ctx.path(), message);
            return;
        }
        log.atWarn()
                .addKeyValue(ProblemKey.NAME, "404 " + ctx.method() + " " + endpoint.path)
                .log("404 on {} {}: {}", ctx.method(), ctx.path(), message);
    }

    /**
     * Names the place in a body a refusal was about, where Jackson recorded one.
     *
     * <p>Naming the place is the difference between a reader guessing and a reader looking. The
     * place is spelled the way the sender wrote it, out of their own field names, so nothing of
     * the type it failed to become is revealed.
     */
    private static String atFieldPath(Refusal refusal, List<JacksonException.Reference> path) {
        return Failures.fieldPath(path)
                .map(where -> refusal.message() + ", at " + where)
                .orElse(refusal.message());
    }

    /**
     * Says what was wrong with a value the body carried that whatever it describes refused to take.
     *
     * <p>This is the refusal a record writes in its own constructor, so its wording is the most
     * useful thing there is to pass on, and it is passed on wherever it reads as prose rather than
     * as machinery.
     */
    private static String rejectedValueDetail(ValueInstantiationException err) {
        String where =
                Failures.fieldPath(err.getPath()).map(path -> ", at " + path).orElse("");
        String said = err.getCause() == null ? null : err.getCause().getMessage();
        return Failures.readable(said)
                .map(prose -> prose + where)
                .orElse(BodyRefusal.BODY_VALUE_REJECTED.message() + where);
    }
}

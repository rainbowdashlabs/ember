/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.util.DevErrorWriter;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

    @Inject
    public ExceptionMapping(Demo demoConfig) {
        this.demoConfig = demoConfig;
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
            ctx.json(err.body()).status(err.getStatus());
        });

        routes.exception(HttpResponseException.class, (err, ctx) -> {
            int code = err.getStatus();
            logFailure(ctx, code, err.getMessage(), err, devErrors);
            Long retryAfter = null;
            if (err instanceof RateLimits.TooManyRequestsException refused) {
                retryAfter = refused.retryAfterSeconds();
                ctx.header("Retry-After", Long.toString(retryAfter));
            }
            ctx.json(new ErrorResponseWrapper(HttpStatus.forStatus(code).getMessage(), err.getMessage(), retryAfter))
                    .status(code);
        });

        routes.exception(IllegalArgumentException.class, (err, ctx) -> {
            log.warn("Invalid input on {} {}: {}", ctx.method(), ctx.path(), err.getMessage(), err);
            String said = Failures.readable(err.getMessage()).orElse(Refusal.INPUT_NOT_USABLE.message());
            ctx.json(ErrorResponseWrapper.of(Refusal.INPUT_NOT_USABLE, said)).status(Refusal.INPUT_NOT_USABLE.status());
        });

        routes.exception(MigrationException.class, (err, ctx) -> {
            log.warn("Storage move refused on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.json(new ErrorResponseWrapper("Storage Unavailable", err.getMessage()))
                    .status(HttpStatus.BAD_REQUEST);
        });

        routes.exception(StationMemberInviteService.ProvisionException.class, (err, ctx) -> {
            log.warn("Member could not be provisioned on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            ctx.json(new ErrorResponseWrapper("Conflict", err.getMessage())).status(HttpStatus.CONFLICT);
        });

        routes.exception(StreamReadException.class, (err, ctx) -> {
            log.warn("Malformed body on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            answerRefusal(ctx, Refusal.BODY_NOT_JSON, Refusal.BODY_NOT_JSON.message());
        });

        routes.exception(MismatchedInputException.class, (err, ctx) -> {
            log.warn("Rejected body on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            if (err instanceof UnrecognizedPropertyException unknown) {
                answerRefusal(
                        ctx,
                        Refusal.BODY_UNEXPECTED_FIELD,
                        Refusal.BODY_UNEXPECTED_FIELD.message() + ": " + unknown.getPropertyName());
                return;
            }
            answerRefusal(ctx, Refusal.BODY_DOES_NOT_MATCH, atFieldPath(Refusal.BODY_DOES_NOT_MATCH, err.getPath()));
        });

        routes.exception(ValueInstantiationException.class, (err, ctx) -> {
            log.warn("Rejected value in body on {} {}: {}", ctx.method(), ctx.path(), err.getMessage());
            answerRefusal(ctx, Refusal.BODY_VALUE_REJECTED, rejectedValueDetail(err));
        });

        routes.exception(Exception.class, (err, ctx) -> {
            var refusal = Failures.describe(err);
            String reference = Failures.reference();
            boolean ours = refusal.status().getCode() >= 500;
            if (ours) {
                log.error("Unhandled exception on route {} {}, reference {}", ctx.method(), ctx.path(), reference, err);
            } else {
                log.warn("Request refused on route {} {}, reference {}", ctx.method(), ctx.path(), reference, err);
            }
            if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            ctx.json(new ErrorResponseWrapper(
                            refusal.status().getMessage(),
                            refusal.message(),
                            refusal.code(),
                            null,
                            ours ? reference : null))
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
     * and an ordinary refusal is a warning without one, because a reader sending something wrong is
     * not an event an operator needs a trace for. A {@code 401} is left silent: an expired session
     * is the most ordinary thing that happens here.
     */
    private static void logFailure(Context ctx, int code, String message, Throwable err, boolean devErrors) {
        if (code >= 500) {
            log.error("HTTP {} on {} {}: {}", code, ctx.method(), ctx.path(), message, err);
            if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            return;
        }
        if (code == 404) {
            logNotFound(ctx, message);
            if (devErrors) DevErrorWriter.write(err, ctx.method() + " " + ctx.path());
            return;
        }
        if (code >= 400 && code != 401) {
            log.warn("HTTP {} on {} {}: {}", code, ctx.method(), ctx.path(), message);
        }
    }

    /**
     * Records that nothing was found at an address, quietly where the address is one scanners try
     * everywhere.
     *
     * <p>A 404 is worth an operator's attention when a client asks for something that ought to be
     * there, and worth none when it is the hundredth guess at a credentials file. Since the address is
     * part of what the fault log writes down, every spelling of a probe arrived as a fault of its own
     * and the ones worth reading were lost among them.
     *
     * @param ctx     the request that found nothing
     * @param message what the response said, which for an address no route claims is Javalin's own wording
     */
    private static void logNotFound(Context ctx, String message) {
        if (ScannerProbes.looksLikeAProbe(ctx.path())) {
            log.debug("404 on {} {}: {}", ctx.method(), ctx.path(), message);
            return;
        }
        log.warn("404 on {} {}: {}", ctx.method(), ctx.path(), message);
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
                .orElse(Refusal.BODY_VALUE_REJECTED.message() + where);
    }
}

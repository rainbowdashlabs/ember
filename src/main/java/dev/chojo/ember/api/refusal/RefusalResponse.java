/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import dev.chojo.ember.api.ErrorResponseWrapper;
import io.javalin.http.HttpResponseException;
import org.jspecify.annotations.Nullable;

/**
 * A named refusal on its way out of a route.
 *
 * <p>It is a {@link HttpResponseException} so that everything already built on those keeps working,
 * and it carries the {@link Refusal} so the error body can name which refusal this was. Routes do
 * not construct it: {@link Refusal#raise()} does, which is what keeps a code and its sentence from
 * being paired up differently in two places.
 */
public class RefusalResponse extends HttpResponseException {
    private final Refusal refusal;
    private final transient @Nullable RefusalDetail detail;

    /**
     * A refusal with the sentence it is answered with. Only {@link Refusal#raise()} and refusals that
     * carry more than a sentence make one.
     *
     * @param refusal what refused
     * @param message the sentence the reader is shown
     */
    protected RefusalResponse(Refusal refusal, String message) {
        this(refusal, message, null);
    }

    /**
     * A refusal with the sentence it is answered with and the value it was about.
     *
     * @param refusal what refused
     * @param message the sentence the reader is shown, with the detail named in it
     * @param detail  the value the refusal was about, or {@code null} when it named none
     */
    protected RefusalResponse(Refusal refusal, String message, @Nullable RefusalDetail detail) {
        super(refusal.status().getCode(), message);
        this.refusal = refusal;
        this.detail = detail;
    }

    /**
     * What the refusal is answered with. A refusal that says more than one sentence, such as one per
     * question of a form, answers with that as well.
     *
     * @return the error body
     */
    public Object body() {
        return ErrorResponseWrapper.of(refusal, getMessage(), null, detail);
    }

    /**
     * Which refusal this was.
     *
     * @return the refusal, whose code is what the reader and a report see
     */
    public Refusal refusal() {
        return refusal;
    }

    /**
     * The value this refusal was raised about.
     *
     * @return the detail, or {@code null} when it was raised without one
     */
    public @Nullable RefusalDetail detail() {
        return detail;
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.javalin.openapi.OpenApiName;
import org.jspecify.annotations.Nullable;

/**
 * Where a reader goes from a page: on to the page below, to a page further down named by its key, or
 * nowhere, which sends the form.
 *
 * @param kind which of the three it is
 * @param page the key of the page gone to, set only for {@link TargetKind#PAGE}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageTarget(TargetKind kind, @Nullable String page) {
    /** On to the page below, or the end of the form where there is none. */
    public static final PageTarget NEXT = new PageTarget(TargetKind.NEXT, null);

    /** The form is sent. */
    public static final PageTarget SUBMIT = new PageTarget(TargetKind.SUBMIT, null);

    /**
     * The given target, or {@link #NEXT} where none was given.
     *
     * @param target what was sent or stored, possibly nothing
     * @return a target that always has a kind
     */
    public static PageTarget orNext(PageTarget target) {
        if (target == null || target.kind() == null) return NEXT;
        return target;
    }

    /**
     * A page further down, named by its key.
     *
     * @param page the key of the page
     * @return the target
     */
    public static PageTarget page(String page) {
        return new PageTarget(TargetKind.PAGE, page);
    }

    /** The three places a page can lead to. */
    @OpenApiName("FormPageTargetKind")
    public enum TargetKind {
        /** The page below, or the end of the form after the last page. */
        NEXT,
        /** A chosen page further down. */
        PAGE,
        /** The end of the form. */
        SUBMIT
    }
}

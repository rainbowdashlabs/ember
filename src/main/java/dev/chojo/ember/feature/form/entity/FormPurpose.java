/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import io.javalin.openapi.OpenApiName;

/**
 * Form audience and sidebar entry point.
 *
 * <p>The purpose discriminates between the three places a {@link Form} can surface in the UI and
 * constrains which {@link FormQuestionType} values may be used (see
 * {@link FormQuestionType#allowedFor(FormPurpose)}).
 */
@OpenApiName("FormPurpose")
public enum FormPurpose {
    /** Members-only form, surfaced under {@code /station/forms}. */
    INTERNAL,
    /** Publicly answerable contact form, surfaced under {@code /station/pages/forms}. */
    CONTACT,
    /** Publicly answerable poll, surfaced under {@code /station/pages/polls}. */
    POLL
}

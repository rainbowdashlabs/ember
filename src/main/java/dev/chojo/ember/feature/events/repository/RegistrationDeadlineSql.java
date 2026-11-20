/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Writes a {@link RegistrationDeadline} into the columns that hold it: all four on an appointment and an
 * event group, and the three of the rule alone on a template, which knows no date to fix a deadline to.
 */
public final class RegistrationDeadlineSql {

    /** The column assignments of an {@code UPDATE}, bound by {@link #bind(Call, RegistrationDeadline)}. */
    public static final String SET = """
            registration_deadline                = :registration_deadline,
            registration_deadline_kind           = :registration_deadline_kind,
            registration_deadline_offset_minutes = :registration_deadline_offset_minutes,
            registration_deadline_anchor         = :registration_deadline_anchor""";

    /**
     * The column assignments of an {@code UPDATE} on a table holding the rule without a fixed instant,
     * bound by {@link #bindRule(Call, RegistrationDeadline)}.
     */
    public static final String RULE_SET = """
            registration_deadline_kind           = :registration_deadline_kind,
            registration_deadline_offset_minutes = :registration_deadline_offset_minutes,
            registration_deadline_anchor         = :registration_deadline_anchor""";

    /** The column names, in the order of {@link #VALUES}. */
    public static final String COLUMNS =
            "registration_deadline, registration_deadline_kind, registration_deadline_offset_minutes, registration_deadline_anchor";

    /** The bound values of an {@code INSERT}, in the order of {@link #COLUMNS}. */
    public static final String VALUES =
            ":registration_deadline, :registration_deadline_kind, :registration_deadline_offset_minutes, :registration_deadline_anchor";

    private RegistrationDeadlineSql() {}

    /**
     * Binds the four values of a deadline.
     *
     * @param call     the call to bind on
     * @param deadline the rule
     * @return the same call
     */
    public static Call bind(Call call, RegistrationDeadline deadline) {
        return bindRule(call.bind("registration_deadline", deadline.at(), INSTANT_TIMESTAMP), deadline);
    }

    /**
     * Binds the three values of the rule, leaving out the fixed instant.
     *
     * @param call     the call to bind on
     * @param deadline the rule
     * @return the same call
     */
    public static Call bindRule(Call call, RegistrationDeadline deadline) {
        return call.bind("registration_deadline_kind", deadline.kind())
                .bind("registration_deadline_offset_minutes", deadline.offsetMinutes())
                .bind("registration_deadline_anchor", deadline.anchor());
    }
}

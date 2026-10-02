/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Which of the comments on a target a listing shows.
 */
public sealed interface CommentFilter {

    /** Every comment on the target, deleted placeholders included. */
    CommentFilter ALL = new All();

    /** Every comment on the target. */
    record All() implements CommentFilter {}

    /**
     * The comments on one occurrence of a recurring appointment.
     *
     * @param date the occurrence, or {@code null} for the comments on the appointment as a whole
     */
    record Occurrence(@Nullable LocalDate date) implements CommentFilter {}

    /**
     * The comments written from one station, which is what a station reads under a news entry the
     * instance published to every station.
     *
     * @param stationUid the station the authors belong to
     */
    record FromStation(UUID stationUid) implements CommentFilter {}
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.generator.entity.RequirementSignature;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Where the signatures stand that the participants' copies of the documents an appointment asks for call
 * for. Signing owns them; the documents to bring only show them.
 */
@FunctionalInterface
public interface RequirementSignatures {

    /** Knows of no signatures: every copy is only generated or not. */
    RequirementSignatures NONE = (reader, eventId, date, memberIds) -> List.of();

    /**
     * @param reader    who reads them, which decides the fields they can sign now
     * @param eventId   the appointment
     * @param date      the date of the appointment
     * @param memberIds the participants
     * @return the latest signatures asked for per document and participant, none where none were
     */
    List<RequirementSignature> of(StationSession reader, int eventId, LocalDate date, Collection<Integer> memberIds);
}

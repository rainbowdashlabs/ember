/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import java.util.Collection;
import java.util.List;

/**
 * Which document templates a step of a procedure can generate, as the documents feature decides it, so
 * the procedures feature does not need to know how a template is drawn.
 */
public interface DocumentStepTemplates {
    /**
     * Whether a document template can be the document of a step at a station: in use there, not one
     * meant for appointments, and asking the member or a guardian to sign.
     *
     * @param stationId  the station
     * @param templateId the document template
     * @return whether a step may generate it
     */
    boolean suitable(int stationId, int templateId);

    /**
     * Whether a document template is meant for a member, as its audience says, which every document
     * generated for them has to be.
     *
     * @param stationId  the station
     * @param templateId the document template
     * @param memberId   the member
     * @return whether its documents may be generated for the member
     */
    boolean meantFor(int stationId, int templateId, int memberId);

    /**
     * The names of the document templates steps of a station ask to sign, as a step shows them.
     *
     * @param stationId   the station
     * @param templateIds the document templates
     * @return one per template the station may still generate from, archived ones included
     */
    List<StepDocument> named(int stationId, Collection<Integer> templateIds);

    /**
     * A document template a step asks to sign.
     *
     * @param id   the document template
     * @param name its name
     */
    record StepDocument(int id, String name) {}
}

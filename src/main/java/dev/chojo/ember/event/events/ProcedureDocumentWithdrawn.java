/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

import java.util.List;

/**
 * A signature a document step of a procedure rested on was withdrawn, so the step is open again.
 *
 * @param stationId     the station
 * @param procedureId   the procedure
 * @param procedureName its name
 * @param itemId        the step
 * @param itemTitle     the step's title
 * @param toldMemberIds who is told: whoever resolved the procedure, where it was resolved
 * @param withdrawnBy   the member who withdrew it
 */
public record ProcedureDocumentWithdrawn(
        int stationId,
        int procedureId,
        String procedureName,
        int itemId,
        String itemTitle,
        List<Integer> toldMemberIds,
        int withdrawnBy)
        implements DomainEvent {}

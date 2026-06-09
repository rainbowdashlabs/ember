/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

import java.util.List;

public record ProcedureResolved(
        int stationId, int procedureId, String procedureName, List<Integer> assigneeMemberIds, int resolvedByMemberId)
        implements DomainEvent {}

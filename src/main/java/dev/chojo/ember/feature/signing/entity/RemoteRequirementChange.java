/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;

/**
 * What the station holding a shared appointment tells a partner station when the documents the appointment
 * asks for changed. It travels signed with the organiser's federation key, like every federation request.
 *
 * <p>Documents added travel without a list: the partner fetches the documents of each date again, the same way
 * as at registration, and asks for those its members do not stand asked for yet.
 *
 * @param removedTemplateIds the documents taken off, by the ids of their templates at the organiser
 * @param registered         the partner's members registered on dates still ahead, each with the date
 */
public record RemoteRequirementChange(List<Integer> removedTemplateIds, List<RemoteRegisteredMember> registered) {

    public RemoteRequirementChange {
        removedTemplateIds = List.copyOf(removedTemplateIds);
        registered = List.copyOf(registered);
    }
}

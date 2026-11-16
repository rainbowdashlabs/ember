/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The documents a partner station's event group asks a member of this station to sign, handled the way a
 * shared appointment's are: per part, since every part is an appointment of the partner and its documents
 * are handed out per date there.
 *
 * <p>Before a sign-up, a change of parts or a withdrawal, the parts the member holds are read from the
 * partner. Afterwards every part newly held takes on its documents ({@link PartnerAppointmentSignatures#registered})
 * and every part given up lets the signatures still open on it go. A part's date is the day it starts as this
 * station's calendar reads it. A partner that cannot be asked leaves the documents as they were; the sign-up
 * stands either way.
 */
@Singleton
public class PartnerEventGroupSignatures {
    private static final Logger log = LoggerFactory.getLogger(PartnerEventGroupSignatures.class);

    private final PartnerEventGroupService partnerEventGroups;
    private final PartnerAppointmentSignatures signatures;
    private final OccurrenceCalendar calendar;

    @Inject
    public PartnerEventGroupSignatures(
            PartnerEventGroupService partnerEventGroups,
            PartnerAppointmentSignatures signatures,
            OccurrenceCalendar calendar) {
        this.partnerEventGroups = partnerEventGroups;
        this.signatures = signatures;
        this.calendar = calendar;
    }

    /**
     * The parts a member holds at a partner's event group, with the date of every part.
     *
     * @param held  the parts the member holds
     * @param dates the date of every part of the event group
     */
    public record Holding(Set<Integer> held, Map<Integer, LocalDate> dates) {
        /** Nothing known: no part held, no date. */
        public static Holding unknown() {
            return new Holding(Set.of(), Map.of());
        }
    }

    /**
     * Reads which parts the member holds now, before their sign-up changes.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId      the event group, by its id there
     * @param member            the member
     * @return what they hold; nothing known where the partner cannot be asked
     */
    public Holding before(int stationId, UUID partnerStationUid, int eventGroupId, StationMember member) {
        try {
            var detail = partnerEventGroups.eventGroup(
                    stationId, partnerStationUid, eventGroupId, Map.of(member.uid(), member.id()));
            // TODO the partner sends no date for a part, so its day is read on this station's calendar
            var zone = calendar.zoneOf(stationId);
            var dates = detail.parts().stream()
                    .collect(Collectors.toMap(
                            EventGroupPartState::eventId,
                            part -> part.startTime().atZone(zone).toLocalDate(),
                            (a, b) -> a));
            var held = detail.standings().stream()
                    .filter(standing -> standing.memberUid().equals(member.uid()))
                    .flatMap(standing -> standing.parts().stream())
                    .collect(Collectors.toSet());
            return new Holding(held, dates);
        } catch (RuntimeException e) {
            log.warn("Could not read the parts member {} holds at partner {}", member.id(), partnerStationUid, e);
            // TODO a failed read releases nothing on the parts given up meanwhile
            return Holding.unknown();
        }
    }

    /**
     * Takes on the documents of every part newly held and lets the open signatures of every part given up go.
     *
     * @param session           who acted: the member, a guardian or a manager
     * @param partnerStationUid the partner holding the event group
     * @param before            what the member held before
     * @param heldNow           the parts the member holds now
     * @param memberUid         the member
     */
    public void after(
            StationSession session, UUID partnerStationUid, Holding before, Set<Integer> heldNow, UUID memberUid) {
        var added = new HashSet<>(heldNow);
        added.removeAll(before.held());
        var dropped = new HashSet<>(before.held());
        dropped.removeAll(heldNow);
        for (int part : added) {
            var date = before.dates().get(part);
            if (date != null) signatures.registered(session, partnerStationUid, part, date, memberUid);
        }
        for (int part : dropped) {
            var date = before.dates().get(part);
            if (date != null) signatures.withdrawn(session, partnerStationUid, part, date, memberUid);
        }
    }
}

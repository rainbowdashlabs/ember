/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.WaitingListRefusal;
import dev.chojo.ember.feature.mail.entity.WaitlistInvitationDetails;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What the open web reaches of a station's waiting lists: the station a public address names, the
 * lists it put in public, and the appointment an invitation link is about.
 */
@Singleton
public class PublicWaitingListService {
    private final WaitingListService lists;
    private final StationRepository stations;
    private final WaitlistInvitationMessage invitationMessage;
    private final ModuleAvailability modules;

    @Inject
    public PublicWaitingListService(
            WaitingListService lists,
            StationRepository stations,
            WaitlistInvitationMessage invitationMessage,
            ModuleAvailability modules) {
        this.modules = modules;
        this.lists = lists;
        this.stations = stations;
        this.invitationMessage = invitationMessage;
    }

    /**
     * The station a public address names, by its uid or its readable name, when it shows its
     * waiting lists in public and has not switched its waiting list off, nor had it denied by its
     * cluster. What somebody already put on a list stays theirs to look at and take back.
     *
     * @return the station's id
     */
    public int stationIdFor(String address) {
        var station = stations.findByAddress(address)
                .orElseThrow(WaitingListRefusal.STATION_NOT_HERE_BEHIND_PUBLIC_LIST::raise);
        if (!station.publicWaitlistEnabled() || StationModule.WAITING_LIST.isOffIn(modules.disabledAt(station.id()))) {
            throw WaitingListRefusal.PUBLIC_WAITING_LISTS_SWITCHED_OFF.raise();
        }
        return station.id();
    }

    /**
     * Refuses an invite code of a station whose waiting list is switched off, by itself or its cluster:
     * a link sent out earlier takes no new sign-ups while the list is gone. An unknown code is left to
     * the caller's own refusal.
     *
     * @param code the invite code
     */
    public void requireInviteTakesSignUps(String code) {
        lists.findInviteByCode(code)
                .flatMap(invite -> lists.findById(invite.listId()))
                .filter(list -> StationModule.WAITING_LIST.isOffIn(modules.disabledAt(list.stationId())))
                .ifPresent(list -> {
                    throw WaitingListRefusal.PUBLIC_WAITING_LISTS_SWITCHED_OFF.raise();
                });
    }

    /**
     * A list of the station that is open to the public.
     *
     * @param notHere what a list that is not here, belongs to another station or is not public is
     *                refused with
     */
    public WaitingList publicList(int stationId, int listId, Refusal notHere) {
        var list = lists.findById(listId).orElseThrow(notHere::raise);
        if (list.stationId() != stationId || !list.isPublic()) throw notHere.raise();
        return list;
    }

    /**
     * The appointment an invitation is for, written exactly as the mail wrote it.
     */
    public WaitlistInvitationDetails invitationDetails(int stationId, WaitingListInvitation invitation) {
        var station =
                stations.findById(stationId).orElseThrow(WaitingListRefusal.STATION_NOT_HERE_BEHIND_INVITATION::raise);
        return invitationMessage.describe(station, invitation);
    }
}

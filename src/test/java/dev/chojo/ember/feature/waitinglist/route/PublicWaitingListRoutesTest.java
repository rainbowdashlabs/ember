/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.WaitingListRefusal;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListRateLimiter;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The waiting lists a station shows the open web, over HTTP.
 */
class PublicWaitingListRoutesTest {
    private static final String BASE = PREFIX + "/public/station/nord/waitlists";

    private WaitingListService lists;
    private PublicWaitingListService publicLists;
    private RouteHarness harness;

    private static WaitingList list(boolean sendsMail) {
        var list = mock(WaitingList.class);
        when(list.id()).thenReturn(5);
        when(list.name()).thenReturn("Jugend");
        when(list.sendsMail()).thenReturn(sendsMail);
        return list;
    }

    @BeforeEach
    void setup() {
        lists = mock(WaitingListService.class);
        publicLists = mock(PublicWaitingListService.class);
        when(publicLists.stationIdFor("nord")).thenReturn(3);
        harness = RouteHarness.serving(new WaitingListRoutes(
                lists,
                publicLists,
                mock(ConsentService.class),
                mock(PublicWaitingListRateLimiter.class),
                mock(EventCrudService.class),
                mock(EventRestrictionService.class)));
    }

    @Test
    void theStationsPublicListsAndAListsFormAreShown() {
        var open = list(false);
        when(lists.findPublicByStation(3)).thenReturn(List.of(open));
        when(publicLists.publicList(3, 5, WaitingListRefusal.PUBLIC_WAITING_LIST_NOT_HERE))
                .thenReturn(open);

        harness.run((server, client) -> {
            assertEquals("Jugend", json(client.get(BASE)).get(0).path("name").asString());
            assertEquals(
                    "Jugend",
                    json(client.get(BASE + "/5/form")).path("listName").asString());
        });

        verify(lists).findPublicFieldsByList(5);
    }

    @Test
    void aRegistrationNeedsAFirstNameAndAnAddressWhereMailFollows() {
        var mailing = list(true);
        when(publicLists.publicList(3, 5, WaitingListRefusal.PUBLIC_WAITING_LIST_NOT_HERE_ON_REGISTRATION))
                .thenReturn(mailing);

        harness.run((server, client) -> {
            assertEquals(
                    WaitingListRefusal.PUBLIC_REGISTRATION_NEEDS_A_FIRST_NAME,
                    refusalOf(client.post(BASE + "/5/register", body("{\"firstname\": \" \"}"))));
            assertEquals(
                    WaitingListRefusal.PUBLIC_REGISTRATION_NEEDS_AN_ADDRESS,
                    refusalOf(client.post(BASE + "/5/register", body("{\"firstname\": \"Kim\"}"))));
        });

        verify(lists, never()).submitPublicRegistration(anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aRegistrationIsTakenWithItsGuardians() {
        var mailing = list(true);
        when(publicLists.publicList(3, 5, WaitingListRefusal.PUBLIC_WAITING_LIST_NOT_HERE_ON_REGISTRATION))
                .thenReturn(mailing);

        var answer = harness.request(client -> client.post(BASE + "/5/register", body("""
                        {"firstname": "Kim", "email": "kim@test.com",
                         "guardians": [{"firstname": "Mara", "lastname": null, "email": null, "phone": null}]}""")));

        assertEquals(202, answer.code());
        assertEquals("verification_email_sent", json(answer).path("status").asString());
        verify(lists)
                .submitPublicRegistration(eq(5), eq("Kim"), eq(""), eq("kim@test.com"), any(), any(), any(), any());
    }

    @Test
    void anAddressThatNamesNoStationIsRefusedAsTheServiceSays() {
        when(publicLists.stationIdFor(anyString()))
                .thenThrow(WaitingListRefusal.STATION_NOT_HERE_BEHIND_PUBLIC_LIST.raise());

        var answer = harness.request(client -> client.get(PREFIX + "/public/station/west/waitlists"));

        assertEquals(WaitingListRefusal.STATION_NOT_HERE_BEHIND_PUBLIC_LIST, refusalOf(answer));
    }
}

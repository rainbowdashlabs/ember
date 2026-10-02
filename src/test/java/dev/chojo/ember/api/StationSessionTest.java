/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A station session holds a station and a membership, or is refused. */
class StationSessionTest {

    @Test
    void aMemberActingAtTheirStationGetsTheStationAndTheMembership() {
        var session = StationSession.of(TestSessions.member(7, StationPermission.NEWS_EDIT));

        assertEquals(7, session.stationId());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000007"), session.stationUid());
        assertEquals(TestSessions.MEMBER_ID, session.member().id());
        assertEquals(TestSessions.ACCOUNT_ID, session.accountId());
        assertEquals(StationUserType.MEMBER, session.userType());
        assertTrue(session.hasPermission(StationPermission.NEWS_EDIT));
    }

    @Test
    void aRequestNamingNoStationIsRefusedAsABadRequest() {
        var refused = assertThrows(RefusalResponse.class, () -> StationSession.of(TestSessions.administrator()));

        assertEquals(GeneralRefusal.NO_STATION_CHOSEN, refused.refusal());
        assertEquals(400, refused.getStatus());
    }

    @Test
    void somebodyWhoIsNoMemberOfTheNamedStationIsForbidden() {
        var outsider = new UserSession(
                TestSessions.account(),
                1,
                7,
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                null,
                Set.of(StationPermission.LOGIN),
                Set.of(),
                null);

        var refused = assertThrows(RefusalResponse.class, () -> StationSession.of(outsider));

        assertEquals(GeneralRefusal.NOT_A_MEMBER_OF_THIS_STATION, refused.refusal());
        assertEquals(403, refused.getStatus());
    }
}

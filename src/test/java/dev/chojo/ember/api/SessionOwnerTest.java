/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Each kind of session names the owner it acts for, and only from what the session holds. */
class SessionOwnerTest {

    @Test
    void aStationSessionActsForItsStation() {
        assertEquals(
                new Owner.Station(7), StationSession.of(TestSessions.member(7)).owner());
    }

    @Test
    void anAssociationSessionActsForItsAssociation() {
        var session = TestSessions.clusterMember(4, ClusterPermission.CLUSTER_MEMBER_READ);

        assertEquals(new Owner.Association(4), session.association(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_FIELDS));
    }

    @Test
    void aSessionNamingNoAssociationIsRefusedWithTheRoutesOwnRefusal() {
        var refused = assertThrows(RefusalResponse.class, () -> TestSessions.member(7)
                .association(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_FIELDS));

        assertEquals(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_FIELDS, refused.refusal());
    }

    @Test
    void theInstanceIsOneOwner() {
        assertEquals(new Owner.Instance(), TestSessions.administrator().instance());
    }
}

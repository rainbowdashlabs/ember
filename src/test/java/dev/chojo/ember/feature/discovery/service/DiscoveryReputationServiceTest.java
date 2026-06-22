/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryReputationServiceTest extends RepositoryTestBase {

    private static DiscoveryReputationService service;

    @BeforeAll
    static void init() {
        service = new DiscoveryReputationService(discoveryPeerRepo);
    }

    @BeforeEach
    void seed() {
        discoveryPeerRepo.upsert("rep-key", "https://rep.example", "fp-rep", PeerSource.MANUAL, null);
    }

    private int currentRep() {
        return discoveryPeerRepo.findByPublicKey("rep-key").orElseThrow().reputation();
    }

    @Test
    void deltasApply() {
        int start = currentRep();
        service.recordSuccessfulCallback("rep-key");
        service.recordSuccessfulFetch("rep-key");
        assertEquals(start + 2, currentRep());

        service.recordTimeout("rep-key");
        assertEquals(start + 1, currentRep());

        service.recordInvalidAnnouncement("rep-key");
        assertEquals(start + 1 - 2, currentRep());

        service.recordSignatureFailure("rep-key");
        assertEquals(start + 1 - 2 - 20, currentRep());

        service.upvote("rep-key");
        assertEquals(start + 1 - 2 - 20 + 50, currentRep());

        service.downvote("rep-key");
        assertEquals(start + 1 - 2 - 20, currentRep());
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsFederationShare;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsFederationRepositoryTest extends RepositoryTestBase {
    private static NewsFederationRepository fedRepo;
    private static FederationRepository federationRepo;
    private static FederationService federationService;

    private static Station station;
    private static Account account;
    private static News news1;
    private static News news2;
    private static News news3;
    private static int partnerId;
    private static int partnerIdB;
    private static int shareId;

    @BeforeAll
    static void setup() {
        fedRepo = new NewsFederationRepository();
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());

        station = stationRepo.create("NewsFedRepoStation");
        var stationB = stationRepo.create("NewsFedRepoStationB");
        var stationC = stationRepo.create("NewsFedRepoStationC");
        account = accountRepo.create("newsfed@test.com", "NewsFed", "User");
        StationMember member = stationMemberRepo.create(station.id(), account.id());

        // Create news articles (published at creation)
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        news1 = newsRepo.create(station.id(), "Fed News 1", "# One", "<h1>One</h1>", authorIdentity);
        news2 = newsRepo.create(station.id(), "Fed News 2", "# Two", "<h1>Two</h1>", authorIdentity);
        news3 = newsRepo.create(station.id(), "Fed News 3", "# Three", "<h1>Three</h1>", authorIdentity);

        // Create federation partners
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                station.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerId = partner.id();

        var keyPairB = federationService.generateKeyPair();
        var partnerB = federationService.acceptInvite(
                station.id(), stationC.id(), federationService.encodePublicKey(keyPairB), null, null);
        partnerIdB = partnerB.id();

        // Create a comment for federated author tests
        var comment = newsRepo.createComment(news1.id(), null, authorIdentity, "Test comment");
        int commentId = comment.id();
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(station.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- setShare and findShareByNews --

    @Test
    @Order(1)
    void setShareAllPartners() {
        var share = fedRepo.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER);
        assertNotNull(share);
        assertEquals(news1.id(), share.newsId());
        assertEquals(ShareScope.ALL_PARTNERS, share.scope());
        assertEquals(NewsVisibilityRole.MEMBER, share.visibilityRole());
        shareId = share.id();
    }

    @Test
    @Order(2)
    void findShareByNews() {
        var found = fedRepo.findShareByNews(news1.id());
        assertTrue(found.isPresent());
        assertEquals(shareId, found.get().id());
        assertEquals(ShareScope.ALL_PARTNERS, found.get().scope());
        assertEquals(NewsVisibilityRole.MEMBER, found.get().visibilityRole());
    }

    @Test
    @Order(3)
    void findShareByNewsMissing() {
        assertTrue(fedRepo.findShareByNews(99999).isEmpty());
    }

    // -- setShare upsert (ON CONFLICT) --

    @Test
    @Order(4)
    void setShareUpdatesExisting() {
        var updated = fedRepo.setShare(news1.id(), ShareScope.SPECIFIC, NewsVisibilityRole.TEAM);
        assertEquals(shareId, updated.id(), "Should reuse same share ID");
        assertEquals(ShareScope.SPECIFIC, updated.scope());
        assertEquals(NewsVisibilityRole.TEAM, updated.visibilityRole());
    }

    // -- Share targets --

    @Test
    @Order(10)
    void setShareTargets() {
        fedRepo.setShareTargets(shareId, List.of(partnerId, partnerIdB));
        var targets = fedRepo.findShareTargets(shareId);
        assertEquals(2, targets.size());
        assertTrue(targets.contains(partnerId));
        assertTrue(targets.contains(partnerIdB));
    }

    @Test
    @Order(11)
    void setShareTargetsReplacesExisting() {
        fedRepo.setShareTargets(shareId, List.of(partnerId));
        var targets = fedRepo.findShareTargets(shareId);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(partnerId));
        assertFalse(targets.contains(partnerIdB));
    }

    @Test
    @Order(12)
    void setShareTargetsEmpty() {
        fedRepo.setShareTargets(shareId, List.of());
        var targets = fedRepo.findShareTargets(shareId);
        assertTrue(targets.isEmpty());
    }

    @Test
    @Order(13)
    void findShareTargetsForNonExistentShare() {
        var targets = fedRepo.findShareTargets(99999);
        assertTrue(targets.isEmpty());
    }

    // -- findSharedNewsIds --

    @Test
    @Order(20)
    void findSharedNewsIdsAllPartners() {
        // Set news1 to ALL_PARTNERS
        fedRepo.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER);
        var ids = fedRepo.findSharedNewsIds(partnerId, station.id());
        assertTrue(ids.contains(news1.id()));
    }

    @Test
    @Order(21)
    void findSharedNewsIdsSpecificWithTarget() {
        // Set news2 to SPECIFIC with partnerId
        var share2 = fedRepo.setShare(news2.id(), ShareScope.SPECIFIC, NewsVisibilityRole.MEMBER);
        fedRepo.setShareTargets(share2.id(), List.of(partnerId));

        var ids = fedRepo.findSharedNewsIds(partnerId, station.id());
        assertTrue(ids.contains(news2.id()));
    }

    @Test
    @Order(22)
    void findSharedNewsIdsSpecificWithoutTarget() {
        // news2 is SPECIFIC for partnerId only; partnerIdB should NOT see it
        var ids = fedRepo.findSharedNewsIds(partnerIdB, station.id());
        // partnerIdB should see news1 (ALL_PARTNERS) but NOT news2 (SPECIFIC, targeted only at partnerId)
        assertTrue(ids.contains(news1.id()));
        assertFalse(ids.contains(news2.id()));
    }

    @Test
    @Order(23)
    void findSharedNewsIdsNoSharesConfigured() {
        // news3 has no share configured at all
        var ids = fedRepo.findSharedNewsIds(partnerId, station.id());
        assertFalse(ids.contains(news3.id()));
    }

    @Test
    @Order(24)
    void findSharedNewsIdsWrongStation() {
        var ids = fedRepo.findSharedNewsIds(partnerId, 99999);
        assertTrue(ids.isEmpty());
    }

    // -- findVisibilityRole --

    @Test
    @Order(30)
    void findVisibilityRole() {
        var role = fedRepo.findVisibilityRole(news1.id());
        assertTrue(role.isPresent());
        assertEquals(NewsVisibilityRole.MEMBER, role.get());
    }

    @Test
    @Order(31)
    void findVisibilityRoleMissing() {
        assertTrue(fedRepo.findVisibilityRole(99999).isEmpty());
    }

    // -- removeShare --

    @Test
    @Order(40)
    void removeShare() {
        // Remove news2 share
        fedRepo.removeShare(news2.id());
        assertTrue(fedRepo.findShareByNews(news2.id()).isEmpty());
    }

    @Test
    @Order(41)
    void removeShareCascadesTargets() {
        // Create a new share with targets, then remove it
        var share = fedRepo.setShare(news3.id(), ShareScope.SPECIFIC, NewsVisibilityRole.MEMBER);
        fedRepo.setShareTargets(share.id(), List.of(partnerId));
        fedRepo.removeShare(news3.id());

        assertTrue(fedRepo.findShareByNews(news3.id()).isEmpty());
        assertTrue(fedRepo.findShareTargets(share.id()).isEmpty());
    }

    @Test
    @Order(42)
    void removeShareNonExistent() {
        // Should not throw
        assertDoesNotThrow(() -> fedRepo.removeShare(99999));
    }

    // -- Entity record construction --

    @Test
    @Order(60)
    void newsFederationShareRecord() {
        var share = new NewsFederationShare(1, 2, ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER);
        assertEquals(1, share.id());
        assertEquals(2, share.newsId());
        assertEquals(ShareScope.ALL_PARTNERS, share.scope());
        assertEquals(NewsVisibilityRole.MEMBER, share.visibilityRole());
    }
}

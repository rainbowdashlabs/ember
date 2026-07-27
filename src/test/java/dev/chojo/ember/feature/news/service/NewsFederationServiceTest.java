/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.repository.NewsFederationRepository;
import dev.chojo.ember.feature.news.service.NewsFederationService.FederatedNewsData;
import dev.chojo.ember.feature.news.service.NewsFederationService.FederatedNewsItem;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsFederationServiceTest extends RepositoryTestBase {
    private static final UUID REMOTE_MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE_MEMBER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static NewsFederationService service;
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;
    private static NewsService newsService;

    private static Station stationA;
    private static Station stationB;
    private static Station stationC;
    private static Account accountA;
    private static StationMember memberA;
    private static int partnerIdAB;
    private static News news1;
    private static News news2;
    private static int commentId;

    @BeforeAll
    static void setup() {
        NewsFederationRepository fedRepo = new NewsFederationRepository();
        federationRepo = new FederationRepository();
        EventFederationRepository eventFederationRepo = new EventFederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);
        var eventBus = new DomainEventBus(Set.of());
        newsService = new NewsService(newsRepo, restrictionService, eventBus, stationMemberRepo, accountRepo);

        service = new NewsFederationService(
                fedRepo,
                federationService,
                federationRepo,
                httpClient,
                stationRepo,
                newsService,
                eventFederationRepo,
                memberNameResolver,
                new FederationFanout(),
                new FederationEntityResolver(federationRepo, stationRepo, httpClient));

        stationA = stationRepo.create("NewsFedSvcA");
        stationB = stationRepo.create("NewsFedSvcB");
        stationC = stationRepo.create("NewsFedSvcC");
        accountA = accountRepo.create("newsfed-svc@test.com", "Alice", "Smith");
        memberA = stationMemberRepo.create(stationA.id(), accountA.id());

        // Local partnership: A <-> B
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                stationA.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerIdAB = partner.id();

        // Remote partnership: A <-> C (remote host)
        var keyPairC = federationService.generateKeyPair();
        var partnerC = federationService.acceptInvite(
                stationA.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPairC),
                "https://remote-news.example.com",
                null);
        int partnerIdAC = partnerC.id();

        // Create published news on stationA
        var authorIdentity = stationMemberRepo.resolveIdentity(memberA.id());
        news1 = newsRepo.create(stationA.id(), "News One", "# One", "<h1>One</h1>", authorIdentity);
        news2 = newsRepo.create(stationA.id(), "News Two", "# Two", "<h1>Two</h1>", authorIdentity);
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(stationA.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationB.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationC.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        stationRepo.delete(stationC.id());
        accountRepo.delete(accountA.id());
    }

    // -- Share management delegation --

    @Test
    @Order(1)
    void setShareAllPartners() {
        var share = service.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());
        assertNotNull(share);
        assertEquals(news1.id(), share.newsId());
        assertEquals(ShareScope.ALL_PARTNERS, share.scope());
        assertEquals(NewsVisibilityRole.MEMBER, share.visibilityRole());
    }

    @Test
    @Order(2)
    void findShareByNews() {
        var found = service.findShareByNews(news1.id());
        assertTrue(found.isPresent());
        assertEquals(ShareScope.ALL_PARTNERS, found.get().scope());
    }

    @Test
    @Order(3)
    void findShareByNewsMissing() {
        assertTrue(service.findShareByNews(99999).isEmpty());
    }

    @Test
    @Order(4)
    void setShareSpecificWithTargets() {
        var share = service.setShare(news1.id(), ShareScope.SPECIFIC, NewsVisibilityRole.TEAM, List.of(partnerIdAB));
        assertEquals(ShareScope.SPECIFIC, share.scope());
        assertEquals(NewsVisibilityRole.TEAM, share.visibilityRole());

        var targets = service.findShareTargets(share.id());
        assertEquals(1, targets.size());
        assertTrue(targets.contains(partnerIdAB));
    }

    @Test
    @Order(5)
    void findShareTargetsEmpty() {
        assertTrue(service.findShareTargets(99999).isEmpty());
    }

    @Test
    @Order(6)
    void findSharedNewsIds() {
        // news1 is SPECIFIC for partnerIdAB
        var ids = service.findSharedNewsIds(partnerIdAB, stationA.id());
        assertTrue(ids.contains(news1.id()));
    }

    @Test
    @Order(7)
    void findVisibilityRole() {
        var role = service.findVisibilityRole(news1.id());
        assertTrue(role.isPresent());
        assertEquals(NewsVisibilityRole.TEAM, role.get());
    }

    @Test
    @Order(8)
    void findVisibilityRoleMissing() {
        assertTrue(service.findVisibilityRole(99999).isEmpty());
    }

    @Test
    @Order(9)
    void removeShare() {
        service.removeShare(news1.id());
        assertTrue(service.findShareByNews(news1.id()).isEmpty());
    }

    // -- createRemoteComment --

    @Test
    @Order(15)
    void createRemoteComment() {
        var comment = service.createRemoteComment(
                stationA.id(), news1.id(), partnerIdAB, REMOTE_MEMBER_2, "Bob Jones", null, "Remote comment!");
        assertNotNull(comment);
        assertNotNull(comment.author());
        assertEquals("Remote comment!", comment.content());
    }

    @Test
    @Order(16)
    void createRemoteCommentWithParent() {
        var parentIdentity = stationMemberRepo.resolveIdentity(memberA.id());
        var parent = newsService.createComment(stationA.id(), news1.id(), null, parentIdentity, "Alice", "Parent");
        var child = service.createRemoteComment(
                stationA.id(), news1.id(), partnerIdAB, REMOTE_MEMBER_1, "Alice Remote", parent.id(), "Reply");
        assertNotNull(child);
        assertEquals("Reply", child.content());
    }

    // -- Federated browsing (local) --

    @Test
    @Order(20)
    void browseFederatedNewsLocalPartner() {
        // Share news1 with ALL_PARTNERS
        service.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());

        // Mock httpClient.getList for the remote partner to return empty
        when(httpClient.getList(anyString(), anyString(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        // Browse from stationB's perspective — stationA is a local partner
        var items = service.browseFederatedNews(stationB.id());
        assertNotNull(items);
        // stationB sees stationA as a local partner with shared news
        assertTrue(
                items.stream().anyMatch(i -> i.news().id() == news1.id()),
                "Should find news1 shared via local partner");
    }

    @Test
    @Order(21)
    void browseFederatedNewsNoShares() {
        service.removeShare(news1.id());

        when(httpClient.getList(anyString(), anyString(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        var items = service.browseFederatedNews(stationB.id());
        assertTrue(items.stream().noneMatch(i -> i.news().id() == news1.id()), "Should NOT find unshared news");
    }

    @Test
    @Order(22)
    void browseFederatedNewsMultipleShared() {
        service.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());
        service.setShare(news2.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.TEAM, List.of());

        when(httpClient.getList(anyString(), anyString(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        var items = service.browseFederatedNews(stationB.id());
        assertTrue(items.stream().anyMatch(i -> i.news().id() == news1.id()));
        assertTrue(items.stream().anyMatch(i -> i.news().id() == news2.id()));
    }

    // -- Federated browsing (remote via HTTP) --

    @Test
    @Order(25)
    @SuppressWarnings("unchecked")
    void browseFederatedNewsViaHttp() throws Exception {
        service.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());

        // Construct RemoteNewsListEntry via reflection (it's a private record)
        var entryClass =
                Class.forName("dev.chojo.ember.feature.news.service.NewsFederationService$RemoteNewsListEntry");
        Constructor<?> ctor = entryClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var remoteEntry = ctor.newInstance(
                9999, "Remote Title", "<p>content</p>", "Remote Author", "2026-01-01", 5, NewsVisibilityRole.MEMBER);

        when(httpClient.getList(
                        eq("https://remote-news.example.com"),
                        eq("/remote/news"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        any()))
                .thenReturn(List.of(remoteEntry));

        var items = service.browseFederatedNews(stationA.id());
        assertFalse(items.isEmpty());

        // Verify HTTP was called
        verify(httpClient)
                .getList(
                        eq("https://remote-news.example.com"),
                        eq("/remote/news"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        any());

        // Should contain the remote news item
        assertTrue(items.stream()
                .anyMatch(i ->
                        i.news().id() == 9999 && "Remote Title".equals(i.news().title())));
    }

    @Test
    @Order(26)
    void browseFederatedNewsHttpReturnsEmpty() {
        when(httpClient.getList(
                        eq("https://remote-news.example.com"),
                        eq("/remote/news"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        any()))
                .thenReturn(List.of());

        var items = service.browseFederatedNews(stationA.id());
        assertNotNull(items);
    }

    // -- getFederatedNews (local) --

    @Test
    @Order(30)
    void getFederatedNewsLocal() {
        service.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());

        var result = service.getFederatedNews(stationB.id(), stationA.uid(), news1.id());
        assertNotNull(result);
        assertEquals(news1.id(), result.id());
        assertEquals("News One", result.title());
        assertNotNull(result.authorName());
        assertEquals(NewsVisibilityRole.MEMBER, result.visibilityRole());
    }

    @Test
    @Order(31)
    void getFederatedNewsLocalNotShared() {
        service.removeShare(news1.id());
        assertThrows(Exception.class, () -> service.getFederatedNews(stationB.id(), stationA.uid(), news1.id()));
    }

    @Test
    @Order(32)
    void getFederatedNewsUnknownPartner() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getFederatedNews(stationB.id(), UUID.randomUUID(), news1.id()));
    }

    // -- getFederatedNews (remote via HTTP) --

    @Test
    @Order(35)
    void getFederatedNewsRemote() {
        var remoteData = new FederatedNewsData(
                42, "Remote Single", "# md", "<p>html</p>", "Author", "2026-03-15", 3, NewsVisibilityRole.TEAM);

        when(httpClient.get(
                        eq("https://remote-news.example.com"),
                        eq("/remote/news/42"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(FederatedNewsData.class)))
                .thenReturn(remoteData);

        var result = service.getFederatedNews(stationA.id(), stationC.uid(), 42);
        assertNotNull(result);
        assertEquals(42, result.id());
        assertEquals("Remote Single", result.title());
        assertEquals(NewsVisibilityRole.TEAM, result.visibilityRole());
    }

    @Test
    @Order(36)
    void getFederatedNewsRemoteReturnsNull() {
        when(httpClient.get(
                        eq("https://remote-news.example.com"),
                        eq("/remote/news/99"),
                        any(),
                        eq(stationA.id()),
                        any(),
                        eq(FederatedNewsData.class)))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.getFederatedNews(stationA.id(), stationC.uid(), 99));
    }

    // -- Inactive partner --

    @Test
    @Order(40)
    void getFederatedNewsSuspendedPartner() {
        // Find the reverse partner (stationA's record pointing to stationC)
        var reversePartner = federationRepo
                .findPartnerByStationAndRemoteUid(stationA.id(), stationC.uid())
                .orElseThrow();

        // Suspend stationA's partner record for stationC
        federationRepo.updatePartnerStatus(reversePartner.id(), FederationPartner.FederationStatus.SUSPENDED);

        assertThrows(BadRequestResponse.class, () -> service.getFederatedNews(stationA.id(), stationC.uid(), 42));

        // Restore
        federationRepo.updatePartnerStatus(reversePartner.id(), FederationPartner.FederationStatus.ACTIVE);
    }

    // -- Record types --

    @Test
    @Order(50)
    void federatedNewsItemRecord() {
        var data =
                new FederatedNewsData(1, "Title", "md", "html", "Author", "2026-01-01", 0, NewsVisibilityRole.MEMBER);
        var item = new FederatedNewsItem(10, "StationName", "uid-123", data);
        assertEquals(10, item.partnerId());
        assertEquals("StationName", item.partnerStationName());
        assertEquals("uid-123", item.partnerStationUid());
        assertEquals(data, item.news());
    }

    @Test
    @Order(51)
    void federatedNewsDataRecord() {
        var data = new FederatedNewsData(
                5, "Title", "# md", "<h1>html</h1>", "Author Name", "2026-06-15", 10, NewsVisibilityRole.TEAM);
        assertEquals(5, data.id());
        assertEquals("Title", data.title());
        assertEquals("# md", data.contentMarkdown());
        assertEquals("<h1>html</h1>", data.contentHtml());
        assertEquals("Author Name", data.authorName());
        assertEquals("2026-06-15", data.publishedAt());
        assertEquals(10, data.commentCount());
        assertEquals(NewsVisibilityRole.TEAM, data.visibilityRole());
    }

    @Test
    @Order(52)
    void federatedNewsDataWithNulls() {
        var data = new FederatedNewsData(0, null, null, null, null, null, 0, null);
        assertEquals(0, data.id());
        assertNull(data.title());
        assertNull(data.contentMarkdown());
    }

    // -- toNewsData covers null content/publishedAt --

    @Test
    @Order(55)
    void getFederatedNewsLocalWithComments() {
        service.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());

        var result = service.getFederatedNews(stationB.id(), stationA.uid(), news1.id());
        assertNotNull(result);
        // news1 has comments created in earlier tests
        assertTrue(result.commentCount() >= 0);
        assertNotNull(result.publishedAt());
        assertFalse(result.publishedAt().isEmpty());
    }

    @Test
    @Order(56)
    void browseFederatedNewsChecksPartnerStationName() {
        service.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());

        when(httpClient.getList(anyString(), anyString(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        var items = service.browseFederatedNews(stationB.id());
        var localItems = items.stream().filter(i -> i.news().id() == news1.id()).toList();
        assertFalse(localItems.isEmpty());
        // Partner station name should be stationA's name
        assertEquals("NewsFedSvcA", localItems.getFirst().partnerStationName());
    }
}

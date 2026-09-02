/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.knowledgebase.service.KbLinkMetadataService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Where a cluster's content lives, and who signs it.
 */
class ClusterContentServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterContentService service;

    @BeforeAll
    static void setup() {
        var storage = new Storage();
        var fileStorage = mock(KbFileStorageService.class);
        var searchService = new KbSearchService(knowledgeBaseRepo, stationRepo);
        var contentService = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                fileStorage,
                searchService);
        var accessService = new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo);
        var kbService = new KnowledgeBaseService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                accessService,
                new KbPresentationService(knowledgeBaseRepo, fileStorage, contentService),
                new KbLinkMetadataService(new RemoteUrlValidator(new Federation(), new Demo())),
                new PresentationCompressor(storage),
                new PdfCompressor(storage),
                new ClusterAutoShareService(clusterRepo, new FederationRepository()));
        var trashService = new KbTrashService(
                knowledgeBaseRepo,
                fileStorage,
                contentService,
                searchService,
                accessService,
                new KbAuthorNameService(stationMemberRepo, accountRepo),
                pageRepo);
        service = new ClusterContentService(clusterRepo, stationRepo, stationMemberRepo, kbService, trashService);
    }

    @Test
    void contentLivesOnTheClustersOwnStation() {
        var cluster = clusterService.create("Kreisverband Inhalt " + NAMES.incrementAndGet(), null);

        assertEquals(cluster.homeStationId(), service.homeStationOf(cluster.id()));
    }

    @Test
    void aWriterGetsABylineOnFirstUseAndKeepsIt() {
        var cluster = clusterService.create("Kreisverband Feder " + NAMES.incrementAndGet(), null);
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clustercontent" + n + "@test.com", "Schrei", "Ber" + n);

        var first = service.authorFor(cluster.id(), account.id());
        var second = service.authorFor(cluster.id(), account.id());

        assertEquals(first.id(), second.id(), "a byline is made once, not once per article");
        assertEquals(cluster.homeStationId(), first.stationId());

        var identity = service.authorIdentity(cluster.id(), account.id());
        assertNotNull(identity.memberUid());
        assertEquals(stationRepo.resolveUid(cluster.homeStationId()), identity.stationUid());
    }

    @Test
    void thatBylineIsNotAMembershipAnywhereElse() {
        var cluster = clusterService.create("Kreisverband Trennung " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Trennung " + NAMES.incrementAndGet());
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clusterbyline" + n + "@test.com", "Nur", "Feder" + n);

        service.authorFor(cluster.id(), account.id());

        assertTrue(
                stationMemberRepo
                        .findByStationAndAccount(station.id(), account.id())
                        .isEmpty(),
                "writing for the cluster joins none of its stations");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aClusterThatIsNotThereHasNowhereToPutContent() {
        assertThrows(NotFoundResponse.class, () -> service.homeStationOf(999_999));
    }

    @Test
    void aKnowledgeFolderIsSharedWithTheWholeClusterAsItIsMade() {
        var cluster = clusterService.create("Kreisverband Wissen " + NAMES.incrementAndGet(), null);
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clusterkb" + n + "@test.com", "Wis", "Sen" + n);

        var folder = service.createFolder(cluster.id(), null, "Dienstanweisungen", "Für alle Wachen", account.id());

        assertEquals(1, service.findFolders(cluster.id()).size());
        assertTrue(
                new FederationRepository()
                        .findKbShares(cluster.homeStationId()).stream()
                                .anyMatch(share -> share.folderId() != null && share.folderId() == folder.id()),
                "a cluster's knowledge is the cluster's knowledge, so it is shared as it is made");
    }

    @Test
    void anArticleIsSharedTooAndCanBeTakenBack() {
        var cluster = clusterService.create("Kreisverband Artikel " + NAMES.incrementAndGet(), null);
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clusterarticle" + n + "@test.com", "Art", "Ikel" + n);

        var file = service.createArticle(cluster.id(), null, "Ablauf", "Kurz", "# Ablauf", account.id());

        assertEquals(1, service.findFiles(cluster.id(), null).size());
        assertTrue(new FederationRepository()
                .findKbShares(cluster.homeStationId()).stream()
                        .anyMatch(share -> share.fileId() != null && share.fileId() == file.id()));

        service.deleteArticle(cluster.id(), file.id());
        assertTrue(service.findFiles(cluster.id(), null).isEmpty());
    }

    @Test
    void oneClusterCannotRemoveAnothersArticle() {
        var cluster = clusterService.create("Kreisverband Eigen " + NAMES.incrementAndGet(), null);
        var other = clusterService.create("Kreisverband Fremd " + NAMES.incrementAndGet(), null);
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clusterforeign" + n + "@test.com", "Fre", "Md" + n);
        var file = service.createArticle(other.id(), null, "Fremd", null, "x", account.id());

        assertThrows(NotFoundResponse.class, () -> service.deleteArticle(cluster.id(), file.id()));
    }

    @Test
    void aFolderAndAnArticleBothNeedAName() {
        var cluster = clusterService.create("Kreisverband Namenlos " + NAMES.incrementAndGet(), null);
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clusternameless" + n + "@test.com", "Na", "Me" + n);

        assertThrows(BadRequestResponse.class, () -> service.createFolder(cluster.id(), null, " ", null, account.id()));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createArticle(cluster.id(), null, " ", null, "x", account.id()));
    }
}

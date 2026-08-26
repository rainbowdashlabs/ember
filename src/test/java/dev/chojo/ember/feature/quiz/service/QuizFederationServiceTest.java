/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.FederationTestContracts;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizFederationServiceTest extends RepositoryTestBase {
    private static QuizFederationService service;
    private static QuizCatalogService catalogService;
    private static QuizQuestionService questionService;
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;
    private static Station station;
    private static Station localPartner;
    private static Station remotePartner;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);
        questionService = new QuizQuestionService(quizCatalogRepo);
        catalogService = new QuizCatalogService(quizCatalogRepo);
        service = new QuizFederationService(
                catalogService,
                questionService,
                federationService,
                federationRepo,
                httpClient,
                stationRepo,
                new FederationFanout(),
                new FederationEntityResolver(federationRepo, stationRepo, httpClient));

        station = stationRepo.create("QuizFedStation");
        localPartner = stationRepo.create("QuizFedStationLocal");
        remotePartner = stationRepo.create("QuizFedStationRemote");

        federationService.acceptInvite(
                station.id(),
                localPartner.id(),
                federationService.encodePublicKey(federationService.generateKeyPair()),
                null,
                null);
        federationService.acceptInvite(
                station.id(),
                remotePartner.id(),
                federationService.encodePublicKey(federationService.generateKeyPair()),
                "https://remote-quiz.example.com",
                null);
        FederationTestContracts.storeCurrentContractOnRemotePartners(federationService, federationRepo, station.id());
    }

    @AfterAll
    static void cleanup() {
        for (var s : List.of(station, localPartner, remotePartner)) {
            for (var partner : federationService.findPartners(s.id())) federationRepo.deletePartner(partner.id());
            stationRepo.delete(s.id());
        }
    }

    @Test
    @Order(1)
    void browseSharedQuizWithoutSharesIsEmpty() {
        assertTrue(service.browseSharedQuiz(station.id()).isEmpty());
        assertTrue(service.browseSharedCatalogs(station.id()).isEmpty());
    }

    @Test
    @Order(2)
    void browseSharedQuizListsPartnerCatalogs() {
        var catalog = quizCatalogRepo.create(
                localPartner.id(), "FedCatalog", "Federated catalog", false, CatalogMetadata.none());
        var share = federationRepo.createQuizShare(localPartner.id(), catalog.id(), ShareScope.ALL_PARTNERS);

        assertTrue(service.browseSharedQuiz(station.id()).stream().anyMatch(s -> s.id() == catalog.id()));

        federationRepo.deleteQuizShare(share.id(), localPartner.id());
        quizCatalogRepo.delete(catalog.id());
    }

    @Test
    @Order(3)
    void browseSharedCatalogsResolvesStationName() {
        var catalog = quizCatalogRepo.create(
                localPartner.id(), "FedNamed", "Federated catalog", false, CatalogMetadata.none());
        var share = federationRepo.createQuizShare(localPartner.id(), catalog.id(), ShareScope.ALL_PARTNERS);

        var entry = service.browseSharedCatalogs(station.id()).stream()
                .filter(s -> s.id() == catalog.id())
                .findFirst();
        assertTrue(entry.isPresent());
        assertEquals(localPartner.name(), entry.get().stationName());
        assertEquals(localPartner.uid().toString(), entry.get().stationUid());

        federationRepo.deleteQuizShare(share.id(), localPartner.id());
        quizCatalogRepo.delete(catalog.id());
    }

    @Test
    @Order(10)
    void getFederatedQuizCatalogFromLocalPartner() {
        var catalog = quizCatalogRepo.create(
                localPartner.id(), "FedDetail", "Detailed catalog", false, CatalogMetadata.none());
        var question = quizCatalogRepo.createQuestion(
                catalog.id(),
                null,
                QuizQuestionType.TRUE_FALSE,
                "FedQ1",
                "desc",
                null,
                2.0,
                false,
                "{\"correctAnswer\":true}",
                0);

        var result = service.getFederatedQuizCatalog(station.id(), localPartner.uid(), catalog.id());
        assertNotNull(result.catalog());
        assertNotNull(result.categories());
        assertEquals(1, result.questions().size());

        quizCatalogRepo.deleteQuestion(question.id());
        quizCatalogRepo.delete(catalog.id());
    }

    @Test
    @Order(11)
    void getFederatedQuizCatalogRejectsForeignCatalog() {
        var catalog =
                quizCatalogRepo.create(station.id(), "LocalOnly", "Not on the partner", false, CatalogMetadata.none());
        assertThrows(
                Exception.class, () -> service.getFederatedQuizCatalog(station.id(), localPartner.uid(), catalog.id()));
        quizCatalogRepo.delete(catalog.id());
    }

    @Test
    @Order(20)
    void copyQuizCatalogClonesCategoriesAndQuestions() {
        var source =
                quizCatalogRepo.create(localPartner.id(), "CopySrc", "Source for copy", true, CatalogMetadata.none());
        var category = quizCatalogRepo.createCategory(localPartner.id(), "CopyCat", "Category to copy", 0);
        var question = quizCatalogRepo.createQuestion(
                source.id(),
                category.id(),
                QuizQuestionType.TRUE_FALSE,
                "CopyQ1",
                "desc",
                null,
                3.0,
                false,
                "{\"correctAnswer\":true}",
                0);

        var copied = service.copyQuizCatalog(source.id(), station.id());
        assertEquals("CopySrc", copied.name());
        assertNotEquals(source.id(), copied.id());

        var copiedQuestions = questionService.findQuestions(copied.id());
        assertEquals(1, copiedQuestions.size());
        assertEquals("CopyQ1", copiedQuestions.getFirst().title());

        var targetCategories = catalogService.findCategories(station.id());
        var copiedCategory = targetCategories.stream()
                .filter(c -> "CopyCat".equals(c.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("category was not copied into the target station"));
        assertNotEquals(category.id(), copiedCategory.id());
        assertEquals(
                copiedCategory.id(),
                copiedQuestions.getFirst().categoryId(),
                "the copied question must point at the copied category, not the source station's");

        for (var q : copiedQuestions) quizCatalogRepo.deleteQuestion(q.id());
        quizCatalogRepo.deleteCategory(copiedCategory.id());
        quizCatalogRepo.delete(copied.id());
        quizCatalogRepo.deleteQuestion(question.id());
        quizCatalogRepo.deleteCategory(category.id());
        quizCatalogRepo.delete(source.id());
    }

    /**
     * A category the copied questions never reference must not be dragged into the target station,
     * otherwise importing one catalog imports the source station's whole vocabulary.
     */
    @Test
    @Order(21)
    void copyQuizCatalogLeavesUnreferencedCategoriesBehind() {
        var source = quizCatalogRepo.create(
                localPartner.id(), "CopySrc2", "Source without categories", false, CatalogMetadata.none());
        var unused = quizCatalogRepo.createCategory(localPartner.id(), "UnusedCat", "Never referenced", 0);

        var copied = service.copyQuizCatalog(source.id(), station.id());

        assertTrue(catalogService.findCategories(station.id()).stream().noneMatch(c -> "UnusedCat".equals(c.name())));

        quizCatalogRepo.delete(copied.id());
        quizCatalogRepo.deleteCategory(unused.id());
        quizCatalogRepo.delete(source.id());
    }

    @Test
    @Order(30)
    void browseSharedQuizFetchesRemotePartnerCatalogs() {
        when(httpClient.getList(
                        eq("https://remote-quiz.example.com"),
                        pathIs("/remote/quiz/catalogs"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(QuizFederationService.RemoteQuizCatalog.class)))
                .thenReturn(List.of(new QuizFederationService.RemoteQuizCatalog(99, "RemoteCatalog", "remote desc")));

        var items = service.browseSharedQuiz(station.id());
        assertTrue(items.stream().anyMatch(i -> i.name().equals("RemoteCatalog")));
        assertEquals(
                "remote desc",
                items.stream()
                        .filter(i -> i.id() == 99)
                        .findFirst()
                        .orElseThrow()
                        .description());
    }

    @Test
    @Order(31)
    void getFederatedQuizCatalogFromRemotePartner() {
        var remoteResult = new QuizFederationService.FederatedCatalogDetail(
                new QuizCatalog(88, 0, "RemoteCatalog", "desc", false, false, CatalogMetadata.none(), null, null),
                List.of(),
                List.of());
        when(httpClient.get(
                        eq("https://remote-quiz.example.com"),
                        pathIs("/remote/quiz/catalogs/88"),
                        any(),
                        eq(station.id()),
                        any(),
                        any()))
                .thenReturn(remoteResult);

        var result = service.getFederatedQuizCatalog(station.id(), remotePartner.uid(), 88);
        assertEquals("RemoteCatalog", result.catalog().name());
    }

    @Test
    @Order(40)
    void sharedQuizItemCarriesItsParts() {
        var item = new QuizFederationService.SharedQuizItem(42, "Test Catalog", "A description", 7, 3);
        assertEquals(42, item.id());
        assertEquals("Test Catalog", item.name());
        assertEquals("A description", item.description());
        assertEquals(7, item.sourceStationId());
        assertEquals(3, item.partnerId());
    }

    @Test
    @Order(41)
    void fetchSharedQuizCatalogsDelegatesToTheHttpClient() {
        when(httpClient.getList(
                        eq("https://elsewhere.example.com"),
                        pathIs("/remote/quiz/catalogs"),
                        any(),
                        eq(station.id()),
                        any(),
                        eq(QuizFederationService.RemoteQuizCatalog.class)))
                .thenReturn(List.of(new QuizFederationService.RemoteQuizCatalog(7, "Elsewhere", "desc")));

        var catalogs = service.fetchSharedQuizCatalogs(
                "https://elsewhere.example.com", remotePartner.uid(), station.id(), "key");
        assertEquals(1, catalogs.size());
        assertEquals("Elsewhere", catalogs.getFirst().name());
    }
}

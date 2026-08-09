/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.route.RemoteQuizRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The catalogs federation partners share with a station, resolved the same way whether the
 * partner lives on this instance or on another one, plus the copy that turns a partner
 * catalog into an own one.
 */
@Singleton
public class QuizFederationService {
    private static final Logger log = LoggerFactory.getLogger(QuizFederationService.class);

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final StationRepository stationRepository;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;

    @Inject
    public QuizFederationService(
            QuizCatalogService catalogService,
            QuizQuestionService questionService,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository,
            FederationFanout fanout,
            FederationEntityResolver entityResolver) {
        this.catalogService = catalogService;
        this.questionService = questionService;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
    }

    public List<SharedQuizItem> browseSharedQuiz(int stationId) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(p -> federationService.hasCapability(p, CapabilityType.QUIZ_SHARE, Direction.IMPORT))
                .toList();
        return fanout.fanOut(
                partners,
                partner -> browseSharedQuizDirect(resolvePartnerStationId(partner), partner),
                partner -> browseSharedQuizViaHttp(stationId, partner, resolvePartnerStationId(partner)));
    }

    /**
     * Lists the catalogs federated partners share with this station, each resolved to the
     * display name of the station that owns it.
     */
    public List<SharedQuizCatalog> browseSharedCatalogs(int stationId) {
        return browseSharedQuiz(stationId).stream()
                .map(item -> new SharedQuizCatalog(
                        item.id(),
                        item.name(),
                        item.description(),
                        stationRepository
                                .findById(item.sourceStationId())
                                .map(Station::name)
                                .orElse("Unknown"),
                        item.sourceStationId()))
                .toList();
    }

    public FederatedCatalogDetail getFederatedQuizCatalog(int localStationId, UUID partnerStationUid, int catalogId) {
        return entityResolver.resolve(
                localStationId,
                partnerStationUid,
                RemoteQuizRoutes.GET_CATALOG.at(catalogId),
                FederatedCatalogDetail.class,
                "catalog",
                partner -> {
                    var catalog = catalogService.findCatalog(catalogId).orElseThrow();
                    if (catalog.stationId() != resolvePartnerStationId(partner)) {
                        throw new BadRequestResponse("Catalog does not belong to this partner");
                    }
                    var categories = catalogService.findCategories(catalog.stationId());
                    var questions = questionService.findQuestions(catalog.id());
                    return new FederatedCatalogDetail(catalog, categories, questions);
                });
    }

    /**
     * Copies a catalog with its categories and questions into another station.
     *
     * <p>Categories belong to a station, not to a catalog, so they are read from the source
     * station and recreated in the target one. Only the categories the copied questions actually
     * reference are brought across, to avoid importing the source station's whole vocabulary.
     */
    public QuizCatalog copyQuizCatalog(int catalogId, int targetStationId) {
        var source = catalogService.findCatalog(catalogId).orElseThrow();
        var newCatalog = catalogService.createCatalog(
                targetStationId, source.name(), source.description(), source.trainingEnabled());

        var questions = questionService.findQuestions(source.id());
        var referenced = questions.stream()
                .map(QuizQuestion::categoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        var categoryMap = new HashMap<Integer, Integer>();
        for (var category : catalogService.findCategories(source.stationId())) {
            if (!referenced.contains(category.id())) continue;
            var copy = catalogService.createCategory(
                    targetStationId, category.name(), category.description(), category.position());
            categoryMap.put(category.id(), copy.id());
        }

        for (var question : questions) {
            Integer newCategoryId = question.categoryId() != null ? categoryMap.get(question.categoryId()) : null;
            questionService.createQuestion(
                    CreateQuestionCommand.builder(newCatalog.id(), question.quizQuestionType(), question.title())
                            .category(newCategoryId)
                            .description(question.description())
                            .imageUrl(question.imageUrl())
                            .points(question.points())
                            .autoPoints(question.autoPoints())
                            .config(question.config())
                            .position(question.position())
                            .build());
        }
        log.info(
                "Copied quiz catalog {} to new catalog {} for station {} ({} questions)",
                catalogId,
                newCatalog.id(),
                targetStationId,
                questions.size());
        return newCatalog;
    }

    public List<RemoteQuizCatalog> fetchSharedQuizCatalogs(
            String remoteHost, UUID partnerStationUid, int localStationId, String localPrivateKeyBase64) {
        return federationHttpClient.getList(
                remoteHost,
                RemoteQuizRoutes.BROWSE_CATALOGS.at(),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64,
                RemoteQuizCatalog.class);
    }

    private List<SharedQuizItem> browseSharedQuizDirect(int remoteStationId, FederationPartner partner) {
        var result = new ArrayList<SharedQuizItem>();
        var shares = federationRepository.findQuizShares(remoteStationId);
        for (var share : shares) {
            if (share.catalogId() != null) {
                catalogService.findCatalog(share.catalogId()).ifPresent(catalog -> {
                    result.add(new SharedQuizItem(
                            catalog.id(), catalog.name(), catalog.description(), remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.QUIZ, catalog.id(), catalog.name(), catalog.description());
                });
            }
        }
        return result;
    }

    private List<SharedQuizItem> browseSharedQuizViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId) {
        var result = new ArrayList<SharedQuizItem>();
        var catalogs = fetchSharedQuizCatalogs(
                partner.remoteHost(), partner.partnerStationId(), localStationId, getPrivateKey(localStationId));
        for (var remoteCatalog : catalogs) {
            result.add(new SharedQuizItem(
                    remoteCatalog.id(),
                    remoteCatalog.name(),
                    remoteCatalog.description(),
                    remoteStationId,
                    partner.id()));
            federationRepository.upsertMetadataCache(
                    partner.id(),
                    ContentType.QUIZ,
                    remoteCatalog.id(),
                    remoteCatalog.name(),
                    remoteCatalog.description());
        }
        return result;
    }

    private String getPrivateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    private int resolvePartnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    public record FederatedCatalogDetail(
            QuizCatalog catalog, List<QuizCategory> categories, List<QuizQuestion> questions) {}

    public record SharedQuizItem(int id, String name, String description, int sourceStationId, int partnerId) {}

    /**
     * A shared catalog as the browsing station sees it, including the owning station's
     * display name.
     */
    public record SharedQuizCatalog(int id, String name, String description, String stationName, int sourceStationId) {}

    public record RemoteQuizCatalog(int id, String name, String description) {}
}

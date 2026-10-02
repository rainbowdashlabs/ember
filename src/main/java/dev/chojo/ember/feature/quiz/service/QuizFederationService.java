/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.refusal.QuizRefusal;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.route.RemoteQuizRoutes;
import dev.chojo.ember.feature.quiz.route.RemoteQuizRoutes.RemoteCatalogDetail;
import dev.chojo.ember.feature.quiz.route.RemoteQuizRoutes.RemoteCatalogSummary;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The catalogs federation partners share with a station, and the catalogs this station shares with
 * them, plus the copy that turns a partner catalog into an own one.
 *
 * <p>Both directions go through one set of serving functions: a partner on another instance reaches
 * them through the {@code /remote} routes, one on this instance through the local transport, so a
 * catalog is shown to a partner on exactly the same terms wherever it lives.
 */
@Singleton
public class QuizFederationService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(QuizFederationService.class);

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;
    private final FederationTransport transport;

    @Inject
    public QuizFederationService(
            QuizCatalogService catalogService,
            QuizQuestionService questionService,
            FederationService federationService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            FederationFanout fanout,
            FederationEntityResolver entityResolver,
            FederationTransport transport) {
        this.catalogService = catalogService;
        this.questionService = questionService;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
        this.transport = transport;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteQuizRoutes.BROWSE_CATALOGS, (partner, params, body) -> serveCatalogs(partner));
        endpoints.serve(
                RemoteQuizRoutes.GET_CATALOG, (partner, params, body) -> serveCatalog(partner, params.integer("id")));
    }

    /**
     * The catalogs this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @return a summary per shared catalog of this station
     */
    public List<RemoteCatalogSummary> serveCatalogs(ServingPartner partner) {
        return federationRepository.findQuizShares(partner.servingStationId()).stream()
                .flatMap(share -> Stream.ofNullable(share.catalogId()))
                .flatMap(catalogId -> catalogService.findCatalog(catalogId).stream())
                .filter(catalog -> catalog.stationId() == partner.servingStationId())
                .map(catalog -> new RemoteCatalogSummary(
                        catalog.id(),
                        catalog.name(),
                        catalog.description(),
                        catalog.updatedAt().toString()))
                .toList();
    }

    /**
     * One catalog this station shares with a partner, with its categories and questions.
     *
     * <p>Being paired with the station that owns a catalog says nothing about being allowed to read
     * it, and catalog ids are sequential, so a catalog that is missing, belongs to another station or
     * is not shared is refused alike: a partner could otherwise count its way through the whole
     * question bank.
     *
     * @param partner   the partnership the request arrived on
     * @param catalogId the catalog asked for
     * @return the catalog
     */
    public RemoteCatalogDetail serveCatalog(ServingPartner partner, int catalogId) {
        var catalog = catalogService
                .findCatalog(catalogId)
                .filter(found -> found.stationId() == partner.servingStationId())
                .filter(found -> isShared(partner, catalogId))
                .orElseThrow(QuizRefusal.REMOTE_QUIZ_CATALOG_NOT_SHARED::raise);
        var categories = catalogService.findCategories(catalog.stationId());
        var questions = questionService.findQuestions(catalog.id());
        return new RemoteCatalogDetail(catalog, categories, questions);
    }

    private boolean isShared(ServingPartner partner, int catalogId) {
        return federationRepository.findQuizShares(partner.servingStationId()).stream()
                .anyMatch(share -> Objects.equals(share.catalogId(), catalogId));
    }

    public List<SharedQuizItem> browseSharedQuiz(int stationId) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(p -> federationService.hasCapability(p, CapabilityType.QUIZ_SHARE, Direction.IMPORT))
                .toList();
        return fanout.fanOut(partners, this::browsePartner).items();
    }

    /**
     * Lists the catalogs federated partners share with this station, each resolved to the
     * display name of the station that owns it.
     */
    public List<SharedQuizCatalog> browseSharedCatalogs(int stationId) {
        return browseSharedQuiz(stationId).stream()
                .map(item -> {
                    var partner = federationRepository
                            .findPartnerById(item.partnerId())
                            .orElse(null);
                    return new SharedQuizCatalog(
                            item.id(),
                            item.name(),
                            item.description(),
                            FederationDisplayNames.partnerName(stationRepository, partner, "Unknown"),
                            partner != null ? partner.partnerStationId().toString() : null);
                })
                .toList();
    }

    public RemoteCatalogDetail getFederatedQuizCatalog(int localStationId, UUID partnerStationUid, int catalogId) {
        var partner = entityResolver.requireActivePartner(localStationId, partnerStationUid);
        return transport.get(partner, RemoteQuizRoutes.GET_CATALOG.at(catalogId), RemoteCatalogDetail.class);
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
                targetStationId, source.name(), source.description(), source.trainingEnabled(), source.metadata());

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

    private List<SharedQuizItem> browsePartner(FederationPartner partner) {
        int sourceStationId = resolvePartnerStationId(partner);
        return transport.getList(partner, RemoteQuizRoutes.BROWSE_CATALOGS.at(), RemoteCatalogSummary.class).stream()
                .map(catalog -> {
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.QUIZ, catalog.id(), catalog.name(), catalog.description());
                    return new SharedQuizItem(
                            catalog.id(), catalog.name(), catalog.description(), sourceStationId, partner.id());
                })
                .toList();
    }

    private int resolvePartnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    public record SharedQuizItem(int id, String name, String description, int sourceStationId, int partnerId) {}

    /**
     * A catalog shared by a partner, including the owning station's display name. The station UUID
     * addresses the serving station on the federated read routes and is null when the partnership
     * behind it can no longer be resolved.
     */
    public record SharedQuizCatalog(
            int id,
            String name,
            String description,
            String stationName,
            @Nullable String stationUid) {}
}

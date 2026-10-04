/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.TemplateStationUse;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * How a station uses the templates of its association.
 *
 * <p>The association keeps a template and may offer it for self service; the station decides whether
 * its own members see it there and who of them. Until a station decides, a template of its association
 * is not offered to its members, whatever the association offers. Generating one for a member, as a
 * manager does, needs no decision: every template of the association in use is there for that.
 *
 * <p>An association has no members, so its template names no issuer; each station names its own here,
 * who then issues the template's documents at that station ({@link DocumentIssuerService}).
 */
@Singleton
public class TemplateStationUseService {
    private static final Logger log = LoggerFactory.getLogger(TemplateStationUseService.class);

    private final DocumentTemplateService templates;
    private final TemplateStationUseRepository uses;
    private final RestrictionService restrictions;
    private final DocumentIssuerService issuers;

    @Inject
    public TemplateStationUseService(
            DocumentTemplateService templates,
            TemplateStationUseRepository uses,
            RestrictionService restrictions,
            DocumentIssuerService issuers) {
        this.templates = templates;
        this.uses = uses;
        this.restrictions = restrictions;
        this.issuers = issuers;
    }

    /**
     * A template of the association as a station sees it, with how the station uses it.
     *
     * @param templateId     the template
     * @param name           what the association calls it
     * @param kind           what it is made of
     * @param legal          whether it makes a legal document
     * @param offered        whether the association offers it for self service
     * @param cooldownDays   the days between two self service documents for one member, which the
     *                       association sets
     * @param selfService    whether the station offers it to its members for self service
     * @param audience       who of the station's members may generate it for themselves
     * @param issuerId       the member of the station who issues its documents there, or null for nobody
     * @param issuerFunction what the issuer does at the station, or null where nothing is said
     */
    public record TemplateUseResponse(
            int templateId,
            String name,
            DocumentTemplateKind kind,
            boolean legal,
            boolean offered,
            int cooldownDays,
            boolean selfService,
            RestrictionAudience audience,
            @Nullable Integer issuerId,
            @Nullable String issuerFunction) {}

    /**
     * How a station wants to use a template of its association.
     *
     * @param selfService    whether its members generate it for themselves, where the association offers it
     * @param audience       who of its members may, everybody where left out
     * @param issuerId       the member of the station who issues its documents there, or null for nobody
     * @param issuerFunction what the issuer does at the station, or null where nothing is said
     */
    public record TemplateUseRequest(
            boolean selfService,
            @Nullable RestrictionAudience audience,
            @Nullable Integer issuerId,
            @Nullable String issuerFunction) {}

    /**
     * How a station uses a template of its association.
     *
     * @param stationId  the station
     * @param templateId the template
     * @return the template with the station's choice
     */
    public TemplateUseResponse useOf(int stationId, int templateId) {
        var template = requireOfAssociation(stationId, templateId);
        return response(template, uses.find(templateId, stationId).orElse(null));
    }

    /**
     * Changes how a station uses a template of its association.
     *
     * @param stationId  the station
     * @param templateId the template
     * @param request    what the station chose
     * @return the template with the station's choice as written
     */
    public TemplateUseResponse setUse(int stationId, int templateId, TemplateUseRequest request) {
        var template = requireOfAssociation(stationId, templateId);
        var audience = Objects.requireNonNullElse(request.audience(), RestrictionAudience.empty());
        var kept = uses.find(templateId, stationId)
                .map(TemplateStationUse::issuerId)
                .orElse(null);
        var issuer = issuers.checked(new Owner.Station(stationId), request.issuerId(), request.issuerFunction(), kept);
        var written = Transactions.call(() -> {
            var use = uses.write(templateId, stationId, request.selfService(), audience.mode(), issuer);
            restrictions.setRestrictions(
                    RestrictionType.DOCUMENT_TEMPLATE_STATION_USE, use.id(), audience.toSelection());
            return use;
        });
        log.info(
                "Station {} {} the association's document template {} for self service",
                stationId,
                request.selfService() ? "offers" : "does not offer",
                templateId);
        return response(template, written);
    }

    /**
     * Whether a station offers a template of its association to one of its members for self service:
     * the association offers it, the station switched it on, and its audience takes the member in.
     *
     * @param template  a template of the station's association
     * @param stationId the station
     * @param member    the member the document would be about, as restrictions see them, or null where
     *                  there is no such member
     * @return whether the member is offered it
     */
    public boolean offers(DocumentTemplate template, int stationId, @Nullable RestrictionMember member) {
        if (!template.selfService()) return false;
        return uses.find(template.id(), stationId)
                .filter(TemplateStationUse::selfService)
                .map(use -> restrictions.includes(RestrictionType.DOCUMENT_TEMPLATE_STATION_USE, use.id(), member))
                .orElse(false);
    }

    private DocumentTemplate requireOfAssociation(int stationId, int templateId) {
        var template = templates.requireUsable(stationId, templateId);
        if (!template.ofAssociation()) throw DocumentRefusal.DOCUMENT_TEMPLATE_USE_NOT_ASSOCIATION.raise();
        return template;
    }

    private TemplateUseResponse response(DocumentTemplate template, @Nullable TemplateStationUse use) {
        var audience = Optional.ofNullable(use)
                .map(chosen -> RestrictionAudience.of(restrictions.findRestrictionSet(
                        RestrictionType.DOCUMENT_TEMPLATE_STATION_USE, chosen.id(), chosen.restrictionMode())))
                .orElseGet(RestrictionAudience::empty);
        return new TemplateUseResponse(
                template.id(),
                template.name(),
                template.kind(),
                template.legal(),
                template.selfService(),
                template.cooldownDays(),
                use != null && use.selfService(),
                audience,
                use == null ? null : use.issuerId(),
                use == null ? null : use.issuerFunction());
    }
}

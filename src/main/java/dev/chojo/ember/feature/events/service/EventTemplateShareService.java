/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which partner stations an appointment made from a template is shared with.
 *
 * <p>A template's share is only a starting point: it is handed to the appointment editor, and the
 * appointment's own share is what is written and checked when the appointment is saved. So nothing
 * here asks whether sharing would be allowed for the appointment; it only keeps the template from
 * naming a partner that is not the station's.
 */
@Singleton
public class EventTemplateShareService {
    private static final Logger log = LoggerFactory.getLogger(EventTemplateShareService.class);

    private final EventFederationRepository shares;
    private final FederationRepository partners;

    @Inject
    public EventTemplateShareService(EventFederationRepository shares, FederationRepository partners) {
        this.shares = shares;
        this.partners = partners;
    }

    /**
     * How a template shares the appointments made from it, with the partners it names.
     *
     * @param templateId the template
     * @return the share and its partners, or empty where the template shares nothing
     */
    public Optional<TemplateShare> find(int templateId) {
        return shares.findShare(AppointmentOwner.TEMPLATE, templateId)
                .map(share -> new TemplateShare(share, shares.findShareTargets(AppointmentOwner.TEMPLATE, share.id())));
    }

    /**
     * Sets how a template shares the appointments made from it. Partners that are not the station's
     * are left out rather than stored.
     *
     * @param stationId  the station the template belongs to
     * @param templateId the template
     * @param scope      every partner, or the ones named
     * @param partnerIds the partners named, read only where the scope names them
     * @return the share and the partners kept
     */
    public TemplateShare set(int stationId, int templateId, ShareScope scope, List<Integer> partnerIds) {
        Set<Integer> ours = partners.findPartners(stationId).stream()
                .map(FederationPartner::id)
                .collect(Collectors.toSet());
        List<Integer> kept = scope == ShareScope.SPECIFIC
                ? partnerIds.stream().distinct().filter(ours::contains).toList()
                : List.of();
        var share = Transactions.call(() -> {
            var written = shares.setShare(AppointmentOwner.TEMPLATE, templateId, scope);
            shares.setShareTargets(AppointmentOwner.TEMPLATE, written.id(), kept);
            return written;
        });
        log.info("Set federation share for template {} (scope {}, {} targets)", templateId, scope, kept.size());
        return new TemplateShare(share, kept);
    }

    /**
     * Stops a template from sharing the appointments made from it.
     *
     * @param templateId the template
     */
    public void remove(int templateId) {
        shares.removeShare(AppointmentOwner.TEMPLATE, templateId);
        log.info("Removed federation share for template {}", templateId);
    }

    /**
     * A template's share with the partners it names.
     *
     * @param share      the share
     * @param partnerIds the partners named, empty where it shares with every partner
     */
    public record TemplateShare(EventFederationShare share, List<Integer> partnerIds) {}
}

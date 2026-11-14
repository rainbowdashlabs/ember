/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.events.service.EventGroupPartDates;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.signing.entity.AgreementSigner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Who signed what an event group asks every part for, for whoever handles its sign-ups.
 *
 * <p>Such a document is signed once for the event group, on whichever part it was asked on, so the signers
 * are gathered from every part ({@link AgreementSigners}) and each participant is listed once per document:
 * with the copy that got furthest (signed or confirmed on paper before still asked, before withdrawn or let
 * go), the earliest part's where two got as far. Members of partner stations sign at home per part and
 * are listed the same way. A document a part asks for on its own is left to that part's page.
 */
@Singleton
public class EventGroupAgreementSigners {
    private final AgreementSigners signers;
    private final EventRequirementRepository documents;
    private final EventGroupPartDates partDates;

    @Inject
    public EventGroupAgreementSigners(
            AgreementSigners signers, EventRequirementRepository documents, EventGroupPartDates partDates) {
        this.signers = signers;
        this.documents = documents;
        this.partDates = partDates;
    }

    /**
     * @param eventGroupId the event group, already checked to be the reader's station's
     * @return the signers, by name and then by the event group's order of documents
     */
    public List<AgreementSigner> of(int eventGroupId) {
        List<Integer> order = documents.forEventGroup(eventGroupId).stream()
                .map(RequiredTemplate::templateId)
                .toList();
        if (order.isEmpty()) return List.of();
        var once = new LinkedHashMap<String, AgreementSigner>();
        for (var part : partDates.parts(eventGroupId)) {
            for (var signer : signers.of(part.eventId(), part.date())) {
                if (!order.contains(signer.templateId())) continue;
                once.merge(keyOf(signer), signer, (kept, found) -> rank(found) > rank(kept) ? found : kept);
            }
        }
        return once.values().stream()
                .sorted(Comparator.comparing(AgreementSigner::name)
                        .thenComparing(signer -> order.indexOf(signer.templateId())))
                .toList();
    }

    /** How far a copy got: signed or confirmed on paper, still asked, or withdrawn or let go. */
    private static int rank(AgreementSigner signer) {
        return switch (signer.state()) {
            case SIGNED, PAPER_CONFIRMED -> 2;
            case OPEN, BY_STATION -> 1;
            case REVOKED, WAIVED -> 0;
        };
    }

    private static String keyOf(AgreementSigner signer) {
        var partner = signer.partnerMember();
        String who =
                partner != null ? partner.stationUid() + "/" + partner.memberUid() : String.valueOf(signer.memberId());
        return who + ":" + signer.templateId();
    }
}

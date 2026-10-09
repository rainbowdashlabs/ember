/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.AgreementSigner;
import dev.chojo.ember.feature.signing.entity.AppointmentRequest;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.PartnerAgreement;
import dev.chojo.ember.feature.signing.entity.PartnerAgreementState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureWithdrawal;
import dev.chojo.ember.feature.signing.repository.PartnerAgreementRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who signed what an appointment asks for on a date, for whoever runs it. On an appointment without
 * registrations this is the list of who said "I will come" by signing; on one with registrations it is the
 * same per document beside the registrations.
 *
 * <p>Each participant's latest copy of each document on which something was signed online or confirmed on
 * paper, with where it stands now: signed, on paper, still waiting for another signer, or withdrawn. A
 * participant who said they will not come on the date since is marked, since their signature then no longer
 * means they come.
 *
 * <p>Members of partner stations sign at home; they are listed from the copies that came back from their
 * station, or the paper copy confirmed here, by the name their station shares, else as a member of that
 * station. One whose station only took the document on is not listed until a signed copy comes back.
 */
@Singleton
public class AgreementSigners {
    private final SignatureRequestRepository requests;
    private final EventRequirementRepository documents;
    private final EventRegistrationRepository registrations;
    private final MemberNameResolver names;
    private final PartnerAgreementRepository partnerAgreements;
    private final EventFederationRepository federation;

    @Inject
    public AgreementSigners(
            SignatureRequestRepository requests,
            EventRequirementRepository documents,
            EventRegistrationRepository registrations,
            MemberNameResolver names,
            PartnerAgreementRepository partnerAgreements,
            EventFederationRepository federation) {
        this.requests = requests;
        this.documents = documents;
        this.registrations = registrations;
        this.names = names;
        this.partnerAgreements = partnerAgreements;
        this.federation = federation;
    }

    /**
     * @param eventId the appointment
     * @param date    the date
     * @return the signers, by name and then by the appointment's order of documents
     */
    public List<AgreementSigner> of(int eventId, LocalDate date) {
        var required = documents.forEvent(eventId);
        Map<Integer, String> documentNames =
                required.stream().collect(Collectors.toMap(RequiredTemplate::templateId, RequiredTemplate::name));
        List<Integer> order =
                required.stream().map(RequiredTemplate::templateId).toList();
        return Stream.concat(
                        ownSigners(eventId, date, documentNames).stream(),
                        partnerSigners(eventId, date, documentNames).stream())
                .sorted(Comparator.comparing(AgreementSigner::name)
                        .thenComparing(signer -> order.indexOf(signer.templateId())))
                .toList();
    }

    private List<AgreementSigner> ownSigners(int eventId, LocalDate date, Map<Integer, String> documentNames) {
        var agreed = requests.agreedForAppointment(eventId, date);
        if (agreed.isEmpty()) return List.of();
        var fields = requests
                .fieldsOfAll(agreed.stream().map(found -> found.request().id()).toList())
                .stream()
                .collect(Collectors.groupingBy(RequestedSignature::requestId));
        Set<Integer> refused = registrations.findByEventAndDate(eventId, date).stream()
                .filter(registration -> registration.status() == RegistrationStatus.DECLINED)
                .map(EventRegistration::memberId)
                .collect(Collectors.toSet());
        return agreed.stream()
                .map(found ->
                        signer(found, fields.getOrDefault(found.request().id(), List.of()), documentNames, refused))
                .toList();
    }

    private AgreementSigner signer(
            AppointmentRequest found,
            List<RequestedSignature> fields,
            Map<Integer, String> documentNames,
            Set<Integer> refused) {
        var request = found.request();
        @Nullable
        Instant withdrawnAt = request.state() == RequestState.REVOKED
                ? requests.withdrawalOf(request.id())
                        .map(SignatureWithdrawal::withdrawnAt)
                        .orElse(null)
                : null;
        Instant signedAt = fields.stream()
                .filter(field -> field.state() == FieldState.SIGNED || field.state() == FieldState.PAPER_CONFIRMED)
                .map(RequestedSignature::settledAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(request.createdAt());
        return new AgreementSigner(
                found.memberId(),
                null,
                names.identified(found.memberId()),
                found.templateId(),
                documentNames.getOrDefault(found.templateId(), request.memberName()),
                RequirementSignatureStates.overall(request.state(), fields),
                signedAt,
                withdrawnAt,
                refused.contains(found.memberId()));
    }

    private List<AgreementSigner> partnerSigners(int eventId, LocalDate date, Map<Integer, String> documentNames) {
        return partnerAgreements.forDate(eventId, date).stream()
                .filter(found -> found.templateId() != null && found.state() != PartnerAgreementState.ASKED)
                .map(found -> partnerSigner(found, Objects.requireNonNull(found.templateId()), documentNames))
                .toList();
    }

    private AgreementSigner partnerSigner(PartnerAgreement found, int templateId, Map<Integer, String> documentNames) {
        Integer partnerId = found.partnerId();
        String shared = partnerId == null
                ? null
                : federation.getCachedName(partnerId, found.remoteMemberId()).orElse(null);
        var identity = new MemberIdentity(found.partnerStationUid(), found.remoteMemberId())
                .withDisplay(shared, found.partnerName(), null, null);
        return new AgreementSigner(
                null,
                identity,
                shared != null ? shared : Objects.requireNonNullElse(found.partnerName(), ""),
                templateId,
                documentNames.getOrDefault(templateId, found.templateName()),
                stateOf(found),
                Objects.requireNonNullElse(found.firstCopyAt(), found.updatedAt()),
                found.state() == PartnerAgreementState.WITHDRAWN ? found.updatedAt() : null,
                false);
    }

    private static RequirementSignatureState stateOf(PartnerAgreement found) {
        return switch (found.state()) {
            case SIGNED -> found.complete() ? RequirementSignatureState.SIGNED : RequirementSignatureState.OPEN;
            case PAPER_CONFIRMED -> RequirementSignatureState.PAPER_CONFIRMED;
            case WITHDRAWN -> RequirementSignatureState.REVOKED;
            case ASKED, MISSING -> RequirementSignatureState.OPEN;
        };
    }
}

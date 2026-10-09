/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.events.entity.PartnerAgreementOffer;
import dev.chojo.ember.feature.events.entity.PartnerDocumentToSign;
import dev.chojo.ember.feature.events.service.PartnerAppointmentSignatures;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.generator.entity.FieldStatements;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.PartnerSigning;
import dev.chojo.ember.feature.signing.entity.RemoteAgreement;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.route.RemoteSigningRoutes;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The documents a partner station's appointment asks the members of this station to sign, at the members'
 * home installation, where they are signed.
 *
 * <p>Registering a member for a partner's appointment asks the partner for the documents of that date. Each
 * one is the copy every partner's signer signs alike; it is filed in the member's documents here as it came,
 * byte for byte, and its signatures are asked for like those of any document here: the member, or the
 * guardians, with this station's second factors, evidence and seal. What each signer confirms is what the
 * partner's template words, else their default statement in the document's language; the signed document is
 * kept as long as the partner's template says. The partner is told the document was taken on, and every sealed
 * state goes back to it ({@link PartnerDeliveries}).
 *
 * <p>A partner's appointment without registrations offers its documents on its page here instead: the reader
 * signs for themselves or a member in their care, the document is taken on the same way, and the partner counts
 * the signature as "I will come".
 *
 * <p>Nothing here holds the registration up. A partner that hands out nothing, cannot be reached, or hands out
 * a file that is not what it says, and a station that keeps no documents, leave the member registered without
 * a document to sign; the partner then counts the document as missing for them, and its organisers can confirm
 * a signed paper copy instead.
 *
 * <p>A document already taken on for the member, the appointment and the date with the same bytes is not
 * taken on twice. Withdrawing the registration lets the signatures still open go; what was signed stays.
 */
@Singleton
public class PartnerSignatures implements PartnerAppointmentSignatures {
    private static final String PDF = "application/pdf";
    private static final Logger log = LoggerFactory.getLogger(PartnerSignatures.class);

    private final FederationEntityResolver partners;
    private final FederationTransport transport;
    private final StationMemberRepository members;
    private final GuardianPolicy guardians;
    private final MemberNameResolver names;
    private final DocumentService documents;
    private final DocumentIntake intake;
    private final SignatureRequestService requestService;
    private final SignatureRequestRepository requests;
    private final PartnerSigningRepository links;
    private final PartnerDeliveries deliveries;
    private final RequirementSignatureStates states;

    @Inject
    public PartnerSignatures(
            FederationEntityResolver partners,
            FederationTransport transport,
            StationMemberRepository members,
            GuardianPolicy guardians,
            MemberNameResolver names,
            DocumentService documents,
            DocumentIntake intake,
            SignatureRequestService requestService,
            SignatureRequestRepository requests,
            PartnerSigningRepository links,
            PartnerDeliveries deliveries,
            RequirementSignatureStates states) {
        this.partners = partners;
        this.transport = transport;
        this.members = members;
        this.guardians = guardians;
        this.names = names;
        this.documents = documents;
        this.intake = intake;
        this.requestService = requestService;
        this.requests = requests;
        this.links = links;
        this.deliveries = deliveries;
        this.states = states;
    }

    @Override
    public List<PartnerDocumentToSign> registered(
            StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {
        var member = actedFor(session, memberUid);
        if (member.isEmpty() || !keepsDocuments(session.stationId())) return List.of();
        var partner = activePartner(session.stationId(), partnerStationUid);
        if (partner.isEmpty()) return List.of();
        var taken = new ArrayList<PartnerDocumentToSign>();
        for (var agreement : agreementsOf(partner.get(), eventId, eventDate)) {
            try {
                taken.add(takeOn(session, partner.get(), eventId, eventDate, member.get(), agreement));
            } catch (RuntimeException e) {
                log.warn(
                        "Could not take on document {} of partner {} for member {}",
                        agreement.templateId(),
                        partner.get().id(),
                        member.get().id(),
                        e);
            }
        }
        return taken;
    }

    @Override
    public List<PartnerAgreementOffer> offers(
            StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate) {
        if (!keepsDocuments(session.stationId())) return List.of();
        return activePartner(session.stationId(), partnerStationUid)
                .map(partner -> agreementsOf(partner, eventId, eventDate).stream()
                        .map(agreement -> new PartnerAgreementOffer(agreement.templateId(), agreement.title()))
                        .toList())
                .orElse(List.of());
    }

    @Override
    public List<PartnerDocumentToSign> offered(
            StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {
        if (actedFor(session, memberUid).isEmpty()) throw EventRefusal.AGREEMENT_NOT_FOR_MEMBER.raise();
        var taken = registered(session, partnerStationUid, eventId, eventDate, memberUid);
        if (taken.isEmpty()) throw EventRefusal.AGREEMENT_NOTHING_TO_SIGN.raise();
        log.info("Member {} took on the agreement of appointment {} of a partner", memberUid, eventId);
        return taken;
    }

    private List<RemoteAgreement> agreementsOf(FederationPartner partner, int eventId, LocalDate eventDate) {
        return transport.getList(partner, RemoteSigningRoutes.AGREEMENTS.at(eventId, eventDate), RemoteAgreement.class);
    }

    private Optional<FederationPartner> activePartner(int stationId, UUID partnerStationUid) {
        try {
            return Optional.of(partners.requireActivePartner(stationId, partnerStationUid));
        } catch (RefusalResponse e) {
            return Optional.empty();
        }
    }

    @Override
    public void withdrawn(
            StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {
        var member = actedFor(session, memberUid);
        if (member.isEmpty()) return;
        for (int requestId : links.openRequests(
                partnerStationUid, eventId, eventDate, member.get().id())) {
            if (requestService.withdrawUnasked(requestId)) {
                log.info(
                        "Signing request {} withdrawn: member {} no longer takes part in appointment {} of a partner",
                        requestId,
                        member.get().id(),
                        eventId);
            }
        }
    }

    private PartnerDocumentToSign takeOn(
            StationSession session,
            FederationPartner partner,
            int eventId,
            LocalDate eventDate,
            StationMember member,
            RemoteAgreement agreement) {
        byte[] content = Base64.getDecoder().decode(agreement.pdf());
        if (!Sha256.hex(content).equals(agreement.sha256())) {
            throw new IllegalArgumentException("The file the partner handed out is not the one it names");
        }
        var live = links.live(partner.partnerStationId(), eventId, eventDate, member.id(), agreement.sha256());
        var request = live.isPresent()
                ? requests.findById(live.get().requestId()).orElseThrow()
                : ask(session, partner, eventId, eventDate, member, agreement, content);
        var signature = states.ofRequest(session, agreement.templateId(), member.id(), request);
        return new PartnerDocumentToSign(agreement.title(), member.id(), names.identified(member.id()), signature);
    }

    private SignatureRequest ask(
            StationSession session,
            FederationPartner partner,
            int eventId,
            LocalDate eventDate,
            StationMember member,
            RemoteAgreement agreement,
            byte[] content) {
        int stationId = session.stationId();
        var upload = new DocumentIntake.Upload(agreement.fileName(), PDF, content);
        String type = intake.take(stationId, StorageCategory.MEMBER_DOCUMENTS, upload, DocumentDoor.STATION.intake());
        var document = documents.store(
                stationId,
                List.of(member.id()),
                agreement.title(),
                agreement.fileName(),
                type,
                content,
                false,
                agreement.retentionMonths() != null,
                Uploader.member(session.member().id()),
                List.of());
        var ask = new SignatureRequestService.PartnerAsk(
                stationId,
                document.id(),
                member.id(),
                content,
                new FieldStatements(agreement.language(), agreement.statements()),
                agreement.retentionMonths(),
                agreement.copyAttached());
        var request = requestService.requestForPartner(
                ask,
                created -> links.create(new PartnerSigning.Draft(
                        stationId,
                        created.id(),
                        partner.id(),
                        partner.partnerStationId(),
                        eventId,
                        eventDate,
                        agreement.templateId(),
                        agreement.templateVersion())));
        links.forRequest(request.id()).ifPresent(deliveries::announce);
        return request;
    }

    /** The member registered, where the reader may act for them: themselves or a member in their care. */
    private Optional<StationMember> actedFor(StationSession session, UUID memberUid) {
        return members.findByUid(session.stationId(), memberUid)
                .filter(member -> guardians.mayActFor(session.user(), member.id()));
    }

    private boolean keepsDocuments(int stationId) {
        try {
            documents.requireKept(stationId, DocumentDoor.STATION);
            return true;
        } catch (RefusalResponse e) {
            return false;
        }
    }
}

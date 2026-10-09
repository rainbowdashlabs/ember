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
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.generator.entity.FieldStatements;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.signing.entity.PartnerSigning;
import dev.chojo.ember.feature.signing.entity.RemoteAgreement;
import dev.chojo.ember.feature.signing.entity.RemoteAgreementRelease;
import dev.chojo.ember.feature.signing.entity.RemoteRegisteredMember;
import dev.chojo.ember.feature.signing.entity.RemoteRequirementChange;
import dev.chojo.ember.feature.signing.entity.RemoteRequirementChangeAnswer;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.route.RemoteSigningRoutes;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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
 * <p><b>Documents changed later.</b> When the partner adds a document to the appointment or takes one off, it
 * says so in a notice signed with its federation key, naming its members registered from here on dates still
 * ahead. The documents of each such date are fetched again and every one of those members is asked for those
 * they do not stand asked for or signed yet, the same way as at registration, so nobody is asked twice. The
 * requests still open for a document taken off, on dates still ahead, are let go, and the partner is told
 * which. Dates before today in this station's zone stay as they are.
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
public class PartnerSignatures implements PartnerAppointmentSignatures, FederationServer {
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
    private final StationRepository stations;

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
            RequirementSignatureStates states,
            StationRepository stations) {
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
        this.stations = stations;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteRequirementChange, RemoteRequirementChangeAnswer>serve(
                RemoteSigningRoutes.REQUIREMENTS_CHANGED,
                (partner, params, body) -> changed(partner, params.integer("eventId"), body));
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

    /**
     * Carries a change of the documents a partner's appointment asks for over to the members of this station:
     * those registered on dates still ahead are asked for what they do not stand asked for yet, and the open
     * requests of the documents taken off are let go.
     *
     * @param partner the partnership the notice arrived on
     * @param eventId the appointment, by its id at the partner
     * @param change  what the partner says changed
     * @return the requests let go, for the partner
     */
    RemoteRequirementChangeAnswer changed(ServingPartner partner, int eventId, RemoteRequirementChange change) {
        int stationId = partner.servingStationId();
        var today = today(stationId);
        var released = release(partner.row(), eventId, change.removedTemplateIds(), today);
        int asked = keepsDocuments(stationId)
                ? askRegistered(stationId, partner.row(), eventId, change.registered(), today)
                : 0;
        log.info(
                "Partner {} changed the documents of appointment {}: {} asked for, {} let go",
                partner.partnerId(),
                eventId,
                asked,
                released.size());
        return new RemoteRequirementChangeAnswer(released);
    }

    private List<RemoteAgreementRelease> release(
            FederationPartner partner, int eventId, List<Integer> removedTemplateIds, LocalDate today) {
        var released = new ArrayList<RemoteAgreementRelease>();
        for (var link : links.openForTemplatesFrom(partner.partnerStationId(), eventId, removedTemplateIds, today)) {
            if (!requestService.withdrawUnasked(link.requestId())) continue;
            requests.findById(link.requestId())
                    .map(SignatureRequest::memberId)
                    .flatMap(members::findById)
                    .ifPresent(member -> released.add(
                            new RemoteAgreementRelease(member.uid(), link.eventDate(), link.remoteTemplateId())));
        }
        return released;
    }

    private int askRegistered(
            int stationId,
            FederationPartner partner,
            int eventId,
            List<RemoteRegisteredMember> registered,
            LocalDate today) {
        Map<LocalDate, List<UUID>> byDate = registered.stream()
                .filter(entry -> !entry.eventDate().isBefore(today))
                .collect(Collectors.groupingBy(
                        RemoteRegisteredMember::eventDate,
                        TreeMap::new,
                        Collectors.mapping(RemoteRegisteredMember::memberUid, Collectors.toList())));
        int asked = 0;
        for (var date : byDate.entrySet()) {
            var agreements = agreementsOf(partner, eventId, date.getKey());
            for (var memberUid : date.getValue()) {
                var member = members.findByUid(stationId, memberUid).filter(found -> !found.former());
                if (member.isEmpty()) continue;
                for (var agreement : agreements) {
                    if (askIfNotAsked(stationId, partner, eventId, date.getKey(), member.get(), agreement)) asked++;
                }
            }
        }
        return asked;
    }

    private boolean askIfNotAsked(
            int stationId,
            FederationPartner partner,
            int eventId,
            LocalDate eventDate,
            StationMember member,
            RemoteAgreement agreement) {
        if (links.asked(partner.partnerStationId(), eventId, eventDate, member.id(), agreement.templateId())) {
            return false;
        }
        try {
            ask(stationId, Uploader.nobody(), partner, eventId, eventDate, member, agreement);
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "Could not ask member {} for document {} added to appointment {} of partner {}",
                    member.id(),
                    agreement.templateId(),
                    eventId,
                    partner.id(),
                    e);
            return false;
        }
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

    private PartnerDocumentToSign takeOn(
            StationSession session,
            FederationPartner partner,
            int eventId,
            LocalDate eventDate,
            StationMember member,
            RemoteAgreement agreement) {
        var live = links.live(partner.partnerStationId(), eventId, eventDate, member.id(), agreement.sha256());
        var request = live.isPresent()
                ? requests.findById(live.get().requestId()).orElseThrow()
                : ask(
                        session.stationId(),
                        Uploader.member(session.member().id()),
                        partner,
                        eventId,
                        eventDate,
                        member,
                        agreement);
        var signature = states.ofRequest(session, agreement.templateId(), member.id(), request);
        return new PartnerDocumentToSign(agreement.title(), member.id(), names.identified(member.id()), signature);
    }

    private SignatureRequest ask(
            int stationId,
            Uploader uploader,
            FederationPartner partner,
            int eventId,
            LocalDate eventDate,
            StationMember member,
            RemoteAgreement agreement) {
        byte[] content = contentOf(agreement);
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
                uploader,
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

    private static byte[] contentOf(RemoteAgreement agreement) {
        byte[] content = Base64.getDecoder().decode(agreement.pdf());
        if (!Sha256.hex(content).equals(agreement.sha256())) {
            throw new IllegalArgumentException("The file the partner handed out is not the one it names");
        }
        return content;
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

    private LocalDate today(int stationId) {
        var zone = StationFormat.timezoneOf(stations.findById(stationId).orElse(null));
        return LocalDate.now(zone);
    }
}

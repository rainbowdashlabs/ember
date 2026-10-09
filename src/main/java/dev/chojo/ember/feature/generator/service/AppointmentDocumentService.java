/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.FieldStatements;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.GenerationOrigin;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.entity.RequirementGeneration;
import dev.chojo.ember.feature.generator.entity.RequirementSignature;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.generator.entity.RequirementStatus;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService.GeneratedDocumentResponse;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The documents an appointment asks its participants to bring, as a participant or their guardian
 * gets them: a copy filled with the participant's data and the appointment's, filed with the
 * participant and logged like every generated document.
 *
 * <p>The reader sees the documents of every member they act for (themselves and the members in their
 * care) who holds a place, pending or accepted, on the date. Nobody else gets a copy here: a member of
 * another appointment, or of another date of this one, is refused. A copy is never hidden from the
 * participant, whatever the template says, since it is theirs to print and sign. Data the participant's
 * profile lacks is left as a gap to fill in by hand, which is what a form to print is for. The copy is
 * issued by the template's issuer at the station.
 *
 * <p>The status per participant and document: a copy is generated or it is not, and a copy from an older
 * version of the template is marked as such so a new one can be generated. Beside it stands the latest
 * scan of a signed paper copy handed in for it, whether it waits, was confirmed or was turned down, and
 * where signatures were asked for on the copy, where each of them stands ({@link RequirementSignatures}). On an
 * appointment that takes no registrations, a document the participant or a guardian signs is offered to sign
 * online wherever nothing stands for it yet, since no registration asks for it.
 */
@Singleton
public class AppointmentDocumentService {
    private final EventRequirementRepository requirements;
    private final PaperSubmissionRepository submissions;
    private final DocumentTemplateService templates;
    private final DocumentGeneratorService generator;
    private final DocumentGenerationService generation;
    private final GuardianPolicy guardians;
    private final EventRegistrationRepository registrations;
    private final EventFieldRepository fields;
    private final MemberNameResolver names;
    private final DocumentIssuerService issuers;
    private final EventRestrictionService audience;
    private final RequirementSignatures signatures;

    @Inject
    public AppointmentDocumentService(
            EventRequirementRepository requirements,
            PaperSubmissionRepository submissions,
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            GuardianPolicy guardians,
            EventRegistrationRepository registrations,
            EventFieldRepository fields,
            MemberNameResolver names,
            DocumentIssuerService issuers,
            EventRestrictionService audience,
            RequirementSignatures signatures) {
        this.audience = audience;
        this.signatures = signatures;
        this.requirements = requirements;
        this.submissions = submissions;
        this.templates = templates;
        this.generator = generator;
        this.generation = generation;
        this.guardians = guardians;
        this.registrations = registrations;
        this.fields = fields;
        this.names = names;
        this.issuers = issuers;
    }

    /**
     * One document an appointment asks for, as it stands for one participant.
     *
     * @param templateId  the template
     * @param name        what the template is called
     * @param status      whether a copy was generated for the participant
     * @param documentId  the copy filed with the participant, or null
     * @param generatedAt when that copy was generated, or null
     * @param outdated    whether the template changed since that copy was generated
     * @param paper       the latest scan of a signed paper copy handed in for the participant, or null
     *                    where none was
     * @param signature   the latest signatures asked for on the participant's copy, or null where none were
     * @param agreementOffered whether the reader can sign the document's agreement online for the participant
     *                    now: the appointment takes no registrations, the document asks the participant or a
     *                    guardian to sign, and nothing signed or still open on a copy stands for it
     */
    public record RequiredDocumentStatus(
            int templateId,
            String name,
            RequirementStatus status,
            @Nullable Integer documentId,
            @Nullable Instant generatedAt,
            boolean outdated,
            @Nullable PaperSubmission paper,
            @Nullable RequirementSignature signature,
            boolean agreementOffered) {}

    /**
     * The documents one participant is asked to bring.
     *
     * @param memberId  the participant
     * @param name      their name
     * @param documents every document the appointment asks for, in its order
     */
    public record ParticipantDocuments(int memberId, String name, List<RequiredDocumentStatus> documents) {}

    /**
     * What an appointment asks participants to bring on a date, as one reader sees it.
     *
     * @param required     every document the appointment asks for, in its order, which anybody who sees
     *                     the appointment may know
     * @param own          the documents of the reader and every member in their care who takes part, where
     *                     the issuer's open field shows as the station's to sign
     * @param participants the documents of every participant, for whoever manages the registrations, where
     *                     the issuer's open field shows as open; null for anybody else
     */
    public record AppointmentDocuments(
            List<RequiredTemplate> required,
            List<ParticipantDocuments> own,
            @Nullable List<ParticipantDocuments> participants) {}

    /**
     * The documents an appointment asks for on a date: the list itself for every reader, the copies of
     * the participants the reader acts for, and for an organiser where every participant stands.
     *
     * @param session  the reader
     * @param event    the appointment, already checked to be one the reader may see
     * @param date     the date of the appointment
     * @param overview whether the reader manages the registrations and is shown every participant
     * @return what the reader sees; all empty where the appointment asks for nothing
     */
    public AppointmentDocuments documentsToBring(
            StationSession session, StationEvent event, LocalDate date, boolean overview) {
        var required = inUse(event.id());
        if (required.isEmpty()) return new AppointmentDocuments(List.of(), List.of(), overview ? List.of() : null);
        var household = guardians.household(session.user());
        List<Integer> own;
        List<Integer> everyone;
        if (event.requiresRegistration()) {
            everyone = registrations.findRegisteredMemberIds(event.id(), date);
            own = household.stream().filter(everyone::contains).toList();
        } else {
            everyone = requirements.copiedBy(event.id(), date);
            own = household.stream()
                    .filter(memberId -> mayAttend(event, memberId))
                    .toList();
        }
        var asked = new LinkedHashSet<>(own);
        if (overview) asked.addAll(everyone);
        var copies = asked.isEmpty()
                ? new Copies(List.of(), List.of(), List.of())
                : new Copies(
                        requirements.latest(event.id(), date, asked),
                        submissions.latest(event.id(), date, asked),
                        signatures.of(session, event.id(), date, asked));
        var offers = event.requiresRegistration() ? Set.<Offer>of() : offers(event, own);
        return new AppointmentDocuments(
                required,
                participantsOf(own, required, copies.asParticipantsSee(), offers),
                overview ? participantsOf(everyone, required, copies, Set.of()) : null);
    }

    /** A document whose agreement could be signed for a participant, where nothing stands for it yet. */
    private record Offer(int memberId, int templateId) {}

    /** The documents of an appointment without registrations each participant the reader acts for signs. */
    private Set<Offer> offers(StationEvent event, Collection<Integer> own) {
        var offers = new HashSet<Offer>();
        for (int memberId : own) {
            signableFor(event, memberId).forEach(template -> offers.add(new Offer(memberId, template.id())));
        }
        return offers;
    }

    /** The generated copies, the scans handed in and the signatures asked for, for the participants asked about. */
    private record Copies(
            List<RequirementGeneration> generated, List<PaperSubmission> scans, List<RequirementSignature> signed) {

        /** The copies as participants and guardians see them, the issuer's field as the station's to sign. */
        Copies asParticipantsSee() {
            return new Copies(
                    generated,
                    scans,
                    signed.stream().map(RequirementSignature::asParticipantsSee).toList());
        }
    }

    /**
     * Refuses a scan to be handed in for a member by somebody who may not: the reader has to act for
     * the member, or manage the registrations, and the member has to take part on the date.
     *
     * @param session  the reader
     * @param event    the appointment, already checked to be one the reader may see
     * @param date     the date of the appointment
     * @param memberId the participant
     * @param manages  whether the reader manages the registrations of the station
     */
    void requireMayHandIn(StationSession session, StationEvent event, LocalDate date, int memberId, boolean manages) {
        boolean actsFor = manages || guardians.mayActFor(session.user(), memberId);
        if (!actsFor || !takesPart(event, date, memberId)) {
            throw DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS.raise();
        }
    }

    /**
     * @param stationId  the station
     * @param eventId    the appointment
     * @param templateId the template
     * @return the template, refusing one the appointment does not ask for
     */
    DocumentTemplate requireAsked(int stationId, int eventId, int templateId) {
        boolean required = inUse(eventId).stream().anyMatch(template -> template.templateId() == templateId);
        if (!required) throw DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_REQUIRED.raise();
        return templates.requireInUse(stationId, templateId);
    }

    /**
     * Whether a member takes part in the appointment on the date: registered where it takes
     * registrations, and anybody it is meant for where it takes none, since nobody could ever be
     * registered for that.
     */
    private boolean takesPart(StationEvent event, LocalDate date, int memberId) {
        if (!event.requiresRegistration()) return mayAttend(event, memberId);
        return registrations.findRegisteredMemberIds(event.id(), date).contains(memberId);
    }

    /**
     * Whether the appointment is meant for the member: they may see it and belong to its audience. Read
     * with the member's own rights only, so a manager's right to register anybody does not hand them
     * copies of documents for appointments that are not theirs to attend.
     */
    private boolean mayAttend(StationEvent event, int memberId) {
        return audience.canRegister(event.id(), memberId, Set.of());
    }

    private List<ParticipantDocuments> participantsOf(
            Collection<Integer> memberIds, List<RequiredTemplate> required, Copies copies, Set<Offer> offers) {
        return memberIds.stream()
                .map(memberId -> new ParticipantDocuments(
                        memberId, names.identified(memberId), statuses(required, copies, memberId, offers)))
                .toList();
    }

    /**
     * Generates a copy of a document the appointment asks for and files it with the participant.
     *
     * @param session    the reader, the participant or their guardian
     * @param event      the appointment, already checked to be one the reader may see
     * @param date       the date of the appointment
     * @param templateId the document asked for
     * @param memberId   the participant
     * @return the filed copy and the data it left as gaps
     */
    public GeneratedDocumentResponse generate(
            StationSession session, StationEvent event, LocalDate date, int templateId, int memberId) {
        requireMayHandIn(session, event, date, memberId, false);
        var template = requireAsked(session.stationId(), event.id(), templateId);
        return generateFor(event, date, template, memberId, session.member().id());
    }

    /**
     * Generates a copy of a document the appointment asks for and files it with the participant, for a
     * caller that already knows the member takes part, such as their registration. The copy is issued by
     * the template's issuer at the appointment's station.
     *
     * @param event       the appointment
     * @param date        the date of the appointment
     * @param template    the document asked for
     * @param memberId    the participant
     * @param generatedBy the member the copy is generated by: whoever registered the participant
     * @return the filed copy and the data it left as gaps
     */
    public GeneratedDocumentResponse generateFor(
            StationEvent event, LocalDate date, DocumentTemplate template, int memberId, int generatedBy) {
        var context =
                new GenerationContext(generatedBy, facts(event, date), issuers.ofTemplate(template, event.stationId()));
        var prepared = generator.prepare(template, memberId, context);
        return generation.file(
                template, memberId, generatedBy, GenerationOrigin.appointment(event.id(), date), prepared);
    }

    /**
     * The documents an appointment asks for whose copy for the member asks the member or a guardian to
     * sign, in the appointment's order. A document only the issuer signs is not among them.
     *
     * @param event    the appointment
     * @param memberId the participant
     * @return the templates, none where the appointment asks for nothing to sign
     */
    public List<DocumentTemplate> signableFor(StationEvent event, int memberId) {
        return inUse(event.id()).stream()
                .flatMap(required -> templates.find(required.templateId()).stream())
                .filter(template -> generator.asksMemberSideToSign(template, memberId))
                .toList();
    }

    /**
     * Draws the one copy of a document the appointment asks for that partner stations' members sign alike on
     * a date: about nobody in particular, with the appointment's values for that date and the station's.
     *
     * @param event    the appointment
     * @param date     the date
     * @param template the document, which partners can sign ({@link MemberNeutralTemplates})
     * @return the document, not filed anywhere
     */
    public DocumentGeneratorService.Rendered drawForPartners(
            StationEvent event, LocalDate date, DocumentTemplate template) {
        return generator.drawForAppointment(template, event.stationId(), facts(event, date));
    }

    /**
     * @param template a document partner stations sign
     * @return what its signers confirm, as {@link #drawForPartners} draws it
     */
    public FieldStatements statementsForPartners(DocumentTemplate template) {
        return generator.statementsForAppointment(template);
    }

    /**
     * @param eventId the appointment
     * @return the templates it asks participants to bring and still offers, in its order
     */
    public List<DocumentTemplate> templatesAskedFor(int eventId) {
        return inUse(eventId).stream()
                .flatMap(required -> templates.find(required.templateId()).stream())
                .toList();
    }

    private List<RequiredTemplate> inUse(int eventId) {
        return requirements.forEvent(eventId).stream()
                .filter(template -> !template.archived())
                .toList();
    }

    private static List<RequiredDocumentStatus> statuses(
            List<RequiredTemplate> required, Copies copies, int memberId, Set<Offer> offers) {
        return required.stream()
                .map(template -> status(
                        template,
                        offers.contains(new Offer(memberId, template.templateId())),
                        copies.generated().stream()
                                .filter(copy ->
                                        copy.memberId() == memberId && copy.templateId() == template.templateId())
                                .findFirst()
                                .orElse(null),
                        copies.scans().stream()
                                .filter(scan ->
                                        scan.memberId() == memberId && scan.templateId() == template.templateId())
                                .findFirst()
                                .orElse(null),
                        copies.signed().stream()
                                .filter(signed ->
                                        signed.memberId() == memberId && signed.templateId() == template.templateId())
                                .findFirst()
                                .orElse(null)))
                .toList();
    }

    private static RequiredDocumentStatus status(
            RequiredTemplate template,
            boolean signable,
            @Nullable RequirementGeneration copy,
            @Nullable PaperSubmission scan,
            @Nullable RequirementSignature signature) {
        boolean offered = signable && nothingStands(signature, scan);
        if (copy == null) {
            return new RequiredDocumentStatus(
                    template.templateId(),
                    template.name(),
                    RequirementStatus.NOT_GENERATED,
                    null,
                    null,
                    false,
                    scan,
                    signature,
                    offered);
        }
        return new RequiredDocumentStatus(
                template.templateId(),
                template.name(),
                RequirementStatus.GENERATED,
                copy.documentId(),
                copy.generatedAt(),
                copy.templateVersion() < template.version(),
                scan,
                signature,
                offered);
    }

    /**
     * Whether nothing stands for a document yet: no signatures asked for, or only ones withdrawn by a signer
     * or let go, and no signed paper copy confirmed.
     */
    private static boolean nothingStands(@Nullable RequirementSignature signature, @Nullable PaperSubmission scan) {
        if (scan != null && scan.state() == PaperState.CONFIRMED) return false;
        if (signature == null) return true;
        return signature.state() == RequirementSignatureState.REVOKED
                || signature.state() == RequirementSignatureState.WAIVED;
    }

    /** What a document says about the appointment on the date: its name, its times and its place. */
    private GenerationContext.EventFacts facts(StationEvent event, LocalDate date) {
        var span = event.occurrenceOn(date).orElseGet(() -> new StationEvent.Span(event.startTime(), event.endTime()));
        var location = AppointmentField.firstLocation(fields.findByEventOn(event.id(), date));
        return new GenerationContext.EventFacts(event.name(), span.start(), span.end(), location);
    }
}

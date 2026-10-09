/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.auth.BreachCheckWorker;
import dev.chojo.ember.auth.HibpClient;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TemplateSigning;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.generator.service.MemberNeutralTemplates;
import dev.chojo.ember.feature.generator.service.RequirementSignatures;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailConfirmationPolicy;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.entity.AppointmentRequest;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.handler.RequirementChangeSignaturesHandler;
import dev.chojo.ember.feature.signing.repository.AccountSignatureRepository;
import dev.chojo.ember.feature.signing.repository.IssuerSignatureRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.service.AppointmentSignatures;
import dev.chojo.ember.feature.signing.service.ChangedRequirementSignatures;
import dev.chojo.ember.feature.signing.service.InEmberSignatureProvider;
import dev.chojo.ember.feature.signing.service.IssuedLetterSigner;
import dev.chojo.ember.feature.signing.service.PartnerRequirementNotices;
import dev.chojo.ember.feature.signing.service.SignatureFieldService;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.signing.service.SignatureNotices;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.signing.service.SignerNames;
import dev.chojo.ember.feature.signing.service.SignerResolver;
import dev.chojo.ember.feature.signing.service.SigningActService;
import dev.chojo.ember.feature.signing.service.SigningAssertions;
import dev.chojo.ember.feature.signing.service.SigningGuards;
import dev.chojo.ember.feature.signing.service.SigningStarts;
import dev.chojo.ember.feature.signing.service.TemplateDocumentStatements;
import dev.chojo.ember.feature.signing.service.TestKeyStamps;
import dev.chojo.ember.feature.signing.service.TestSealing;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.BackupCodeService;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The citizens' festival of the demo and its photo consent, seeded through the real services: the consent
 * saves through the checks a manager's template passes and carries its statements, retention and copy
 * setting, it hangs on the festival the event seeder already made a week ahead, which stays open for
 * registration, and the participants' requests stand at every step, from signed and sealed through confirmed
 * on paper to open, with a guardian asked for each young member. Nobody is registered twice, a young member
 * with a guardian is left to register, and seeding twice leaves one of each.
 */
class DemoPhotoConsentSeederTest extends RepositoryTestBase {
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();

    private static DemoRunContext run;
    private static GeneratorTestBase.Wiring wiring;
    private static DemoPhotoConsentSeeder seeder;
    private static SignatureRequestService requests;
    private static EventRequirementRepository requirementRepo;
    private static ChangedRequirementSignatures changes;
    private static DemoClock clock;

    @BeforeAll
    static void seedTheFestival() {
        run = new DemoRunContext(new PasswordHasher().hash(DemoSeeder.PASSWORD));
        new DemoStationSeeder(accountRepo, stationRepo).seed(run);
        new DemoMemberSeeder(
                        accountRepo,
                        stationMemberRepo,
                        memberLookupService,
                        memberGroupRepo,
                        new MemberGroupSetRepository(),
                        profileFieldRepo,
                        profileFieldChangeRepo,
                        userTagRepo,
                        stationRepo)
                .seed(run);
        clock = new DemoClock(Clock.systemUTC());
        eventSeeder().seed(run);
        wiring = GeneratorTestBase.wire(run.primaryStation().station());
        seeder = seeder();
        seeder.seed(run);
    }

    private static DemoEventSeeder eventSeeder() {
        var events = newEventServices(new DomainEventBus(Set.of()));
        return new DemoEventSeeder(
                eventCategoryRepo,
                eventRegistrationRepo,
                eventFieldRepo,
                attendanceRepo,
                events.crud(),
                new EventTemplateService(
                        new EventTemplateRepository(),
                        attendanceRepo,
                        memberEligibility,
                        new AttendanceTemplateGuards(attendanceRepo)),
                events.restriction(),
                new EventRegistrationFieldService(new EventRegistrationFieldRepository(), memberEligibility),
                clock);
    }

    private static DemoPhotoConsentSeeder seeder() {
        var documents = wiring.documents();
        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, documents, guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        var evidenceRepo = new SigningEvidenceRepository();
        requests = new SignatureRequestService(
                requestRepo,
                evidenceRepo,
                wiring.log(),
                memberDocumentRepo,
                documents,
                stationMemberRepo,
                memberNameResolver,
                guardianPolicy,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                mock(SignatureNotices.class),
                new TemplateDocumentStatements(wiring.generator()));
        var fields = new SignatureFieldService(
                requestRepo,
                evidenceRepo,
                requests,
                guards,
                guardianPolicy,
                memberNameResolver,
                mock(SignatureNotices.class),
                completed -> {});
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var twoFactor = new TwoFactorService(
                twoFactorRepo,
                mock(TotpService.class),
                mock(BackupCodeService.class),
                audit,
                accountRepo,
                mock(MailLocaleService.class),
                mock(EmailService.class));
        var parties = new RelyingParties(null, null, false);
        var assertions = new SigningAssertions(parties, twoFactorRepo, new WebAuthnSettings());
        var names = new SignerNames(accountRepo, memberNameResolver);
        var keyStamps = TestKeyStamps.off(twoFactorRepo);
        var backend = localStorage();
        var images = new SignatureImageService(
                new AccountSignatureRepository(),
                accountRepo,
                new StorageService(new StorageBackendResolver(backend), backend));
        var acts = new SigningActService(
                requestRepo,
                requests,
                fields,
                memberDocumentRepo,
                documents,
                new InEmberSignatureProvider(twoFactor, assertions, names, keyStamps),
                TestSealing.stateSealer(memberDocumentRepo, documents, stationRepo, memberNameResolver),
                new SigningStarts(new WebAuthnChallengeRepository(TokenHasher.forTesting("demo-consent-pepper"))),
                assertions,
                names,
                twoFactor,
                authService(),
                images);
        requirementRepo = new EventRequirementRepository();
        var requirements = new EventRequirementService(
                requirementRepo,
                wiring.templates(),
                new MemberNeutralTemplates(wiring.templates()),
                new EventFederationRepository(),
                new DomainEventBus(Set.of(new RequirementChangeSignaturesHandler(
                        () -> changes, () -> mock(PartnerRequirementNotices.class)))));
        var submissions = new PaperSubmissionRepository();
        var appointments = new AppointmentDocumentService(
                requirementRepo,
                submissions,
                wiring.templates(),
                wiring.generator(),
                wiring.generation(),
                guardianPolicy,
                eventRegistrationRepo,
                eventFieldRepo,
                memberNameResolver,
                wiring.issuers(),
                new EventRestrictionService(eventRepo, restrictionService),
                RequirementSignatures.NONE,
                stationMemberRepo);
        var signatures = new AppointmentSignatures(
                eventRepo,
                eventRegistrationRepo,
                appointments,
                requirementRepo,
                submissions,
                requestRepo,
                requests,
                new DocumentGenerationRepository(),
                new IssuerSignatureRepository(),
                mock(IssuedLetterSigner.class));
        changes = new ChangedRequirementSignatures(
                eventRepo, eventRegistrationRepo, stationRepo, signatures, requestRepo, requests);
        return new DemoPhotoConsentSeeder(
                wiring.templates(),
                eventRegistrationRepo,
                requirements,
                appointments,
                requests,
                requestRepo,
                fields,
                acts,
                stationMemberRepo,
                images,
                new DemoSessions(accountRepo, stationMemberRepo, memberPermissionResolver));
    }

    private static AuthService authService() {
        var hibp = mock(HibpClient.class);
        when(hibp.isPwned(anyString())).thenReturn(false);
        var locales = new MailLocaleService(accountRepo, new ApplicationSettingRepository());
        var mail = mock(EmailService.class);
        return new AuthService(
                accountRepo,
                new AccountEmailService(accountRepo, locales, mail),
                mock(MailConfirmationPolicy.class),
                locales,
                new MailRecipientService(accountRepo, stationMemberRepo),
                registrationCodeRepo,
                stationMemberRepo,
                newGroupMemberships(),
                new PasswordHasher(),
                mail,
                new Auth(),
                new Demo(),
                hibp,
                mock(BreachCheckWorker.class),
                twoFactorRepo,
                new TrustedDeviceService(
                        twoFactorRepo, TokenHasher.forTesting("demo-consent-device"), new TwoFactorSettings()),
                passkeyModeService);
    }

    @Test
    void onlyTheFirstStationsFestivalAsksForTheConsent() {
        assertEquals(1, consents(run.primaryStation()).size());
        for (var station : run.stations().subList(1, run.stations().size())) {
            assertTrue(consents(station).isEmpty(), station.profile().name());
            var festival = station.events().buergerfest().event();
            assertTrue(
                    requirementRepo.forEvent(festival.id()).isEmpty(),
                    station.profile().name());
        }
    }

    @Test
    void theConsentHangsOnTheFestivalTheEventsAlreadyHave() {
        var station = run.primaryStation();

        assertEquals(1, festivals(station).size(), "no second festival is created");
        assertEquals(station.events().buergerfest().event().id(), festival().id());
    }

    @Test
    void seedingAgainLeavesOneOfEach() {
        seeder.seed(run);

        var station = run.primaryStation();
        assertEquals(1, consents(station).size());
        assertEquals(1, festivals(station).size());
        for (var participant : seeder.participants(members())) {
            assertEquals(1, liveRequestsOf(participant.memberId()).size(), "member " + participant.memberId());
        }
    }

    /**
     * Attaching the consent asks whoever already held a place on the festival, and the seeder takes that
     * request on instead of asking a second time, so each participant holds exactly one live request for the
     * consent on the day, and the member left unregistered holds none.
     */
    @Test
    void eachParticipantHoldsOneLiveRequestForTheConsent() {
        var participants = seeder.participants(members());
        for (var participant : participants) {
            assertEquals(1, liveRequestsOf(participant.memberId()).size(), "member " + participant.memberId());
        }
        assertTrue(
                participants.stream()
                        .anyMatch(participant -> liveRequestsOf(participant.memberId())
                                        .getFirst()
                                        .createdBy()
                                == null),
                "someone already registered was asked by attaching the consent");
        assertTrue(liveRequestsOf(DemoPhotoConsentSeeder.unregistered(members()).id())
                .isEmpty());
    }

    @Test
    void eachParticipantHoldsOneRegistrationOnTheFestivalDay() {
        var registrations = eventRegistrationRepo.findByEventAndDate(festival().id(), festivalDay());
        var participants = seeder.participants(members());

        for (var participant : participants) {
            assertEquals(
                    1,
                    registrations.stream()
                            .filter(registration -> registration.memberId() == participant.memberId())
                            .count(),
                    "member " + participant.memberId());
        }
        assertEquals(
                registrations.size(),
                registrations.stream()
                        .map(registration -> registration.memberId())
                        .distinct()
                        .count());
    }

    @Test
    void aYoungMemberWithAGuardianIsLeftToRegister() {
        var ben = DemoPhotoConsentSeeder.unregistered(members());

        assertFalse(stationMemberRepo.findManagers(ben.id()).isEmpty(), "he has a guardian");
        assertFalse(eventRegistrationRepo
                .findRegisteredMemberIds(festival().id(), festivalDay())
                .contains(ben.id()));
        assertFalse(
                seeder.participants(members()).stream().anyMatch(participant -> participant.memberId() == ben.id()));
    }

    @Test
    void theConsentCarriesItsSignersStatementsAndRetention() {
        var template = wiring.templates().detail(wiring.owner(), consentId());

        assertTrue(template.forAppointments(), "an appointment asks for it");
        assertTrue(template.legal(), "a consent is legal");
        assertFalse(template.selfService(), "it is fetched from the appointment");
        assertEquals(new TemplateSigning(DemoPhotoConsentTemplate.RETENTION_MONTHS, true), template.signing());
        var lines = LetterContent.blocks(template.body())
                .filter(cell -> cell.config() instanceof CellConfig.SignatureConfig)
                .toList();
        assertEquals(
                List.of(SignatureRole.PARTICIPANT, SignatureRole.ISSUER, SignatureRole.EACH_GUARDIAN),
                lines.stream().map(DemoPhotoConsentSeederTest::signer).toList());
        assertEquals(
                List.of(
                        DemoPhotoConsentTemplate.PARTICIPANT_STATEMENT,
                        DemoPhotoConsentTemplate.ISSUER_STATEMENT,
                        DemoPhotoConsentTemplate.GUARDIAN_STATEMENT),
                lines.stream()
                        .map(cell -> ((CellConfig.SignatureConfig) cell.config()).statement())
                        .toList());
        var guardianLine = lines.getLast();
        assertEquals(
                List.of(StationUserType.MEMBER),
                Objects.requireNonNull(guardianLine.restriction()).userTypes(),
                "only the young members' guardians sign");
    }

    @Test
    void theFestivalLiesAWeekAheadStaysOpenAndAsksForTheConsent() {
        var festival = festival();
        LocalDate today = clock.of(run.primaryStation().station()).today();
        LocalDate day = festivalDay();

        assertEquals(today.plusWeeks(1), day);
        assertTrue(festival.requiresRegistration());
        assertTrue(
                Objects.requireNonNull(festival.registrationDeadline()).isAfter(Instant.now()),
                "registration is still open");
        assertEquals(
                DemoEventSeeder.BUERGERFEST_PLACE,
                AppointmentField.firstLocation(eventFieldRepo.findByEventOn(festival.id(), day)));
        assertEquals(
                List.of(consentId()),
                requirementRepo.forEvent(festival.id()).stream()
                        .map(required -> required.templateId())
                        .toList());
    }

    @Test
    void lenasConsentIsSignedByEverybodyAndSealed() {
        var lena = members().anfaenger().get(1);
        var request = requestFor(lena);

        assertEquals(RequestState.COMPLETE, request.state());
        var fields = requestRepo.fieldsOf(request.id());
        assertEquals(Set.of("participant", "issuer", "guardian1"), names(fields));
        assertTrue(fields.stream().allMatch(field -> field.state() == FieldState.SIGNED), fields.toString());
        assertEquals(guardianOf(lena).id(), signerOf(fields, "guardian1"));
        assertEquals(warden().id(), signerOf(fields, "issuer"));
        var document = memberDocumentRepo
                .findById(Objects.requireNonNull(request.documentId()))
                .orElseThrow();
        assertTrue(document.sealed(), "each act was sealed into the document");
    }

    @Test
    void sophieSignedHerselfAndTheOthersAreStillAsked() {
        var sophie = members().anfaenger().get(3);
        var fields = requestRepo.fieldsOf(requestFor(sophie).id());

        assertEquals(RequestState.OPEN, requestFor(sophie).state());
        assertEquals(FieldState.SIGNED, stateOf(fields, "participant"));
        assertEquals(FieldState.OPEN, stateOf(fields, "guardian1"));
        assertEquals(FieldState.OPEN, stateOf(fields, "issuer"));
        var guardian = session(guardianOf(sophie));
        assertTrue(
                requests.openFor(guardian).stream()
                        .anyMatch(open -> open.pending().field().fieldName().equals("guardian1")),
                "her guardian finds the consent among his open tasks");
    }

    @Test
    void lukasConsentCameBackOnPaper() {
        var lukas = members().anfaenger().get(2);
        var request = requestFor(lukas);

        assertEquals(RequestState.COMPLETE, request.state());
        assertTrue(requestRepo.fieldsOf(request.id()).stream()
                .allMatch(field -> field.state() == FieldState.PAPER_CONFIRMED));
    }

    @Test
    void miaAndTheCarerAreStillAskedEverything() {
        var mia = members().fortgeschritten().getFirst();
        var carer = members().betreuer().get(2);

        var miasFields = requestRepo.fieldsOf(requestFor(mia).id());
        assertEquals(Set.of("participant", "issuer", "guardian1"), names(miasFields));
        assertTrue(miasFields.stream().allMatch(field -> field.state() == FieldState.OPEN));
        var carersFields = requestRepo.fieldsOf(requestFor(carer).id());
        assertEquals(Set.of("participant", "issuer"), names(carersFields), "an adult signs without a guardian");
        assertTrue(carersFields.stream().allMatch(field -> field.state() == FieldState.OPEN));
        assertEquals(
                DemoPhotoConsentTemplate.PARTICIPANT_STATEMENT,
                carersFields.stream()
                        .filter(field -> field.fieldName().equals("participant"))
                        .findFirst()
                        .orElseThrow()
                        .statement());
        assertEquals(
                DemoPhotoConsentTemplate.RETENTION_MONTHS, requestFor(carer).retentionMonths());
        assertTrue(requestFor(carer).copyAttached());
    }

    @Test
    void theCopyNamesTheFestivalTheParticipantAndTheGuardian() {
        var mia = members().fortgeschritten().getFirst();
        var text = wiring.textOf(Objects.requireNonNull(requestFor(mia).documentId()));

        assertTrue(text.contains(DemoEventSeeder.BUERGERFEST), text);
        assertTrue(text.contains(DemoEventSeeder.BUERGERFEST_PLACE), text);
        assertTrue(text.contains(memberNameResolver.official(mia.id())), text);
        assertTrue(text.contains(memberNameResolver.official(guardianOf(mia).id())), text);
        assertTrue(text.contains("jederzeit mit Wirkung für die Zukunft widerrufen"), text);
        assertTrue(text.contains("freiwillig"), text);
    }

    private static SignatureRole signer(ContentCell cell) {
        return Objects.requireNonNull(((CellConfig.SignatureConfig) cell.config()).signer());
    }

    private static DemoMemberSeeder.SeedResult members() {
        return run.primaryStation().members();
    }

    private static StationMember warden() {
        return members().betreuer().get(DemoDocumentTemplateSeeder.WARDEN_PLACE);
    }

    private static StationMember guardianOf(StationMember child) {
        return stationMemberRepo.findManagers(child.id()).getFirst();
    }

    private static StationSession session(StationMember member) {
        return stationSession(member, StationPermission.LOGIN);
    }

    private static int consentId() {
        return consents(run.primaryStation()).getFirst().id();
    }

    private static List<DocumentTemplateSummary> consents(DemoStationContext station) {
        return wiring.templates().list(new Owner.Station(station.stationId()), false).stream()
                .filter(template -> DemoPhotoConsentTemplate.NAME.equals(template.name()))
                .toList();
    }

    private static List<StationEvent> festivals(DemoStationContext station) {
        return eventRepo.findByStation(station.stationId()).stream()
                .filter(event -> DemoEventSeeder.BUERGERFEST.equals(event.name()))
                .toList();
    }

    private static StationEvent festival() {
        return festivals(run.primaryStation()).getFirst();
    }

    private static LocalDate festivalDay() {
        return run.primaryStation().events().buergerfest().day();
    }

    private static List<SignatureRequest> liveRequestsOf(int memberId) {
        return requestRepo.liveForAppointment(festival().id(), festivalDay(), memberId).stream()
                .filter(asked -> asked.templateId() == consentId())
                .map(AppointmentRequest::request)
                .toList();
    }

    private static SignatureRequest requestFor(StationMember member) {
        var live = liveRequestsOf(member.id());
        assertEquals(1, live.size(), "member " + member.id());
        return live.getFirst();
    }

    private static Set<String> names(List<RequestedSignature> fields) {
        return fields.stream().map(RequestedSignature::fieldName).collect(Collectors.toSet());
    }

    private static FieldState stateOf(List<RequestedSignature> fields, String name) {
        return fields.stream()
                .filter(field -> field.fieldName().equals(name))
                .findFirst()
                .orElseThrow()
                .state();
    }

    private static Integer signerOf(List<RequestedSignature> fields, String name) {
        return fields.stream()
                .filter(field -> field.fieldName().equals(name))
                .findFirst()
                .orElseThrow()
                .signerId();
    }
}

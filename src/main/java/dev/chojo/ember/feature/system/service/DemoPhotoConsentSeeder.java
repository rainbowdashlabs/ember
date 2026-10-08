/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SigningAnswer;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningPicture;
import dev.chojo.ember.feature.signing.service.SignatureFieldService;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.signing.service.SigningActService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The citizens' festival of the demo's first station, a few weeks ahead, and the photo consent it asks every
 * participant to bring, which members and guardians sign online.
 *
 * <p>The festival takes registrations, and five people are registered for it: four young members, each with
 * a guardian, and one carer. Each of them fetched their copy of the consent from the appointment, the
 * guardians for their children, and the station's administrator asked for the signatures on each. From there
 * the copies stand at every step a station sees:
 *
 * <ul>
 *   <li>Lena Berger's is signed by everybody: by Lena, by her guardian Hans Berger and by the youth warden
 *       Anna Schmidt, who takes it in, and sealed with each act;</li>
 *   <li>Sophie Schulze signed her own, and her guardian Klaus Schulze and the youth warden have yet to;</li>
 *   <li>Lukas Frank's came back on paper, and the administrator confirmed each signature on it;</li>
 *   <li>Mia Berger's and that of the carer Thomas Müller wait for everybody.</li>
 * </ul>
 *
 * <p>Everything goes through the services a person's request reaches, in the session of whoever would do it
 * ({@link DemoSessions}): the template is saved as a manager saves one, the copies are fetched from the
 * appointment, signatures are asked for, and every signature is a signing act confirmed with the demo
 * password and a drawn signature. Sealing follows the instance's settings for timestamps like any other act.
 *
 * <p>A station that already has the consent keeps it and everything hung on it, so seeding twice leaves one.
 */
@Singleton
public class DemoPhotoConsentSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoPhotoConsentSeeder.class);

    /** What the appointment is called. */
    static final String FESTIVAL = "Bürgerfest";

    /** Where it takes place, which the consent prints. */
    static final String PLACE = "Marktplatz Musterstadt";

    /** How many weeks ahead of the station's today the festival is held, on the Saturday from then on. */
    static final int WEEKS_AHEAD = 4;

    private static final String DESCRIPTION = """
            Die Jugendfeuerwehr ist mit Löschvorführung, Spritzwand für Kinder und Infostand beim Bürgerfest \
            dabei. Wer mitmacht, bringt die unterschriebene Fotoerlaubnis mit oder unterschreibt sie online.""";

    private static final String CATEGORY = "Öffentlichkeitsarbeit";

    private static final SigningCircumstances DEMO_BROWSER = new SigningCircumstances(null, "Ember demo");

    /** How far one participant's consent has come. */
    enum Progress {
        /** Nobody signed yet. */
        OPEN(field -> false, false),
        /** The participant signed, the others have yet to. */
        PARTICIPANT_SIGNED(field -> field.role() == FieldRole.PARTICIPANT, false),
        /** Everybody signed. */
        SIGNED(field -> true, false),
        /** It came back signed on paper, and every signature on it was confirmed. */
        ON_PAPER(field -> false, true);

        private final Predicate<RequestedSignature> signedOnline;
        private final boolean onPaper;

        /**
         * @param signedOnline which fields are signed online, by whoever each one names
         * @param onPaper      whether every field is confirmed as signed on paper instead
         */
        Progress(Predicate<RequestedSignature> signedOnline, boolean onPaper) {
            this.signedOnline = signedOnline;
            this.onPaper = onPaper;
        }
    }

    /**
     * One person registered for the festival.
     *
     * @param memberId  the participant
     * @param fetchedBy who fetched their copy of the consent: their guardian, or they themselves
     * @param progress  how far their consent has come
     */
    record Participant(int memberId, int fetchedBy, Progress progress) {}

    private final DocumentTemplateService templates;
    private final EventCrudService events;
    private final EventCategoryRepository categories;
    private final EventFieldRepository fields;
    private final EventRegistrationRepository registrations;
    private final EventRequirementService requirements;
    private final AppointmentDocumentService appointments;
    private final SignatureRequestService signatures;
    private final SignatureFieldService signatureFields;
    private final SigningActService acts;
    private final StationMemberRepository stationMembers;
    private final DemoSessions sessions;
    private final DemoClock clock;

    @Inject
    public DemoPhotoConsentSeeder(
            DocumentTemplateService templates,
            EventCrudService events,
            EventCategoryRepository categories,
            EventFieldRepository fields,
            EventRegistrationRepository registrations,
            EventRequirementService requirements,
            AppointmentDocumentService appointments,
            SignatureRequestService signatures,
            SignatureFieldService signatureFields,
            SigningActService acts,
            StationMemberRepository stationMembers,
            DemoSessions sessions,
            DemoClock clock) {
        this.templates = templates;
        this.events = events;
        this.categories = categories;
        this.fields = fields;
        this.registrations = registrations;
        this.requirements = requirements;
        this.appointments = appointments;
        this.signatures = signatures;
        this.signatureFields = signatureFields;
        this.acts = acts;
        this.stationMembers = stationMembers;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seed(DemoRunContext run) {
        var station = run.primaryStation();
        var owner = new Owner.Station(station.stationId());
        boolean present = templates.list(owner, false).stream()
                .anyMatch(template -> DemoPhotoConsentTemplate.NAME.equals(template.name()));
        if (present) return;
        int author = Objects.requireNonNull(
                station.adminMember().accountId(), "the station administrator is seeded with an account");
        var warden = station.members().betreuer().get(DemoDocumentTemplateSeeder.WARDEN_PLACE);
        int consent = templates
                .create(owner, DemoPhotoConsentTemplate.request(warden.id()), author)
                .id();
        LocalDate day = festivalDay(clock.of(station.station()).today());
        var festival = festival(station, day);
        requirements.setForEvent(owner, festival.id(), List.of(consent));
        var manager = sessions.of(station.station(), station.adminMember().id());
        for (var participant : participants(station.members())) {
            registrations.create(festival.id(), participant.memberId(), day, RegistrationStatus.ACCEPTED, null);
            var copy = appointments.generate(
                    sessions.of(station.station(), participant.fetchedBy()),
                    festival,
                    day,
                    consent,
                    participant.memberId());
            var request = signatures.request(manager, copy.generationId());
            settle(station.station(), manager, request, participant.progress());
        }
        log.info("Demo: Created the {} on {} with its photo consent at station {}", FESTIVAL, day, station.stationId());
    }

    /**
     * @param today the station's today
     * @return the day of the festival: the first Saturday {@link #WEEKS_AHEAD} weeks from today on
     */
    static LocalDate festivalDay(LocalDate today) {
        return today.plusWeeks(WEEKS_AHEAD).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    /**
     * The people registered for the festival, each with how far their consent has come.
     *
     * @param members the members the member band seeded at the station
     * @return them in the order they are registered
     */
    List<Participant> participants(DemoMemberSeeder.SeedResult members) {
        var carer = members.betreuer().get(2);
        return List.of(
                child(members.anfaenger().get(1), Progress.SIGNED),
                child(members.anfaenger().get(2), Progress.ON_PAPER),
                child(members.anfaenger().get(3), Progress.PARTICIPANT_SIGNED),
                child(members.fortgeschritten().getFirst(), Progress.OPEN),
                new Participant(carer.id(), carer.id(), Progress.OPEN));
    }

    private Participant child(StationMember child, Progress progress) {
        int guardian = stationMembers.findManagers(child.id()).getFirst().id();
        return new Participant(child.id(), guardian, progress);
    }

    private StationEvent festival(DemoStationContext station, LocalDate day) {
        var days = clock.of(station.station());
        var festival = events.create(
                station.stationId(),
                FESTIVAL,
                DESCRIPTION,
                StationEvent.EventType.ONE_TIME,
                null,
                days.at(day, 11, 0),
                days.at(day, 18, 0),
                null,
                true,
                days.at(day.minusWeeks(1), 23, 59),
                false,
                category(station.stationId()),
                null,
                null,
                null,
                null);
        fields.create(
                festival.id(), "Ort", FieldType.LOCATION, EventQuestionSettings.empty(), PLACE, 0, true, null, true);
        fields.create(
                festival.id(),
                "Treffpunkt",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Feuerwehrgerätehaus 10:00",
                1,
                true,
                null,
                true);
        return festival;
    }

    private @Nullable Integer category(int stationId) {
        return categories.findByStation(stationId).stream()
                .filter(category -> CATEGORY.equals(category.name()))
                .map(EventCategory::id)
                .findFirst()
                .orElse(null);
    }

    /** Brings one participant's request as far as their consent has come. */
    private void settle(Station station, StationSession manager, SignatureRequest request, Progress progress) {
        var asked = signatures.view(manager, request.uid()).fields();
        for (var field : asked) {
            if (progress.onPaper) {
                signatureFields.confirmOnPaper(manager, request.uid(), field.fieldName());
            } else if (progress.signedOnline.test(field)) {
                sign(station, field);
            }
        }
    }

    /**
     * Signs a field as the person it names: started and confirmed with their password, with a signature
     * drawn for the act.
     */
    private void sign(Station station, RequestedSignature field) {
        int signerId = Objects.requireNonNull(field.signerId(), "every field of the consent names its signer");
        var signer = sessions.of(station, signerId);
        var attempt = acts.start(signer, acts.requireOwnedField(signer, field.id()), null);
        acts.complete(
                signer,
                field.id(),
                attempt.startToken(),
                new SigningAnswer(StepUpProof.PASSWORD, null, PASSWORD),
                new SigningPicture(
                        DemoSignatureStroke.of(Objects.requireNonNullElse(field.signerName(), field.fieldName())),
                        SignatureImageSource.DRAWN,
                        false),
                DEMO_BROWSER);
    }
}

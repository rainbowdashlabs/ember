/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.mail.service.MailChainService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.repository.NotificationSettingsRepository;
import dev.chojo.ember.feature.onboarding.entity.OnboardingLevel;
import dev.chojo.ember.feature.onboarding.entity.OnboardingMark;
import dev.chojo.ember.feature.onboarding.entity.OnboardingStatus;
import dev.chojo.ember.feature.onboarding.entity.OnboardingTaskState;
import dev.chojo.ember.feature.onboarding.entity.OnboardingTaskView;
import dev.chojo.ember.feature.onboarding.repository.OnboardingTaskRepository;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.SetupService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What Ember asks of somebody is worked out, not stored. These check that it asks for the right
 * things: nothing behind a switched-off module, nothing that has no content behind it, nothing a
 * guardian cannot do for the child in question, and nothing already done.
 */
class OnboardingServiceTest {

    private static final int STATION = 7;
    private static final int MEMBER = 42;
    private static final int CHILD = 43;

    private OnboardingTaskRepository markRepository;
    private SetupService setupService;
    private TwoFactorRepository twoFactorRepository;
    private StationMemberRepository memberRepository;
    private AccountRepository accountRepository;
    private StationService stationService;
    private ProfileFieldService profileFieldService;
    private NotificationSettingsRepository notificationSettingsRepository;
    private EventRegistrationRepository registrationRepository;
    private FeedTokenRepository feedTokenRepository;
    private QuizCatalogRepository quizCatalogRepository;
    private KnowledgeBaseRepository knowledgeBaseRepository;
    private EventRepository eventRepository;
    private OnboardingService service;

    @BeforeEach
    void setUp() {
        markRepository = mock(OnboardingTaskRepository.class);
        setupService = mock(SetupService.class);
        twoFactorRepository = mock(TwoFactorRepository.class);
        memberRepository = mock(StationMemberRepository.class);
        accountRepository = mock(AccountRepository.class);
        stationService = mock(StationService.class);
        profileFieldService = mock(ProfileFieldService.class);
        notificationSettingsRepository = mock(NotificationSettingsRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        feedTokenRepository = mock(FeedTokenRepository.class);
        quizCatalogRepository = mock(QuizCatalogRepository.class);
        knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        eventRepository = mock(EventRepository.class);

        service = new OnboardingService(
                markRepository,
                memberRepository,
                accountRepository,
                stationService,
                setupService,
                profileFieldService,
                notificationSettingsRepository,
                registrationRepository,
                feedTokenRepository,
                twoFactorRepository,
                mock(ConsentService.class),
                mock(MailChainService.class),
                mock(StationRepository.class),
                quizCatalogRepository,
                knowledgeBaseRepository,
                eventRepository);

        when(markRepository.findByMember(anyInt())).thenReturn(List.of());
        when(stationService.findEffectiveDisabledModules(STATION)).thenReturn(Set.of());
        when(registrationRepository.findByMember(anyInt())).thenReturn(List.of());
        when(notificationSettingsRepository.findByMember(anyInt())).thenReturn(List.of());
        when(feedTokenRepository.findByMember(anyInt())).thenReturn(Optional.empty());
        when(eventRepository.existsForStation(STATION)).thenReturn(true);
        when(knowledgeBaseRepository.existsForStation(STATION)).thenReturn(true);
        when(quizCatalogRepository.hasTrainableCatalog(STATION)).thenReturn(true);
    }

    @Test
    void aMemberIsAskedTheThingsTheirStationOffers() {
        var tasks = keysOf(service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER));

        assertTrue(tasks.contains("member.profile"));
        assertTrue(tasks.contains("member.eventAnswer"));
        assertTrue(tasks.contains("member.quiz"));
    }

    @Test
    void aSwitchedOffModuleIsNeverAskedAbout() {
        when(stationService.findEffectiveDisabledModules(STATION)).thenReturn(EnumSet.of(StationModule.QUIZ));

        var tasks = keysOf(service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER));

        assertFalse(tasks.contains("member.quiz"));
        assertTrue(tasks.contains("member.profile"));
    }

    @Test
    void aQuizNobodyCanTrainOnIsNotAskedFor() {
        when(quizCatalogRepository.hasTrainableCatalog(STATION)).thenReturn(false);

        assertFalse(keysOf(service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER))
                .contains("member.quiz"));
    }

    @Test
    void anEmptyWikiAndAnEmptyCalendarAreNotAskedAbout() {
        when(knowledgeBaseRepository.existsForStation(STATION)).thenReturn(false);
        when(eventRepository.existsForStation(STATION)).thenReturn(false);

        var tasks = keysOf(service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER));

        assertFalse(tasks.contains("member.wiki"));
        assertFalse(tasks.contains("member.eventAnswer"));
        assertFalse(tasks.contains("member.calendar"));
    }

    @Test
    void aTaskIsDoneBecauseTheThingItAsksForExists() {
        // The notification settings, because those really are answered by the data. The profile stood
        // here until looking over what is written about you became the task, and no row can answer
        // that for anybody.
        when(notificationSettingsRepository.findByMember(eq(MEMBER)))
                .thenReturn(
                        List.of(new NotificationSetting(MEMBER, NotificationType.EVENT_REMINDER, true, false, false)));

        assertEquals(
                OnboardingTaskState.DONE,
                stateOf(
                        service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER),
                        "member.notifications"));
    }

    @Test
    void aCalendarCountsOnlyOnceAnAppHasFetchedIt() {
        when(feedTokenRepository.findByMember(MEMBER))
                .thenReturn(Optional.of(new FeedToken(MEMBER, "token", Instant.now(), null, null)));

        assertEquals(
                OnboardingTaskState.OPEN,
                stateOf(
                        service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER),
                        "member.calendar"));

        when(feedTokenRepository.findByMember(MEMBER))
                .thenReturn(Optional.of(new FeedToken(MEMBER, "token", Instant.now(), Instant.now(), null)));

        assertEquals(
                OnboardingTaskState.DONE,
                stateOf(
                        service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER),
                        "member.calendar"));
    }

    @Test
    void whatSomebodyPassedOverStaysPassedOver() {
        when(markRepository.findByMember(MEMBER))
                .thenReturn(List.of(new OnboardingMark("member.absence", "SKIPPED", Instant.now(), null)));

        assertEquals(
                OnboardingTaskState.SKIPPED,
                stateOf(
                        service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER),
                        "member.absence"));
    }

    @Test
    void whatSomebodyThrewAwayIsNotListedAgain() {
        when(markRepository.findByMember(MEMBER))
                .thenReturn(List.of(new OnboardingMark("member.absence", "DISMISSED", Instant.now(), null)));

        var status = service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER);

        assertFalse(keysOf(status).contains("member.absence"));
        assertTrue(keysOf(status).contains("member.profile"));
    }

    /**
     * A derived task reads its answer from the data, so throwing it away has to beat that answer.
     * Otherwise the one task somebody wanted rid of would come back the moment it became true.
     */
    @Test
    void aThrownAwayTaskStaysGoneEvenOnceItsAnswerIsThere() {
        when(markRepository.findByMember(MEMBER))
                .thenReturn(List.of(new OnboardingMark("member.calendar", "DISMISSED", Instant.now(), null)));
        when(feedTokenRepository.findByMember(MEMBER))
                .thenReturn(Optional.of(new FeedToken(MEMBER, "token", Instant.now(), Instant.now(), null)));

        assertFalse(keysOf(service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER))
                .contains("member.calendar"));
    }

    @Test
    void aTaskThatReadsItsOwnAnswerCannotBeTickedOff() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.mark(
                        OnboardingLevel.MEMBER, "member.notifications", OnboardingTaskState.DONE, MEMBER, 1));
        verify(markRepository, never()).markForMember(anyInt(), eq("member.notifications"), eq("CONFIRMED"));
    }

    @Test
    void lookingOverTheProfileIsSettledByWalkingIt() {
        // A complete profile is exactly the one worth looking over, so the data must not settle the
        // task and hide it from the people it is for.
        when(profileFieldService.isProfileComplete(eq(MEMBER))).thenReturn(true);

        assertEquals(
                OnboardingTaskState.OPEN,
                stateOf(
                        service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER),
                        "member.profile"));
        assertDoesNotThrow(
                () -> service.mark(OnboardingLevel.MEMBER, "member.profile", OnboardingTaskState.DONE, MEMBER, 1));
    }

    @Test
    void aTaskEmberCannotSeeIsTickedOffByHand() {
        service.mark(OnboardingLevel.MEMBER, "member.bookmark", OnboardingTaskState.DONE, MEMBER, 1);

        verify(markRepository).markForMember(MEMBER, "member.bookmark", "CONFIRMED");
    }

    @Test
    void takingATaskUpAgainDropsWhatWasSaidAboutIt() {
        service.mark(OnboardingLevel.MEMBER, "member.absence", OnboardingTaskState.OPEN, MEMBER, 1);

        verify(markRepository).clearForMember(MEMBER, "member.absence");
    }

    @Test
    void aGuardianIsAskedOncePerChild() {
        when(memberRepository.findManaged(MEMBER)).thenReturn(List.of(member(CHILD, StationUserType.MEMBER)));
        when(accountRepository.findById(CHILD)).thenReturn(Optional.of(account(CHILD, "child-1@managed.local")));

        var tasks = service.forMember(member(MEMBER, StationUserType.GUARDIAN), StationUserType.GUARDIAN);
        var username = tasks.tasks().stream()
                .filter(task -> task.key().equals("guardian.username"))
                .findFirst()
                .orElseThrow();

        assertEquals("guardian.username:" + CHILD, username.id());
        assertEquals(CHILD, username.subjectId());
        assertEquals("Kind", username.subject());
    }

    @Test
    void aChildWithAnAddressOfTheirOwnNeedsNoNameAndNoPassword() {
        when(memberRepository.findManaged(MEMBER)).thenReturn(List.of(member(CHILD, StationUserType.MEMBER)));
        when(accountRepository.findById(CHILD)).thenReturn(Optional.of(account(CHILD, "kind@example.org")));

        var tasks = keysOf(service.forMember(member(MEMBER, StationUserType.GUARDIAN), StationUserType.GUARDIAN));

        assertFalse(tasks.contains("guardian.username"));
        assertFalse(tasks.contains("guardian.password"));
        assertTrue(tasks.contains("guardian.login"));
    }

    @Test
    void aChildWhoMaySignInHasThatTaskBehindThem() {
        when(memberRepository.findManaged(MEMBER)).thenReturn(List.of(member(CHILD, StationUserType.MEMBER)));
        when(accountRepository.findById(CHILD)).thenReturn(Optional.of(account(CHILD, "child-1@managed.local")));
        when(memberRepository.hasPermission(CHILD, StationPermission.LOGIN)).thenReturn(true);

        assertEquals(
                OnboardingTaskState.DONE,
                stateOf(
                        service.forMember(member(MEMBER, StationUserType.GUARDIAN), StationUserType.GUARDIAN),
                        "guardian.login"));
    }

    @Test
    void aGuardianIsNotAskedToTrain() {
        var tasks = keysOf(service.forMember(member(MEMBER, StationUserType.GUARDIAN), StationUserType.GUARDIAN));

        assertFalse(tasks.contains("member.quiz"));
        assertTrue(tasks.contains("member.profile"));
    }

    @Test
    void whatIsNotAGuardiansBusinessIsNotOnTheirList() {
        var tasks = keysOf(service.forMember(member(MEMBER, StationUserType.MEMBER), StationUserType.MEMBER));

        assertFalse(tasks.contains("guardian.username"));
    }

    @Test
    void theStationReadsItsTasksFromTheSetupChecklistRatherThanASecondSource() {
        when(setupService.getStatus(STATION))
                .thenReturn(new SetupService.SetupStatus(
                        null,
                        List.of(),
                        List.of(
                                new SetupService.StepState(SetupService.STEP_GROUPS, true, true),
                                new SetupService.StepState(SetupService.STEP_MAIL, false, true),
                                new SetupService.StepState(SetupService.STEP_KB_SEED, false, false))));

        var status = service.forStation(STATION);

        assertEquals(OnboardingTaskState.DONE, stateOf(status, "station.groups"));
        assertEquals(OnboardingTaskState.OPEN, stateOf(status, "station.mail"));
        assertEquals(OnboardingTaskState.OPEN, stateOf(status, "station.kbSeed"));
    }

    @Test
    void aStationTaskNamesWhoSettledIt() {
        when(setupService.getStatus(STATION)).thenReturn(new SetupService.SetupStatus(null, List.of(), List.of()));
        when(markRepository.findByStation(STATION))
                .thenReturn(List.of(new OnboardingMark("station.memberTypes", "SKIPPED", Instant.now(), MEMBER)));
        when(memberRepository.findById(MEMBER)).thenReturn(Optional.of(member(MEMBER, StationUserType.MANAGER)));
        when(accountRepository.findById(MEMBER)).thenReturn(Optional.of(account(MEMBER, "manager@example.org")));

        var settled = service.forStation(STATION).tasks().stream()
                .filter(task -> task.key().equals("station.memberTypes"))
                .findFirst()
                .orElseThrow();

        assertEquals(OnboardingTaskState.SKIPPED, settled.state());
        assertEquals("Kind Sommer", settled.actorName());
    }

    @Test
    void aStationTaskIsSettledForEverybodyWhoManagesIt() {
        when(memberRepository.findById(MEMBER)).thenReturn(Optional.of(member(MEMBER, StationUserType.MANAGER)));

        service.mark(OnboardingLevel.STATION, "station.memberTypes", OnboardingTaskState.SKIPPED, MEMBER, 1);

        verify(markRepository).markForStation(STATION, "station.memberTypes", "SKIPPED", MEMBER);
    }

    @Test
    void theInstanceAsksAboutTheReadersOwnAccountFirst() {
        var status = service.forInstance(account(1, "admin@example.org"));

        assertEquals("instance.ownAccount", status.tasks().getFirst().key());
    }

    @Test
    void anAdministratorWithAnAddressAndASecondFactorIsDoneWithTheirAccount() {
        var admin = account(1, "admin@example.org");
        when(twoFactorRepository.isEnrolled(admin.id())).thenReturn(true);

        assertEquals(OnboardingTaskState.DONE, stateOf(service.forInstance(admin), "instance.ownAccount"));
    }

    @Test
    void anInstanceTaskIsSettledForEveryAdministrator() {
        service.mark(OnboardingLevel.INSTANCE, "instance.security", OnboardingTaskState.DONE, 0, 5);

        verify(markRepository).markForInstance("instance.security", "CONFIRMED", 5);
    }

    @Test
    void aTaskNobodyHasHeardOfIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.mark(OnboardingLevel.MEMBER, "member.nonsense", OnboardingTaskState.SKIPPED, MEMBER, 1));
    }

    @Test
    void aTaskOfAnotherLevelIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.mark(OnboardingLevel.MEMBER, "station.groups", OnboardingTaskState.SKIPPED, MEMBER, 1));
    }

    private static List<String> keysOf(OnboardingStatus status) {
        return status.tasks().stream().map(OnboardingTaskView::key).toList();
    }

    private static OnboardingTaskState stateOf(OnboardingStatus status, String key) {
        return status.tasks().stream()
                .filter(task -> task.key().equals(key))
                .map(OnboardingTaskView::state)
                .findFirst()
                .orElseThrow();
    }

    private static StationMember member(int id, StationUserType type) {
        return new StationMember(id, STATION, UUID.randomUUID(), id, false, null, "Name", type, null);
    }

    private static Account account(int id, String email) {
        return new Account(
                id,
                UUID.randomUUID(),
                email,
                null,
                "Kind",
                "Sommer",
                true,
                InstanceUserType.USER,
                "Kind Sommer",
                null,
                null);
    }
}

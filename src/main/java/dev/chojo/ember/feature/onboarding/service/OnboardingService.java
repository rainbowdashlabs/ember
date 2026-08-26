/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.mail.service.MailChainService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.notifications.repository.NotificationSettingsRepository;
import dev.chojo.ember.feature.onboarding.entity.OnboardingLevel;
import dev.chojo.ember.feature.onboarding.entity.OnboardingMark;
import dev.chojo.ember.feature.onboarding.entity.OnboardingStatus;
import dev.chojo.ember.feature.onboarding.entity.OnboardingTask;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What is still to do, worked out fresh on every read.
 *
 * <p>Nothing about completion is stored. A task is finished because the thing it asks for exists,
 * which is what lets a task come back when somebody undoes it, and what keeps this in step with the
 * station setup checklist rather than beside it: the station's tasks are read from the same service
 * the checklist uses, so there is one answer to what is set up and not two.
 *
 * <p>The two things that cannot be worked out are what somebody ticked off by hand and what somebody
 * passed over. Those are stored, and on the station and the instance they are shared: what one
 * manager ticks off is ticked off for the next one.
 */
@Singleton
public class OnboardingService {
    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final OnboardingTaskRepository markRepository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final StationService stationService;
    private final SetupService setupService;
    private final ProfileFieldService profileFieldService;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final EventRegistrationRepository registrationRepository;
    private final FeedTokenRepository feedTokenRepository;
    private final TwoFactorRepository twoFactorRepository;
    private final ConsentService consentService;
    private final MailChainService mailChainService;
    private final StationRepository stationRepository;
    private final QuizCatalogRepository quizCatalogRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final EventRepository eventRepository;

    @Inject
    public OnboardingService(
            OnboardingTaskRepository markRepository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            StationService stationService,
            SetupService setupService,
            ProfileFieldService profileFieldService,
            NotificationSettingsRepository notificationSettingsRepository,
            EventRegistrationRepository registrationRepository,
            FeedTokenRepository feedTokenRepository,
            TwoFactorRepository twoFactorRepository,
            ConsentService consentService,
            MailChainService mailChainService,
            StationRepository stationRepository,
            QuizCatalogRepository quizCatalogRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            EventRepository eventRepository) {
        this.markRepository = markRepository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.stationService = stationService;
        this.setupService = setupService;
        this.profileFieldService = profileFieldService;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.registrationRepository = registrationRepository;
        this.feedTokenRepository = feedTokenRepository;
        this.twoFactorRepository = twoFactorRepository;
        this.consentService = consentService;
        this.mailChainService = mailChainService;
        this.stationRepository = stationRepository;
        this.quizCatalogRepository = quizCatalogRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * The tasks of one member: their own, plus one chain for every member in their care.
     */
    public OnboardingStatus forMember(StationMember member, StationUserType userType) {
        Map<String, OnboardingMark> marks = byKey(markRepository.findByMember(member.id()));
        Set<StationModule> disabled = stationService.findEffectiveDisabledModules(member.stationId());
        List<StationMember> managed =
                userType == StationUserType.GUARDIAN ? memberRepository.findManaged(member.id()) : List.of();

        List<OnboardingTaskView> views = new ArrayList<>();
        for (OnboardingTask task : OnboardingTask.of(OnboardingLevel.MEMBER)) {
            if (!applies(task, userType, disabled)) continue;
            if (!worthAsking(task, member.stationId())) continue;
            if (!task.perManagedMember()) {
                views.add(view(task, null, null, marks, () -> memberTaskDone(task, member, userType)));
                continue;
            }
            for (StationMember child : managed) {
                Account childAccount = account(child);
                if (childAccount == null) continue;
                if (needsOwnAddressAbsent(task) && childAccount.hasRealEmail()) continue;
                views.add(view(
                        task,
                        childAccount.firstName(),
                        child.id(),
                        marks,
                        () -> guardianTaskDone(task, child, childAccount)));
            }
        }
        return OnboardingStatus.of(OnboardingLevel.MEMBER, views);
    }

    /**
     * The tasks of one station, shared by everyone who manages it.
     */
    public OnboardingStatus forStation(int stationId) {
        Map<String, OnboardingMark> marks = byKey(markRepository.findByStation(stationId));
        Map<String, Boolean> steps = new HashMap<>();
        var status = setupService.getStatus(stationId);
        for (var step : status.optionalSteps()) {
            steps.put(step.id(), step.complete() && step.applicable());
        }
        Set<StationModule> disabled = stationService.findEffectiveDisabledModules(stationId);

        List<OnboardingTaskView> views = new ArrayList<>();
        for (OnboardingTask task : OnboardingTask.of(OnboardingLevel.STATION)) {
            if (task.module().filter(disabled::contains).isPresent()) continue;
            views.add(view(task, null, null, marks, () -> steps.getOrDefault(setupStepOf(task), false)));
        }
        return OnboardingStatus.of(OnboardingLevel.STATION, views);
    }

    /**
     * The tasks of the instance, shared by every administrator. The first one is about the reader's
     * own account, which is the only part of this list that differs between two administrators.
     */
    public OnboardingStatus forInstance(Account account) {
        Map<String, OnboardingMark> marks = byKey(markRepository.findForInstance());
        List<OnboardingTaskView> views = new ArrayList<>();
        for (OnboardingTask task : OnboardingTask.of(OnboardingLevel.INSTANCE)) {
            views.add(view(task, null, null, marks, () -> instanceTaskDone(task, account)));
        }
        return OnboardingStatus.of(OnboardingLevel.INSTANCE, views);
    }

    /**
     * Records that somebody ticked a task off, passed it over, or took it up again. A derived task
     * refuses to be ticked off, because its answer is not anybody's to declare.
     */
    public void mark(OnboardingLevel level, String taskId, OnboardingTaskState state, int memberId, int accountId) {
        OnboardingTask task = OnboardingTask.byKey(keyOf(taskId))
                .filter(candidate -> candidate.level() == level)
                .orElseThrow(() -> new BadRequestResponse("Unknown onboarding task"));
        if (state == OnboardingTaskState.DONE && task.derived()) {
            throw new BadRequestResponse("This task finishes itself once it is actually done");
        }
        String stored =
                switch (state) {
                    case DONE -> "CONFIRMED";
                    case DISMISSED -> "DISMISSED";
                    case OPEN, SKIPPED -> "SKIPPED";
                };
        switch (level) {
            case MEMBER -> {
                if (state == OnboardingTaskState.OPEN) markRepository.clearForMember(memberId, taskId);
                else markRepository.markForMember(memberId, taskId, stored);
            }
            case STATION -> {
                int stationId = memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BadRequestResponse("Unknown member"))
                        .stationId();
                if (state == OnboardingTaskState.OPEN) markRepository.clearForStation(stationId, taskId);
                else markRepository.markForStation(stationId, taskId, stored, memberId);
            }
            case INSTANCE -> {
                if (state == OnboardingTaskState.OPEN) markRepository.clearForInstance(taskId);
                else markRepository.markForInstance(taskId, stored, accountId);
            }
        }
        log.debug("Onboarding task {} at {} level set to {} by member {}", taskId, level, state, memberId);
    }

    private OnboardingTaskView view(
            OnboardingTask task,
            String subject,
            Integer subjectId,
            Map<String, OnboardingMark> marks,
            DerivedCheck derived) {
        String id = subjectId == null ? task.key() : task.key() + ":" + subjectId;
        OnboardingMark mark = marks.get(id);
        OnboardingTaskState state;
        if (mark != null && mark.dismissed()) {
            // Thrown away for good, and so not asked about again, whatever the data would now say.
            state = OnboardingTaskState.DISMISSED;
        } else if (task.derived()) {
            state = derived.done()
                    ? OnboardingTaskState.DONE
                    : mark != null && mark.skipped() ? OnboardingTaskState.SKIPPED : OnboardingTaskState.OPEN;
        } else if (mark == null) {
            state = OnboardingTaskState.OPEN;
        } else {
            state = mark.confirmed() ? OnboardingTaskState.DONE : OnboardingTaskState.SKIPPED;
        }
        return new OnboardingTaskView(
                id,
                task.key(),
                subject,
                subjectId,
                state,
                !task.derived(),
                mark == null ? null : actorName(mark, task.level()),
                mark == null ? null : mark.changedAt());
    }

    private boolean memberTaskDone(OnboardingTask task, StationMember member, StationUserType userType) {
        return switch (task) {
            case PROFILE_FIELDS ->
                profileFieldService.isProfileComplete(member.id(), member.stationId(), List.of(userType.name()));
            case NOTIFICATIONS ->
                !notificationSettingsRepository.findByMember(member.id()).isEmpty();
            case EVENT_ANSWER ->
                !registrationRepository.findByMember(member.id()).isEmpty();
            case CALENDAR_FEED ->
                feedTokenRepository
                        .findByMember(member.id())
                        .filter(token -> token.icalPolledAt() != null)
                        .isPresent();
            default -> false;
        };
    }

    private boolean guardianTaskDone(OnboardingTask task, StationMember child, Account childAccount) {
        return switch (task) {
            case GUARDIAN_USERNAME -> childAccount.username() != null;
            case GUARDIAN_LOGIN -> memberRepository.hasPermission(child.id(), StationPermission.LOGIN);
            case GUARDIAN_PASSWORD ->
                accountRepository.findCredential(childAccount.id()).isPresent();
            case GUARDIAN_EVENT_ANSWER ->
                !registrationRepository.findByMember(child.id()).isEmpty();
            default -> false;
        };
    }

    private boolean instanceTaskDone(OnboardingTask task, Account account) {
        return switch (task) {
            case INSTANCE_OWN_ACCOUNT -> account.hasRealEmail() && twoFactorRepository.isEnrolled(account.id());
            case INSTANCE_LEGAL -> consentService.hasOwnLegalTexts();
            case INSTANCE_MAIL -> !mailChainService.forInstance().isEmpty();
            case INSTANCE_FIRST_STATION ->
                stationRepository.findAllRegular().stream()
                        .anyMatch(station -> memberRepository.findByStation(station.id()).stream()
                                .anyMatch(member -> member.userType() == StationUserType.MANAGER));
            default -> false;
        };
    }

    /**
     * Which step of the setup checklist answers a station task, so both read the same fact.
     */
    private static String setupStepOf(OnboardingTask task) {
        return switch (task) {
            case STATION_GROUPS -> SetupService.STEP_GROUPS;
            case STATION_MAIL -> SetupService.STEP_MAIL;
            case STATION_BRANDING -> SetupService.STEP_BRANDING;
            case STATION_FIRST_EVENT -> SetupService.STEP_FIRST_EVENT;
            case STATION_KB_SEED -> SetupService.STEP_KB_SEED;
            case STATION_FEDERATION -> SetupService.STEP_FEDERATION;
            case STATION_INVITES -> SetupService.STEP_INVITES;
            default -> "";
        };
    }

    /**
     * Whether there is anything behind the task yet. A module being switched on does not mean it
     * holds anything: a station with the quiz enabled but no catalogue to train on would otherwise
     * send its members to an empty page and call it a task.
     */
    private boolean worthAsking(OnboardingTask task, int stationId) {
        return switch (task) {
            case QUIZ -> quizCatalogRepository.hasTrainableCatalog(stationId);
            case WIKI -> knowledgeBaseRepository.existsForStation(stationId);
            case EVENT_ANSWER, CALENDAR_FEED, GUARDIAN_EVENT_ANSWER -> eventRepository.existsForStation(stationId);
            default -> true;
        };
    }

    private static boolean applies(OnboardingTask task, StationUserType userType, Set<StationModule> disabled) {
        if (task.module().filter(disabled::contains).isPresent()) return false;
        return task.reaches(userType);
    }

    /**
     * Whether a task only makes sense while the member it is about has no address of their own.
     * Those two set a password and a name to sign in with, both of which a member with an address
     * handles themselves.
     */
    private static boolean needsOwnAddressAbsent(OnboardingTask task) {
        return task == OnboardingTask.GUARDIAN_USERNAME || task == OnboardingTask.GUARDIAN_PASSWORD;
    }

    private Account account(StationMember member) {
        if (member.accountId() == null) return null;
        return accountRepository.findById(member.accountId()).orElse(null);
    }

    private String actorName(OnboardingMark mark, OnboardingLevel level) {
        if (mark.actorId() == null) return null;
        return switch (level) {
            case STATION ->
                memberRepository
                        .findById(mark.actorId())
                        .map(this::account)
                        .map(Account::fullName)
                        .orElse(null);
            case INSTANCE ->
                accountRepository
                        .findById(mark.actorId())
                        .map(Account::fullName)
                        .orElse(null);
            case MEMBER -> null;
        };
    }

    private static Map<String, OnboardingMark> byKey(List<OnboardingMark> marks) {
        Map<String, OnboardingMark> byKey = new HashMap<>();
        for (OnboardingMark mark : marks) byKey.put(mark.taskKey(), mark);
        return byKey;
    }

    private static String keyOf(String taskId) {
        int separator = taskId.indexOf(':');
        return separator < 0 ? taskId : taskId.substring(0, separator);
    }

    @FunctionalInterface
    private interface DerivedCheck {
        boolean done();
    }
}

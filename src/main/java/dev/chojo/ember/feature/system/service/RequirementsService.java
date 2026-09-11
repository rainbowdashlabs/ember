/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The work a member still owes the station.
 *
 * <p>Two different questions are answered here and they must not be confused. What blocks the
 * landing after signing in is a forced form, a forced quiz or an incomplete profile: each is
 * something the station needs before the member does anything else. A self-check due in four weeks
 * is not that, and neither is a registration short of an answer to a question its appointment
 * gained later. Both count towards the badge and are listed so a screen can offer them, and the
 * landing lets the member past them.
 */
@Singleton
public class RequirementsService {
    private final FormService formService;
    private final QuizService quizService;
    private final ProfileFieldService profileFieldService;
    private final SelfCheckService selfCheckService;
    private final EventRegistrationService registrationService;
    private final StationMemberService stationMemberService;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public RequirementsService(
            FormService formService,
            QuizService quizService,
            ProfileFieldService profileFieldService,
            SelfCheckService selfCheckService,
            EventRegistrationService registrationService,
            StationMemberService stationMemberService,
            MemberNameResolver memberNameResolver) {
        this.formService = formService;
        this.quizService = quizService;
        this.profileFieldService = profileFieldService;
        this.selfCheckService = selfCheckService;
        this.registrationService = registrationService;
        this.stationMemberService = stationMemberService;
        this.memberNameResolver = memberNameResolver;
    }

    public RequirementsResponse getRequirements(int memberId, int stationId, List<String> roleNames) {
        var forcedForms = formService.findForcedPending(stationId, memberId);
        var forcedQuizzes = quizService.findForcedPending(stationId, memberId);
        boolean profileIncomplete = !profileFieldService.isProfileComplete(memberId);
        return new RequirementsResponse(
                forcedForms,
                forcedQuizzes,
                profileIncomplete,
                selfChecks(memberId, roleNames),
                registrationUpdates(memberId, guardian(roleNames)));
    }

    public int countPending(int memberId, int stationId, List<String> roleNames) {
        int count = 0;
        count += formService.findForcedPending(stationId, memberId).size();
        count += quizService.findForcedPending(stationId, memberId).size();
        if (!profileFieldService.isProfileComplete(memberId)) count++;
        count += selfCheckService.countOutstandingFor(memberId, guardian(roleNames));
        count += registrationUpdates(memberId, guardian(roleNames)).size();
        return count;
    }

    /**
     * The registrations the reader still owes an answer, their own and those of everyone in their
     * care.
     *
     * <p>Whose each one is rides along only where it is not the reader's own, which is what lets a
     * guardian tell their children apart without the screen naming the reader to themselves.
     */
    private List<RegistrationUpdateItem> registrationUpdates(int memberId, boolean guardian) {
        var household = new ArrayList<Integer>();
        household.add(memberId);
        if (guardian) {
            for (var managed : stationMemberService.findManaged(memberId)) {
                household.add(managed.id());
            }
        }
        return registrationService.findShortOfAnswer(household).stream()
                .map(entry -> new RegistrationUpdateItem(
                        entry.registrationId(),
                        entry.eventId(),
                        entry.eventName(),
                        entry.eventDate(),
                        entry.memberId(),
                        entry.memberId() == memberId ? null : memberNameResolver.resolveLocal(entry.memberId())))
                .toList();
    }

    private List<SelfCheckItem> selfChecks(int memberId, List<String> roleNames) {
        return selfCheckService.outstandingFor(memberId, guardian(roleNames)).stream()
                .map(task -> new SelfCheckItem(task.id(), task.memberId(), task.dueOn()))
                .toList();
    }

    private static boolean guardian(List<String> roleNames) {
        return roleNames != null && roleNames.contains(StationPermission.MEMBER_GUARDIAN.name());
    }

    public record RequirementItem(int id, String title) {}

    /**
     * One self-check the reader is answerable for, their own or one held for a member in their care.
     *
     * @param memberId whose gear it is about, which is what tells a guardian's several apart
     * @param dueOn    the day the answer is wanted by, or {@code null} where none was named
     */
    public record SelfCheckItem(int id, int memberId, LocalDate dueOn) {}

    /**
     * One registration short of an answer to a question its appointment gained after the sign-up.
     *
     * @param memberName whose registration it is, named only where it is not the reader's own, so
     *                   a guardian can tell the members in their care apart
     */
    public record RegistrationUpdateItem(
            int registrationId, int eventId, String eventName, LocalDate eventDate, int memberId, String memberName) {}

    /**
     * What a member still owes.
     *
     * @param selfChecks          the self-checks they are answerable for. Unlike the three fields
     *                            above, this one does not stand in the doorway: it is here to be
     *                            listed and counted, never to hold the reader on the landing.
     * @param registrationUpdates the registrations still owing an answer to a question added after
     *                            the sign-up. Listed and counted like the self-checks, and just as
     *                            unable to hold the reader on the landing.
     */
    public record RequirementsResponse(
            List<RequirementItem> forcedForms,
            List<RequirementItem> forcedQuizzes,
            boolean profileIncomplete,
            List<SelfCheckItem> selfChecks,
            List<RegistrationUpdateItem> registrationUpdates) {}
}

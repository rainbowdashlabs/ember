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
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The work a member still owes the station.
 *
 * <p>Two different questions are answered here and they must not be confused. What blocks the
 * landing after signing in is a forced form, a forced quiz or an incomplete profile: each is
 * something the station needs before the member does anything else. A self-check due in four weeks
 * is not that, and neither is a registration short of an answer to a question its appointment
 * gained later, and neither is a document waiting for a signature. All three count towards the badge
 * and are listed so a screen can offer them, and the landing lets the member past them.
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
    private final SignatureRequestService signatureRequests;

    @Inject
    public RequirementsService(
            FormService formService,
            QuizService quizService,
            ProfileFieldService profileFieldService,
            SelfCheckService selfCheckService,
            EventRegistrationService registrationService,
            StationMemberService stationMemberService,
            MemberNameResolver memberNameResolver,
            SignatureRequestService signatureRequests) {
        this.formService = formService;
        this.quizService = quizService;
        this.profileFieldService = profileFieldService;
        this.selfCheckService = selfCheckService;
        this.registrationService = registrationService;
        this.stationMemberService = stationMemberService;
        this.memberNameResolver = memberNameResolver;
        this.signatureRequests = signatureRequests;
    }

    public RequirementsResponse getRequirements(int memberId, int stationId, List<String> roleNames) {
        var forcedForms = formService.findForcedPending(stationId, household(memberId, guardian(roleNames)));
        var forcedQuizzes = quizService.findForcedPending(stationId, memberId);
        boolean profileIncomplete = !profileFieldService.isProfileComplete(memberId);
        return new RequirementsResponse(
                forcedForms,
                forcedQuizzes,
                profileIncomplete,
                selfChecks(memberId, roleNames),
                registrationUpdates(memberId, guardian(roleNames)),
                pendingSignatures(memberId, stationId));
    }

    public int countPending(int memberId, int stationId, List<String> roleNames) {
        int count = 0;
        count += formService
                .findForcedPending(stationId, household(memberId, guardian(roleNames)))
                .size();
        count += quizService.findForcedPending(stationId, memberId).size();
        if (!profileFieldService.isProfileComplete(memberId)) count++;
        count += selfCheckService.countOutstandingFor(memberId, guardian(roleNames));
        count += registrationUpdates(memberId, guardian(roleNames)).size();
        count += signatureRequests.pendingFor(stationId, memberId).size();
        return count;
    }

    /**
     * The signature fields waiting for the reader, their own and those they sign for or lend their
     * account to the members in their care.
     *
     * <p>Whose document it is rides along only where it is not the reader's own, as for the
     * registrations.
     */
    private List<SignatureItem> pendingSignatures(int memberId, int stationId) {
        return signatureRequests.pendingFor(stationId, memberId).stream()
                .map(pending -> new SignatureItem(
                        pending.field().id(),
                        pending.documentTitle(),
                        Objects.equals(pending.field().memberId(), memberId) ? null : pending.memberName()))
                .toList();
    }

    /**
     * The registrations the reader still owes an answer, their own and those of everyone in their
     * care.
     *
     * <p>Whose each one is rides along only where it is not the reader's own, which is what lets a
     * guardian tell their children apart without the screen naming the reader to themselves.
     */
    private List<RegistrationUpdateItem> registrationUpdates(int memberId, boolean guardian) {
        return registrationService.findShortOfAnswer(household(memberId, guardian)).stream()
                .map(entry -> new RegistrationUpdateItem(
                        entry.registrationId(),
                        entry.eventId(),
                        entry.eventName(),
                        entry.eventDate(),
                        entry.memberId(),
                        entry.memberId() == memberId ? null : memberNameResolver.called(entry.memberId())))
                .toList();
    }

    private List<SelfCheckItem> selfChecks(int memberId, List<String> roleNames) {
        return selfCheckService.outstandingFor(memberId, guardian(roleNames)).stream()
                .map(task -> new SelfCheckItem(task.id(), task.memberId(), task.dueOn()))
                .toList();
    }

    /** The reader, and for a guardian everybody in their care, whose answers the reader gives. */
    private List<Integer> household(int memberId, boolean guardian) {
        var household = new ArrayList<Integer>();
        household.add(memberId);
        if (guardian) {
            for (var managed : stationMemberService.findManaged(memberId)) {
                household.add(managed.id());
            }
        }
        return household;
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
    public record SelfCheckItem(
            int id, int memberId, @Nullable LocalDate dueOn) {}

    /**
     * One registration short of an answer to a question its appointment gained after the sign-up.
     *
     * @param memberName whose registration it is, named only where it is not the reader's own, so
     *                   a guardian can tell the members in their care apart
     */
    public record RegistrationUpdateItem(
            int registrationId,
            int eventId,
            String eventName,
            LocalDate eventDate,
            int memberId,
            @Nullable String memberName) {}

    /**
     * One signature field waiting for the reader.
     *
     * @param fieldId       the field, which is what the signing screen opens
     * @param documentTitle the title the document is filed under, or null once it was deleted
     * @param memberName    the official name of the member the document is about, named only where it is
     *                      not the reader's own
     */
    public record SignatureItem(
            int fieldId,
            @Nullable String documentTitle,
            @Nullable String memberName) {}

    /**
     * What a member still owes.
     *
     * @param selfChecks          the self-checks they are answerable for. Unlike the three fields
     *                            above, this one does not stand in the doorway: it is here to be
     *                            listed and counted, never to hold the reader on the landing.
     * @param registrationUpdates the registrations still owing an answer to a question added after
     *                            the sign-up. Listed and counted like the self-checks, and just as
     *                            unable to hold the reader on the landing.
     * @param pendingSignatures   the signature fields waiting for the reader or a member in their care.
     *                            Listed and counted, never holding the reader on the landing.
     */
    public record RequirementsResponse(
            List<RequirementItem> forcedForms,
            List<RequirementItem> forcedQuizzes,
            boolean profileIncomplete,
            List<SelfCheckItem> selfChecks,
            List<RegistrationUpdateItem> registrationUpdates,
            List<SignatureItem> pendingSignatures) {

        /** What a reader owes who is at no station: nothing. */
        public static RequirementsResponse none() {
            return new RequirementsResponse(List.of(), List.of(), false, List.of(), List.of(), List.of());
        }
    }
}

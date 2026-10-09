/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRegistrationService.RegistrationShortOfAnswer;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.system.service.RequirementsService.RequirementItem;
import dev.chojo.ember.feature.system.service.RequirementsService.RequirementsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementsServiceTest {
    private FormService formService;
    private QuizService quizService;
    private ProfileFieldService profileFieldService;
    private SelfCheckService selfCheckService;
    private EventRegistrationService registrationService;
    private StationMemberService stationMemberService;
    private MemberNameResolver memberNameResolver;
    private SignatureRequestService signatureRequests;
    private RequirementsService requirementsService;

    @BeforeEach
    void setup() {
        formService = mock(FormService.class);
        quizService = mock(QuizService.class);
        profileFieldService = mock(ProfileFieldService.class);
        selfCheckService = mock(SelfCheckService.class);
        registrationService = mock(EventRegistrationService.class);
        stationMemberService = mock(StationMemberService.class);
        memberNameResolver = mock(MemberNameResolver.class);
        signatureRequests = mock(SignatureRequestService.class);
        requirementsService = new RequirementsService(
                formService,
                quizService,
                profileFieldService,
                selfCheckService,
                registrationService,
                stationMemberService,
                memberNameResolver,
                signatureRequests);
    }

    @Test
    void getRequirementsWithEmptyLists() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);

        var result = requirementsService.getRequirements(10, 1, List.of("USER"));

        assertTrue(result.forcedForms().isEmpty());
        assertTrue(result.forcedQuizzes().isEmpty());
        assertFalse(result.profileIncomplete());
    }

    @Test
    void getRequirementsWithPopulatedLists() {
        var forms = List.of(new RequirementItem(1, "Form A"), new RequirementItem(2, "Form B"));
        var quizzes = List.of(new RequirementItem(3, "Quiz C"));
        when(formService.findForcedPending(1, List.of(10))).thenReturn(forms);
        when(quizService.findForcedPending(1, 10)).thenReturn(quizzes);
        when(profileFieldService.isProfileComplete(10)).thenReturn(false);

        var result = requirementsService.getRequirements(10, 1, List.of("USER"));

        assertEquals(2, result.forcedForms().size());
        assertEquals("Form A", result.forcedForms().getFirst().title());
        assertEquals(1, result.forcedQuizzes().size());
        assertEquals("Quiz C", result.forcedQuizzes().getFirst().title());
        assertTrue(result.profileIncomplete());
    }

    @Test
    void getRequirementsProfileComplete() {
        when(formService.findForcedPending(2, List.of(20))).thenReturn(List.of());
        when(quizService.findForcedPending(2, 20)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(20)).thenReturn(true);

        var result = requirementsService.getRequirements(20, 2, List.of("ADMIN"));

        assertFalse(result.profileIncomplete());
    }

    @Test
    void getRequirementsProfileIncomplete() {
        when(formService.findForcedPending(2, List.of(20))).thenReturn(List.of());
        when(quizService.findForcedPending(2, 20)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(20)).thenReturn(false);

        var result = requirementsService.getRequirements(20, 2, List.of("USER"));

        assertTrue(result.profileIncomplete());
    }

    @Test
    void requirementItemRecord() {
        var item = new RequirementItem(42, "Test Item");
        assertEquals(42, item.id());
        assertEquals("Test Item", item.title());
    }

    @Test
    void requirementsResponseRecord() {
        var forms = List.of(new RequirementItem(1, "Form"));
        var quizzes = List.of(new RequirementItem(2, "Quiz"));
        var response = new RequirementsResponse(forms, quizzes, true, List.of(), List.of(), List.of());

        assertEquals(forms, response.forcedForms());
        assertEquals(quizzes, response.forcedQuizzes());
        assertTrue(response.profileIncomplete());
    }

    @Test
    void requirementsResponseWithNoRequirements() {
        var response = RequirementsResponse.none();

        assertTrue(response.forcedForms().isEmpty());
        assertTrue(response.forcedQuizzes().isEmpty());
        assertFalse(response.profileIncomplete());
    }

    @Test
    void getRequirementsWithMultipleRoles() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);

        var result = requirementsService.getRequirements(10, 1, List.of("USER", "ADMIN"));

        assertFalse(result.profileIncomplete());
        verify(profileFieldService).isProfileComplete(10);
    }

    @Test
    void countPendingZeroWhenNothingPending() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);

        int count = requirementsService.countPending(10, 1, List.of("USER"));

        assertEquals(0, count);
    }

    @Test
    void countPendingWithFormsAndQuizzes() {
        var forms = List.of(new RequirementItem(1, "Form A"), new RequirementItem(2, "Form B"));
        var quizzes = List.of(new RequirementItem(3, "Quiz C"));
        when(formService.findForcedPending(1, List.of(10))).thenReturn(forms);
        when(quizService.findForcedPending(1, 10)).thenReturn(quizzes);
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);

        int count = requirementsService.countPending(10, 1, List.of("USER"));

        assertEquals(3, count);
    }

    @Test
    void countPendingWithIncompleteProfile() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(false);

        int count = requirementsService.countPending(10, 1, List.of("USER"));

        assertEquals(1, count);
    }

    @Test
    void selfChecksAreListedAndCountedWithoutBlockingTheLanding() {
        var task = new SelfCheck(
                7, 1, 10, null, Instant.EPOCH, LocalDate.of(2026, 11, 1), SelfCheckState.OPEN, null, null, null, null);
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);
        when(selfCheckService.outstandingFor(10, false)).thenReturn(List.of(task));
        when(selfCheckService.countOutstandingFor(10, false)).thenReturn(1);

        var result = requirementsService.getRequirements(10, 1, List.of("USER"));

        assertEquals(1, result.selfChecks().size());
        assertEquals(7, result.selfChecks().getFirst().id());
        assertEquals(10, result.selfChecks().getFirst().memberId());
        assertEquals(LocalDate.of(2026, 11, 1), result.selfChecks().getFirst().dueOn());
        assertTrue(result.forcedForms().isEmpty());
        assertTrue(result.forcedQuizzes().isEmpty());
        assertFalse(result.profileIncomplete());
        assertEquals(1, requirementsService.countPending(10, 1, List.of("USER")));
    }

    @Test
    void aGuardianIsAskedForTheirOwnAndTheirChargesSelfChecks() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);
        when(selfCheckService.countOutstandingFor(10, true)).thenReturn(3);

        assertEquals(3, requirementsService.countPending(10, 1, List.of("USER", "MEMBER_GUARDIAN")));
        assertEquals(0, requirementsService.countPending(10, 1, null));
        verify(selfCheckService).countOutstandingFor(10, true);
    }

    @Test
    void aRegistrationShortOfAnswerIsListedAndCountedWithoutBlockingTheLanding() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);
        when(registrationService.findShortOfAnswer(List.of(10)))
                .thenReturn(List.of(new RegistrationShortOfAnswer(5, 3, "Training", LocalDate.of(2026, 10, 2), 10)));

        var result = requirementsService.getRequirements(10, 1, List.of("USER"));

        assertEquals(1, result.registrationUpdates().size());
        var update = result.registrationUpdates().getFirst();
        assertEquals(5, update.registrationId());
        assertEquals(3, update.eventId());
        assertEquals("Training", update.eventName());
        assertEquals(LocalDate.of(2026, 10, 2), update.eventDate());
        assertEquals(10, update.memberId());
        assertNull(update.memberName());
        assertTrue(result.forcedForms().isEmpty());
        assertTrue(result.forcedQuizzes().isEmpty());
        assertFalse(result.profileIncomplete());
        assertEquals(1, requirementsService.countPending(10, 1, List.of("USER")));
        verifyNoInteractions(memberNameResolver);
    }

    @Test
    void aGuardianSeesWhoseRegistrationStillOwesAnAnswer() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);
        var charge = new StationMember(11, 1, null, null, false, null, "Kid", null, null);
        when(stationMemberService.findManaged(10)).thenReturn(List.of(charge));
        when(registrationService.findShortOfAnswer(List.of(10, 11)))
                .thenReturn(List.of(new RegistrationShortOfAnswer(6, 3, "Training", LocalDate.of(2026, 10, 2), 11)));
        when(memberNameResolver.called(11)).thenReturn("Kim Muster");

        var result = requirementsService.getRequirements(10, 1, List.of("USER", "MEMBER_GUARDIAN"));

        assertEquals(1, result.registrationUpdates().size());
        assertEquals("Kim Muster", result.registrationUpdates().getFirst().memberName());
        assertEquals(11, result.registrationUpdates().getFirst().memberId());
    }

    /** A guardian is asked for the forms that the members in their care owe, not only their own. */
    @Test
    void aGuardianIsAskedForTheFormsOwedInTheirCare() {
        var charge = new StationMember(11, 1, null, null, false, null, "Kid", null, null);
        when(stationMemberService.findManaged(10)).thenReturn(List.of(charge));
        when(formService.findForcedPending(1, List.of(10, 11)))
                .thenReturn(List.of(new RequirementItem(5, "Übungszeit")));
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);

        var result = requirementsService.getRequirements(10, 1, List.of("USER", "MEMBER_GUARDIAN"));

        assertEquals(1, result.forcedForms().size());
        assertEquals("Übungszeit", result.forcedForms().getFirst().title());
    }

    /**
     * A field waiting for a signature is listed with the document's title and counted, naming whose
     * document it is only where it is not the reader's own, and never holds the reader on the landing.
     */
    @Test
    void signaturesWaitingAreListedAndCountedWithoutBlockingTheLanding() {
        when(formService.findForcedPending(1, List.of(10))).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10)).thenReturn(true);
        when(signatureRequests.pendingFor(1, 10))
                .thenReturn(List.of(
                        waiting(31, 10, "Einverständnis Zeltlager", "Alex Muster"),
                        waiting(32, 11, null, "Kim Muster")));

        var result = requirementsService.getRequirements(10, 1, List.of("USER"));

        assertEquals(2, result.pendingSignatures().size());
        var own = result.pendingSignatures().getFirst();
        assertEquals(31, own.fieldId());
        assertEquals("Einverständnis Zeltlager", own.documentTitle());
        assertNull(own.memberName());
        var ward = result.pendingSignatures().get(1);
        assertEquals(32, ward.fieldId());
        assertNull(ward.documentTitle());
        assertEquals("Kim Muster", ward.memberName());
        assertFalse(result.profileIncomplete());
        assertTrue(result.forcedForms().isEmpty());
        assertEquals(2, requirementsService.countPending(10, 1, List.of("USER")));
    }

    private static PendingSignature waiting(int fieldId, int memberId, String title, String memberName) {
        return new PendingSignature(
                UUID.randomUUID(),
                title == null ? null : 7,
                title,
                memberName,
                new RequestedSignature(
                        fieldId,
                        3,
                        "participant",
                        FieldRole.PARTICIPANT,
                        memberId,
                        memberId,
                        memberName,
                        SignerCapacity.ACCOUNT_HOLDER,
                        "Ich stimme zu.",
                        FieldState.OPEN,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void countPendingWithEverythingPending() {
        var forms = List.of(new RequirementItem(1, "Form A"));
        var quizzes = List.of(new RequirementItem(2, "Quiz B"), new RequirementItem(3, "Quiz C"));
        when(formService.findForcedPending(2, List.of(20))).thenReturn(forms);
        when(quizService.findForcedPending(2, 20)).thenReturn(quizzes);
        when(profileFieldService.isProfileComplete(20)).thenReturn(false);

        int count = requirementsService.countPending(20, 2, List.of("ADMIN"));

        assertEquals(4, count);
    }
}

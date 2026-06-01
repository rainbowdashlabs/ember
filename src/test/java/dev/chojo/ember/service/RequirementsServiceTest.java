/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.system.service.RequirementsService;
import dev.chojo.ember.feature.system.service.RequirementsService.RequirementItem;
import dev.chojo.ember.feature.system.service.RequirementsService.RequirementsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementsServiceTest {
    private FormService formService;
    private QuizService quizService;
    private ProfileFieldService profileFieldService;
    private RequirementsService requirementsService;

    @BeforeEach
    void setup() {
        formService = mock(FormService.class);
        quizService = mock(QuizService.class);
        profileFieldService = mock(ProfileFieldService.class);
        requirementsService = new RequirementsService(formService, quizService, profileFieldService);
    }

    @Test
    void getRequirementsWithEmptyLists() {
        when(formService.findForcedPending(1, 10)).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10, 1, List.of("USER"))).thenReturn(true);

        var result = requirementsService.getRequirements(10, 1, List.of("USER"));

        assertTrue(result.forcedForms().isEmpty());
        assertTrue(result.forcedQuizzes().isEmpty());
        assertFalse(result.profileIncomplete());
    }

    @Test
    void getRequirementsWithPopulatedLists() {
        var forms = List.of(new RequirementItem(1, "Form A"), new RequirementItem(2, "Form B"));
        var quizzes = List.of(new RequirementItem(3, "Quiz C"));
        when(formService.findForcedPending(1, 10)).thenReturn(forms);
        when(quizService.findForcedPending(1, 10)).thenReturn(quizzes);
        when(profileFieldService.isProfileComplete(10, 1, List.of("USER"))).thenReturn(false);

        var result = requirementsService.getRequirements(10, 1, List.of("USER"));

        assertEquals(2, result.forcedForms().size());
        assertEquals("Form A", result.forcedForms().getFirst().title());
        assertEquals(1, result.forcedQuizzes().size());
        assertEquals("Quiz C", result.forcedQuizzes().getFirst().title());
        assertTrue(result.profileIncomplete());
    }

    @Test
    void getRequirementsProfileComplete() {
        when(formService.findForcedPending(2, 20)).thenReturn(List.of());
        when(quizService.findForcedPending(2, 20)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(20, 2, List.of("ADMIN"))).thenReturn(true);

        var result = requirementsService.getRequirements(20, 2, List.of("ADMIN"));

        assertFalse(result.profileIncomplete());
    }

    @Test
    void getRequirementsProfileIncomplete() {
        when(formService.findForcedPending(2, 20)).thenReturn(List.of());
        when(quizService.findForcedPending(2, 20)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(20, 2, List.of("USER"))).thenReturn(false);

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
        var response = new RequirementsResponse(forms, quizzes, true);

        assertEquals(forms, response.forcedForms());
        assertEquals(quizzes, response.forcedQuizzes());
        assertTrue(response.profileIncomplete());
    }

    @Test
    void requirementsResponseWithNoRequirements() {
        var response = new RequirementsResponse(List.of(), List.of(), false);

        assertTrue(response.forcedForms().isEmpty());
        assertTrue(response.forcedQuizzes().isEmpty());
        assertFalse(response.profileIncomplete());
    }

    @Test
    void getRequirementsWithMultipleRoles() {
        when(formService.findForcedPending(1, 10)).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10, 1, List.of("USER", "ADMIN")))
                .thenReturn(true);

        var result = requirementsService.getRequirements(10, 1, List.of("USER", "ADMIN"));

        assertFalse(result.profileIncomplete());
        verify(profileFieldService).isProfileComplete(10, 1, List.of("USER", "ADMIN"));
    }

    @Test
    void countPendingZeroWhenNothingPending() {
        when(formService.findForcedPending(1, 10)).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10, 1, List.of("USER"))).thenReturn(true);

        int count = requirementsService.countPending(10, 1, List.of("USER"));

        assertEquals(0, count);
    }

    @Test
    void countPendingWithFormsAndQuizzes() {
        var forms = List.of(new RequirementItem(1, "Form A"), new RequirementItem(2, "Form B"));
        var quizzes = List.of(new RequirementItem(3, "Quiz C"));
        when(formService.findForcedPending(1, 10)).thenReturn(forms);
        when(quizService.findForcedPending(1, 10)).thenReturn(quizzes);
        when(profileFieldService.isProfileComplete(10, 1, List.of("USER"))).thenReturn(true);

        int count = requirementsService.countPending(10, 1, List.of("USER"));

        assertEquals(3, count);
    }

    @Test
    void countPendingWithIncompleteProfile() {
        when(formService.findForcedPending(1, 10)).thenReturn(List.of());
        when(quizService.findForcedPending(1, 10)).thenReturn(List.of());
        when(profileFieldService.isProfileComplete(10, 1, List.of("USER"))).thenReturn(false);

        int count = requirementsService.countPending(10, 1, List.of("USER"));

        assertEquals(1, count);
    }

    @Test
    void countPendingWithEverythingPending() {
        var forms = List.of(new RequirementItem(1, "Form A"));
        var quizzes = List.of(new RequirementItem(2, "Quiz B"), new RequirementItem(3, "Quiz C"));
        when(formService.findForcedPending(2, 20)).thenReturn(forms);
        when(quizService.findForcedPending(2, 20)).thenReturn(quizzes);
        when(profileFieldService.isProfileComplete(20, 2, List.of("ADMIN"))).thenReturn(false);

        int count = requirementsService.countPending(20, 2, List.of("ADMIN"));

        assertEquals(4, count);
    }
}
